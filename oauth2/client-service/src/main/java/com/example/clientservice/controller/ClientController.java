package com.example.clientservice.controller;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.Map;

@RestController
@RequestMapping("/client")
public class ClientController {

    private final RestClient restClient;
    private final RestClient plainRestClient;
    private final OAuth2AuthorizedClientManager tokenExchangeAuthorizedClientManager;
    private final String resourceServerBaseUrl;

    public ClientController(RestClient restClient,
                            @Qualifier("plain") RestClient plainRestClient,
                            @Qualifier("tokenExchange") OAuth2AuthorizedClientManager tokenExchangeAuthorizedClientManager,
                            @Value("${resource-server.base-url}") String resourceServerBaseUrl) {
        this.restClient = restClient;
        this.plainRestClient = plainRestClient;
        this.tokenExchangeAuthorizedClientManager = tokenExchangeAuthorizedClientManager;
        this.resourceServerBaseUrl = resourceServerBaseUrl;
    }

    /**
     * Flow Client Credentials (M2M) : `client-service` appelle `resource-server`
     * avec son propre token de service-account, sans utilisateur.
     */
    @GetMapping("/call")
    public Map<?, ?> callResourceServer() {
        Map<?, ?> body = restClient.get()
                .uri(resourceServerBaseUrl + "/api/message")
                .retrieve()
                .body(Map.class);
        if (body == null) {
            throw new IllegalStateException("Réponse vide du resource-server");
        }
        return body;
    }

    /**
     * Flow OAuth 2.0 Token Exchange (RFC 8693) : `client-service` reçoit le
     * token de l'utilisateur transmis par `frontend-service`, l'échange
     * auprès de Keycloak contre un nouveau token émis pour lui-même
     * (`demo-client`) mais représentant toujours l'utilisateur, puis appelle
     * `resource-server` avec ce token échangé. Le résultat dépend donc des
     * rôles de l'utilisateur d'origine.
     */
    @GetMapping("/call-as-user")
    public Map<?, ?> callResourceServerAsUser(JwtAuthenticationToken authentication) {
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("token-exchange")
                .principal(authentication)
                .build();
        OAuth2AuthorizedClient authorizedClient = tokenExchangeAuthorizedClientManager.authorize(authorizeRequest);
        if (authorizedClient == null) {
            throw new IllegalStateException("Échange de token impossible");
        }
        String exchangedToken = authorizedClient.getAccessToken().getTokenValue();

        boolean isAdmin = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        String path = isAdmin ? "/api/admin/dashboard" : "/api/user/profile";

        Map<?, ?> body = plainRestClient.get()
                .uri(resourceServerBaseUrl + path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + exchangedToken)
                .retrieve()
                .body(Map.class);
        if (body == null) {
            throw new IllegalStateException("Réponse vide du resource-server");
        }
        return body;
    }
}

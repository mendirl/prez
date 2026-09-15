package io.mendirl.demo.clientservice.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Configuration
public class WebClientConfig {

    private static final Logger log = LoggerFactory.getLogger(WebClientConfig.class);

    @Bean
    @Qualifier("clientCredentials")
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .clientCredentials()
                .build();

        var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    /**
     * Manager dédié au flow OAuth 2.0 Token Exchange (RFC 8693). Le
     * `subjectTokenResolver` par défaut de `TokenExchangeOAuth2AuthorizedClientProvider`
     * récupère le token à échanger depuis l'`Authentication` courante (le JWT
     * de l'utilisateur transmis par `frontend-service`, décodé par le
     * `oauth2ResourceServer` de ce module).
     */
    @Bean
    @Qualifier("tokenExchange")
    public OAuth2AuthorizedClientManager tokenExchangeAuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        var tokenExchangeProvider = new TokenExchangeOAuth2AuthorizedClientProvider();
        // Le endpoint token-exchange (preview) de Keycloak 25 exige un
        // `subject_token_type=access_token` : on enveloppe le JWT entrant
        // (décodé par oauth2ResourceServer) dans un OAuth2AccessToken, sinon
        // le resolver par défaut renvoie le Jwt tel quel et Spring Security
        // envoie `urn:ietf:params:oauth:token-type:jwt`, rejeté par Keycloak.
        tokenExchangeProvider.setSubjectTokenResolver(context -> {
            if (context.getPrincipal() instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                return new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, jwt.getTokenValue(),
                        jwt.getIssuedAt(), jwt.getExpiresAt());
            }
            return null;
        });

        var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
                .provider(tokenExchangeProvider)
                .build();

        var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                clientRegistrationRepository, authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Bean
    public RestClient restClient(@Qualifier("clientCredentials") OAuth2AuthorizedClientManager authorizedClientManager) {
        ClientHttpRequestInterceptor oauth2Interceptor = new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                    ClientHttpRequestExecution execution) throws IOException {
                var authorizeRequest = OAuth2AuthorizeRequest
                        .withClientRegistrationId("client-credentials")
                        .principal("client-service")
                        .build();
                log.info("[Client Credentials] Demande de token auprès de Keycloak pour appeler {}", request.getURI());
                var authorizedClient = authorizedClientManager.authorize(authorizeRequest);
                if (authorizedClient != null) {
                    var token = authorizedClient.getAccessToken().getTokenValue();
                    log.info("[Client Credentials] Access token de service-account reçu (expire à {}) : {}",
                            authorizedClient.getAccessToken().getExpiresAt(), token);
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                } else {
                    log.warn("[Client Credentials] Impossible d'obtenir un token pour appeler {}", request.getURI());
                }
                return execution.execute(request, body);
            }
        };

        return RestClient.builder()
                .requestInterceptor(oauth2Interceptor)
                .build();
    }

    /**
     * `RestClient` neutre (sans intercepteur OAuth2 automatique), utilisé par
     * le flow Token Exchange qui doit positionner lui-même le Bearer obtenu
     * après l'échange (le token change à chaque appel selon l'utilisateur).
     */
    @Bean
    @Qualifier("plain")
    public RestClient plainRestClient() {
        return RestClient.create();
    }
}

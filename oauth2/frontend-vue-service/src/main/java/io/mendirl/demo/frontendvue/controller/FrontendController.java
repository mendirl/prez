package io.mendirl.demo.frontendvue.controller;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Controller
public class FrontendController {

    private static final Logger log = LoggerFactory.getLogger(FrontendController.class);

    private final String resourceServerBaseUrl;
    private final String clientServiceBaseUrl;
    private final RestClient restClient;

    public FrontendController(@Value("${resource-server.base-url}") String resourceServerBaseUrl,
                              @Value("${client-service.base-url}") String clientServiceBaseUrl) {
        this.resourceServerBaseUrl = resourceServerBaseUrl;
        this.clientServiceBaseUrl = clientServiceBaseUrl;
        this.restClient = RestClient.create();
    }

    @GetMapping({"/", "/home"})
    public String application() {
        return "forward:/index.html";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String admin() {
        return "forward:/index.html";
    }

    @GetMapping("/api/csrf")
    @ResponseBody
    public CsrfToken csrf(@RequestAttribute("_csrf") CsrfToken csrfToken) {
        return csrfToken;
    }

    @GetMapping("/api/session")
    @ResponseBody
    public UserSession session(@AuthenticationPrincipal OidcUser oidcUser) {
        List<String> authorities = oidcUser.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return new UserSession(oidcUser.getName(), oidcUser.getClaims(), authorities, oidcUser.getIdToken().getTokenValue());
    }

    @GetMapping("/api/access-token")
    @ResponseBody
    public AccessToken accessToken(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient) {
        return new AccessToken(
                authorizedClient.getAccessToken().getTokenValue(),
                authorizedClient.getAccessToken().getExpiresAt(),
                authorizedClient.getAccessToken().getScopes());
    }

    @GetMapping("/api/user")
    @ResponseBody
    public ApiResponse user(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient) {
        return callResourceServer(authorizedClient, "/api/user/profile");
    }

    @GetMapping("/api/admin")
    @ResponseBody
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse adminApi(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient) {
        return callResourceServer(authorizedClient, "/api/admin/dashboard");
    }

    @GetMapping("/api/message")
    @ResponseBody
    public ApiResponse message(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient) {
        return callResourceServer(authorizedClient, "/api/message");
    }

    @GetMapping("/api/clientm")
    @ResponseBody
    public ApiResponse clientm(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient) {
        String path = "/client/call-as-user";
        try {
            @SuppressWarnings("unchecked")
            @Nullable Map<String, Object> response = restClient.get()
                    .uri(clientServiceBaseUrl + path)
                    .header("Authorization", "Bearer " + authorizedClient.getAccessToken().getTokenValue())
                    .retrieve()
                    .body(Map.class);
            return new ApiResponse("client-service " + path + " (Token Exchange)", response, null);
        } catch (Exception ex) {
            log.warn("Échec de l'appel à client-service {}{} : {}", clientServiceBaseUrl, path, ex.getMessage());
            return new ApiResponse("client-service " + path + " (Token Exchange)", null, ex.getMessage());
        }
    }

    private ApiResponse callResourceServer(OAuth2AuthorizedClient authorizedClient, String path) {
        try {
            @SuppressWarnings("unchecked")
            @Nullable Map<String, Object> response = restClient.get()
                    .uri(resourceServerBaseUrl + path)
                    .header("Authorization", "Bearer " + authorizedClient.getAccessToken().getTokenValue())
                    .retrieve()
                    .body(Map.class);
            return new ApiResponse(path, response, null);
        } catch (Exception ex) {
            log.warn("Échec de l'appel à resource-server {}{} : {}", resourceServerBaseUrl, path, ex.getMessage());
            return new ApiResponse(path, null, ex.getMessage());
        }
    }

    public record UserSession(String name, Map<String, Object> claims, List<String> authorities, String idToken) {
    }

    public record AccessToken(String value, @Nullable Instant expiresAt, Iterable<String> scopes) {
    }

    public record ApiResponse(String endpoint, @Nullable Map<String, Object> response, @Nullable String error) {
    }
}
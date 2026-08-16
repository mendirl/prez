package io.mendirl.demo.frontend.controller;

import io.mendirl.demo.frontend.config.ClientServiceProperties;
import io.mendirl.demo.frontend.config.ResourceServerProperties;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Controller
public class HomeController {
    private static final Logger log = LoggerFactory.getLogger(HomeController.class);

    private final String resourceServerBaseUrl;
    private final String clientServiceBaseUrl;
    private final RestClient restClient;

    public HomeController(ResourceServerProperties resourceServerProperties,
                          ClientServiceProperties clientServiceProperties,
                          RestClient restClient) {
        this.resourceServerBaseUrl = resourceServerProperties.baseUrl();
        this.clientServiceBaseUrl = clientServiceProperties.baseUrl();
        this.restClient = restClient;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        log.info("Affichage de /home pour l'utilisateur '{}' (rôles : {})",
                oidcUser.getName(),
                oidcUser.getAuthorities());
        model.addAttribute("user", oidcUser);
        model.addAttribute("claims", oidcUser.getClaims());
        model.addAttribute("idToken", oidcUser.getIdToken().getTokenValue());
        model.addAttribute("authorities", oidcUser.getAuthorities());
        return "home";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminPage(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        log.info("Affichage de /admin pour l'utilisateur '{}'", oidcUser.getName());
        model.addAttribute("user", oidcUser);
        return "admin";
    }

    @GetMapping("/fragments/token")
    public String token(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
                        Model model) {
        model.addAttribute("accessToken", authorizedClient.getAccessToken().getTokenValue());
        model.addAttribute("expiresAt", authorizedClient.getAccessToken().getExpiresAt());
        model.addAttribute("scopes", authorizedClient.getAccessToken().getScopes());
        return "fragments/token :: card";
    }

    @GetMapping("/fragments/user")
    public String userFragment(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
                               Model model) {
        callApi(authorizedClient, "/api/user/profile", model);
        return "fragments/message :: card";
    }

    @GetMapping("/fragments/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminFragment(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
                                Model model) {
        callApi(authorizedClient, "/api/admin/dashboard", model);
        return "fragments/message :: card";
    }

    @GetMapping("/fragments/message")
    public String message(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
                          Model model) {
        callApi(authorizedClient, "/api/message", model);
        return "fragments/message :: card";
    }

    /**
     * Flow OAuth 2.0 Token Exchange : le token de l'utilisateur connecté est
     * transmis à `client-service`, qui l'échange auprès de Keycloak avant
     * d'appeler `resource-server` en son nom. Le résultat diffère donc selon
     * les rôles de l'utilisateur (zone USER ou ADMIN).
     */
    @GetMapping("/fragments/clientm")
    public String clientmFragment(@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient authorizedClient,
                                  Model model) {
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        String path = "/client/call-as-user";
        log.info("[Token Exchange] Transmission du token de l'utilisateur courant à client-service {}{}",
                clientServiceBaseUrl,
                path);
        try {
            @SuppressWarnings("unchecked")
            @Nullable Map<String, Object> response = restClient.get()
                    .uri(clientServiceBaseUrl + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            log.info("[Token Exchange] Réponse reçue de client-service : {}", response);
            model.addAttribute("response", response);
            model.addAttribute("error", null);
            model.addAttribute("endpoint", "clientm " + path + " (Token Exchange)");
        } catch (Exception e) {
            log.warn("[Token Exchange] Échec de l'appel à client-service {}{} : {}",
                    clientServiceBaseUrl,
                    path,
                    e.getMessage());
            model.addAttribute("response", null);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("endpoint", "clientm " + path + " (Token Exchange)");
        }
        return "fragments/message :: card";
    }

    private void callApi(OAuth2AuthorizedClient authorizedClient, String path, Model model) {
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        log.info("Appel de resource-server {}{} avec le token de l'utilisateur courant", resourceServerBaseUrl, path);
        try {
            @SuppressWarnings("unchecked")
            @Nullable Map<String, Object> response = restClient.get()
                    .uri(resourceServerBaseUrl + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            log.info("Réponse reçue de resource-server {} : {}", path, response);
            model.addAttribute("response", response);
            model.addAttribute("error", null);
            model.addAttribute("endpoint", path);
        } catch (Exception e) {
            log.warn("Échec de l'appel à resource-server {}{} : {}", resourceServerBaseUrl, path, e.getMessage());
            model.addAttribute("response", null);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("endpoint", path);
        }
    }
}

package com.example.frontend.controller;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
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
    private final String resourceServerBaseUrl;
    private final RestClient restClient;

    public HomeController(@Value("${resource-server.base-url}") String resourceServerBaseUrl, RestClient restClient) {
        this.resourceServerBaseUrl = resourceServerBaseUrl;
        this.restClient = restClient;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/home")
    public String home(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
        model.addAttribute("user", oidcUser);
        model.addAttribute("claims", oidcUser.getClaims());
        model.addAttribute("idToken", oidcUser.getIdToken().getTokenValue());
        model.addAttribute("authorities", oidcUser.getAuthorities());
        return "home";
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminPage(@AuthenticationPrincipal OidcUser oidcUser, Model model) {
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

    private void callApi(OAuth2AuthorizedClient authorizedClient, String path, Model model) {
        String accessToken = authorizedClient.getAccessToken().getTokenValue();
        try {
            @SuppressWarnings("unchecked")
            @Nullable Map<String, Object> response = restClient.get()
                    .uri(resourceServerBaseUrl + path)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            model.addAttribute("response", response);
            model.addAttribute("error", null);
            model.addAttribute("endpoint", path);
        } catch (Exception e) {
            model.addAttribute("response", null);
            model.addAttribute("error", e.getMessage());
            model.addAttribute("endpoint", path);
        }
    }
}

package io.mendirl.demo.frontendvue.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FrontendController {

    private final SpaConfiguration configuration;

    public FrontendController(@Value("${spa.keycloak-url}") String keycloakUrl,
                              @Value("${spa.resource-server-url}") String resourceServerUrl,
                              @Value("${spa.client-service-url}") String clientServiceUrl) {
        this.configuration = new SpaConfiguration(keycloakUrl, resourceServerUrl, clientServiceUrl);
    }

    @GetMapping("/spa-config.json")
    public SpaConfiguration configuration() {
        return configuration;
    }

    public record SpaConfiguration(String keycloakUrl, String resourceServerUrl, String clientServiceUrl) {
    }
}
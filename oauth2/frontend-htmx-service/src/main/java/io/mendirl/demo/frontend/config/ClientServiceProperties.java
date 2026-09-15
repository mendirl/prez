package io.mendirl.demo.frontend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de connexion vers `client-service`, utilisées par
 * {@code HomeController} pour construire les URLs appelées.
 */
@ConfigurationProperties(prefix = "client-service")
public record ClientServiceProperties(String baseUrl) {
}

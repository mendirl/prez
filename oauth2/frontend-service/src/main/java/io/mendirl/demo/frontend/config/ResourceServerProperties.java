package io.mendirl.demo.frontend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriétés de connexion vers `resource-server`, utilisées par
 * {@code HomeController} pour construire les URLs appelées.
 */
@ConfigurationProperties(prefix = "resource-server")
public record ResourceServerProperties(String baseUrl) {
}

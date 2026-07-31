package com.example.clientservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;

import java.io.IOException;

@Configuration
public class WebClientConfig {

    @Bean
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

    @Bean
    public RestClient restClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        ClientHttpRequestInterceptor oauth2Interceptor = new ClientHttpRequestInterceptor() {
            @Override
            public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                    ClientHttpRequestExecution execution) throws IOException {
                var authorizeRequest = OAuth2AuthorizeRequest
                        .withClientRegistrationId("keycloak")
                        .principal("client-service")
                        .build();
                var authorizedClient = authorizedClientManager.authorize(authorizeRequest);
                if (authorizedClient != null) {
                    var token = authorizedClient.getAccessToken().getTokenValue();
                    request.getHeaders().set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
                }
                return execution.execute(request, body);
            }
        };

        return RestClient.builder()
                .requestInterceptor(oauth2Interceptor)
                .build();
    }
}

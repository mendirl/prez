package com.example.frontend.config;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nimbusds.jwt.JWTParser;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ClientRegistrationRepository clientRegistrationRepository) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/css/**", "/webjars/**", "/error").permitAll()
                .requestMatchers("/admin/**", "/fragments/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .defaultSuccessUrl("/home", true)
                .authorizationEndpoint(a -> a.authorizationRequestResolver(
                        pkceAuthorizationRequestResolver(clientRegistrationRepository)))
                .userInfoEndpoint(u -> u.oidcUserService(keycloakOidcUserService()))
            )
            .logout(logout -> logout
                .logoutSuccessHandler(oidcLogoutSuccessHandler(clientRegistrationRepository))
            );
        return http.build();
    }

    /**
     * Service OIDC qui enrichit l'utilisateur avec les rôles `realm_access.roles`
     * issus de l'access token Keycloak (par défaut, ces rôles ne sont pas dans
     * l'ID token). Les rôles sont préfixés `ROLE_` pour que `hasRole(...)` fonctionne.
     */
    @Bean
    public OidcUserService keycloakOidcUserService() {
        OidcUserService delegate = new OidcUserService();
        return new OidcUserService() {
            @Override
            public OidcUser loadUser(OidcUserRequest userRequest) {
                OidcUser user = delegate.loadUser(userRequest);
                Set<GrantedAuthority> authorities = new LinkedHashSet<>(user.getAuthorities());
                authorities.addAll(extractRealmRoles(userRequest.getAccessToken().getTokenValue()));
                String nameAttr = userRequest.getClientRegistration()
                        .getProviderDetails()
                        .getUserInfoEndpoint()
                        .getUserNameAttributeName();
                if (nameAttr == null || nameAttr.isBlank()) {
                    return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo());
                }
                return new DefaultOidcUser(authorities, user.getIdToken(), user.getUserInfo(), nameAttr);
            }
        };
    }

    private static Collection<GrantedAuthority> extractRealmRoles(String accessTokenValue) {
        List<GrantedAuthority> result = new ArrayList<>();
        Map<String, Object> claims = parseClaims(accessTokenValue);
        if (claims == null) {
            return result;
        }
        Object realmAccess = claims.get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) {
            return result;
        }
        Object roles = map.get("roles");
        if (!(roles instanceof Collection<?> rolesColl)) {
            return result;
        }
        for (Object role : rolesColl) {
            if (role != null) {
                result.add(new SimpleGrantedAuthority("ROLE_" + role));
            }
        }
        return result;
    }

    private static @Nullable Map<String, Object> parseClaims(String jwt) {
        try {
            return JWTParser.parse(jwt).getJWTClaimsSet().getClaims();
        } catch (ParseException ex) {
            return null;
        }
    }

    private OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler(
            ClientRegistrationRepository clientRegistrationRepository) {
        OidcClientInitiatedLogoutSuccessHandler handler =
                new OidcClientInitiatedLogoutSuccessHandler(clientRegistrationRepository);
        handler.setPostLogoutRedirectUri("{baseUrl}/");
        return handler;
    }

    /**
     * Active PKCE (RFC 7636) sur le flow Authorization Code, y compris pour un
     * client confidentiel comme `frontend-client`. Par défaut, Spring Security
     * n'active PKCE que pour les clients publics ; ici on le force via
     * `OAuth2AuthorizationRequestCustomizers.withPkce()` (code_challenge S256).
     */
    private OAuth2AuthorizationRequestResolver pkceAuthorizationRequestResolver(
            ClientRegistrationRepository clientRegistrationRepository) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(
                        clientRegistrationRepository, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
        return resolver;
    }

    @Bean
    public RestClient restClient() {
        return RestClient.create();
    }
}

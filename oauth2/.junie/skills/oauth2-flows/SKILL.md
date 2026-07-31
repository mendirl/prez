# Skill: oauth2-flows

Ajouter ou modifier un flow OAuth2 / OIDC dans ce projet (Spring Boot 4 +
Spring Security 6, Keycloak en Authorization Server).

## Quand l'utiliser
- Ajouter un endpoint protégé par rôle.
- Modifier une `SecurityFilterChain`.
- Ajouter un client OAuth2 dans le frontend ou un service M2M.
- Diagnostiquer un 401 / 403.

## Flows présents dans le projet

| Flow | Module client | Client Keycloak | Particularités |
|---|---|---|---|
| Client Credentials | `client-service` | `demo-client` (`serviceAccountsEnabled`) | `RestClient` + `OAuth2AuthorizedClientManager` |
| Authorization Code + OIDC | `frontend-service` | `frontend-client` (`standardFlowEnabled`) | `oauth2Login`, RP-Initiated Logout, rôles depuis access token |

Le `resource-server` valide les JWT via JWKS de Keycloak (`issuer-uri` dans
`application.yml`).

## Recettes

### 1. Ajouter un endpoint protégé par rôle dans `resource-server`
```java
@GetMapping("/api/mgmt/foo")
public Map<String, Object> foo(@AuthenticationPrincipal Jwt jwt) { ... }
```
Puis dans `SecurityConfig` :
```java
.requestMatchers("/api/mgmt/**").hasRole("ADMIN")
```
Vérifier qu'aucune règle plus large (`anyRequest().authenticated()`) ne court-circuite.

### 2. Appeler le `resource-server` depuis le frontend
- Injecter le `RestClient` (déjà câblé pour propager le Bearer via
  `OAuth2AuthorizedClient`).
- Récupérer le client : `@RegisteredOAuth2AuthorizedClient("keycloak") OAuth2AuthorizedClient`.
- Construire la requête :
  ```java
  restClient.get()
      .uri(RESOURCE_SERVER + path)
      .header("Authorization", "Bearer " + authorizedClient.getAccessToken().getTokenValue())
      .retrieve()
      .body(Map.class);
  ```

### 3. Ajouter un nouveau flow M2M dans un nouveau service
1. Ajouter `spring-boot-starter-oauth2-client` au `pom.xml`.
2. `application.yml` :
   ```yaml
   spring:
     security:
       oauth2:
         client:
           registration:
             keycloak:
               client-id: <id>
               client-secret: <secret>
               authorization-grant-type: client_credentials
               scope: openid
           provider:
             keycloak:
               token-uri: http://localhost:8080/realms/demo/protocol/openid-connect/token
   ```
3. Bean `RestClient` qui injecte un `OAuth2ClientHttpRequestInterceptor` (cf.
   `client-service/config/RestClientConfig.java` comme référence).
4. Déclarer le client dans `keycloak/realm-demo.json` (skill `keycloak-realm`).

### 4. Faire apparaître des rôles côté frontend
- Les rôles realm ne sont **pas** dans l'ID token par défaut.
- Le `frontend-service` parse l'**access token** via Nimbus `JWTParser` dans
  `SecurityConfig.keycloakOidcUserService()` et les ajoute aux authorities
  préfixées `ROLE_`.
- Dans les templates : `sec:authorize="hasRole('XXX')"` (extras Spring Security).
- Dans les contrôleurs : `@PreAuthorize("hasRole('XXX')")` (besoin
  `@EnableMethodSecurity` — déjà présent).

## Diagnostic 401 / 403

1. Inspecter le JWT : `echo $TOKEN | cut -d. -f2 | base64 -d | jq`.
2. Vérifier `iss` = `http://localhost:8080/realms/demo`.
3. Vérifier `realm_access.roles` contient le rôle attendu.
4. Vérifier la `SecurityFilterChain` du module qui répond.
5. Si le rôle manque côté frontend mais présent dans le token → vérifier
   `keycloakOidcUserService` (cassé par un refactor ?).

## Bonnes pratiques
- Toujours protéger par le rôle **le plus restrictif** suffisant.
- Doubler la protection sur les pages sensibles : `SecurityFilterChain` **et**
  `@PreAuthorize`.
- Ne pas exposer l'access token côté JS / template sauf pour démonstration
  explicite (le projet le fait dans `/fragments/token` à des fins pédagogiques).
- Préférer `hasRole("X")` à `hasAuthority("ROLE_X")` (équivalents mais plus lisible).

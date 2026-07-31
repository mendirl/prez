# Skill: keycloak-realm

Modifier le realm Keycloak (`keycloak/realm-demo.json`) — rôles, utilisateurs, clients —
sans casser l'import au démarrage.

## Quand l'utiliser
- Ajouter / modifier un utilisateur, un rôle, un client OAuth2.
- Configurer scopes, mappers, redirect URIs.
- Diagnostiquer un 401/403 dont l'origine est côté IAM.

## Faits clés
- Le conteneur est lancé avec `start-dev --import-realm`.
- L'import **ne s'exécute que si le realm n'existe pas déjà** : après modification
  du JSON, il **faut** invalider le volume :
  ```bash
  docker compose down -v && docker compose up -d
  ```
- L'admin console : http://localhost:8080 — `admin` / `admin`.
- Realm : `demo`.

## Structure attendue de `realm-demo.json`

```jsonc
{
  "realm": "demo",
  "enabled": true,
  "roles": {
    "realm": [
      { "name": "ADMIN", "description": "..." },
      { "name": "USER",  "description": "..." }
    ]
  },
  "clients":  [ /* demo-client, frontend-client */ ],
  "users":    [ /* alice, bob, demo */ ]
}
```

## Recettes

### Ajouter un rôle realm
1. Ajouter une entrée dans `roles.realm`.
2. Affecter le rôle à un user via `realmRoles: [ "MON_ROLE" ]`.
3. Côté `resource-server` : ajouter une règle dans `SecurityConfig`
   (`.requestMatchers(...).hasRole("MON_ROLE")`).
4. Côté `frontend-service` : `sec:authorize="hasRole('MON_ROLE')"` dans les templates.
5. Réimporter : `docker compose down -v && up -d`.

### Ajouter un client OAuth2 confidentiel
Modèle minimal :
```json
{
  "clientId": "mon-client",
  "enabled": true,
  "protocol": "openid-connect",
  "publicClient": false,
  "secret": "mon-secret",
  "serviceAccountsEnabled": true,        // pour Client Credentials
  "standardFlowEnabled": true,           // pour Authorization Code
  "redirectUris": [ "http://localhost:PORT/login/oauth2/code/keycloak" ],
  "webOrigins": [ "http://localhost:PORT" ]
}
```

### Activer le Password Grant (debug uniquement)
Sur le client : `"directAccessGrantsEnabled": true`.
**Ne jamais** activer pour un client production.

## Vérification post-modification

```bash
# Realm visible ?
curl -fsS http://localhost:8080/realms/demo > /dev/null && echo OK

# Discovery OIDC
curl -s http://localhost:8080/realms/demo/.well-known/openid-configuration | jq .issuer

# Tester un token Client Credentials
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=demo-client" -d "client_secret=demo-secret" | jq

# Inspecter le JWT (claims realm_access.roles attendus)
TOKEN=$(... ci-dessus ...)
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null | jq .realm_access
```

## Pièges courants
- Rôles absents du token côté `frontend-service` : c'est **normal**, les rôles realm
  ne sont **pas** dans l'ID token par défaut → ils sont lus depuis l'**access token**
  via Nimbus `JWTParser` (cf. `SecurityConfig` du frontend).
- Oubli de `serviceAccountsEnabled: true` → le Client Credentials renvoie 400.
- Oubli de `redirectUris` → erreur "Invalid redirect_uri" au login.
- Oubli de `down -v` après modification → on croit modifier mais Keycloak garde l'ancien realm.

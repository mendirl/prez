# Démonstration OAuth2 / OIDC avec Keycloak, Spring Boot 4 & HTMX

Projet pédagogique multi-modules illustrant **trois flows OAuth2** et la **gestion fine des rôles**
sur une stack moderne : **Java 25**, **Spring Boot 4.0.5**, **Maven 4.1.0**, **JSpecify**
(null-safety vérifiée à la compilation via NullAway/ErrorProne), **Thymeleaf + HTMX**.

| Flow | Acteur | Module | Cas d'usage |
|------|--------|--------|-------------|
| **Client Credentials** (M2M) | service ↔ service | `client-service` → `resource-server` | API consommée par un backend sans utilisateur |
| **Authorization Code + PKCE + OIDC** | utilisateur ↔ navigateur | `frontend-service` → `resource-server` | Login web, rôles, UI conditionnelle |
| **OAuth 2.0 Token Exchange** (RFC 8693) | utilisateur → `frontend-service` → `client-service` → `resource-server` | `frontend-service` transmet le token utilisateur à `client-service`, qui l'échange auprès de Keycloak | Un service intermédiaire (« clientm ») agit au nom de l'utilisateur, résultat différent suivant ses rôles |

---

## 1. Architecture

```
                          ┌─────────────────┐
                          │    Keycloak     │  realm: demo
                          │   :8080         │  rôles realm: ADMIN, USER
                          └────────┬────────┘
            client_credentials     │     authorization_code + PKCE
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          │                          ▼
┌─────────────────┐                │                ┌─────────────────┐
│  client-service │                │                │ frontend-service│
│      :8082      │                │                │      :8083      │
│  RestClient M2M │                │                │ Thymeleaf + HTMX│
└────────┬────────┘                │                └────────┬────────┘
         │  Bearer JWT             │                         │  Bearer JWT
         └───────────────► ┌─────────────────┐ ◄──────────────┘
                           │ resource-server │
                           │      :8081      │
                           │ JWT JWKS + RBAC │
                           └─────────────────┘
```

Flow **Token Exchange** (utilisateur → frontend → clientm → resource) :

```
utilisateur ──login──► frontend-service ──Bearer (token utilisateur)──► client-service (« clientm »)
                                                                              │
                                                            token-exchange    │  POST /token (grant_type=
                                                            auprès de Keycloak│  urn:ietf:params:oauth:grant-type:token-exchange)
                                                                              ▼
                                                                         Keycloak :8080
                                                                              │
                                                              nouveau token (même utilisateur, azp=client-service)
                                                                              ▼
                                                                    resource-server :8081
                                                        (/api/admin/dashboard si ADMIN, /api/user/profile sinon)
```

---

## 2. Prérequis

| Outil | Version |
|-------|---------|
| JDK | **25** (testé GraalVM 25.0.3) |
| Maven | **3.9+** ou **4.x** (modelVersion 4.1.0) |
| Docker / Docker Compose | récent |

---

## 3. Démarrage rapide

```bash
# 1. Keycloak (importe realm-demo.json au boot, ~30 s)
docker compose up -d
curl -fsS http://localhost:8080/realms/demo > /dev/null && echo "Keycloak OK"

# 2. Build complet (3 modules)
mvn clean package -DskipTests

# 3. Démarrer les 3 services (3 terminaux)
mvn -pl resource-server  spring-boot:run   # :8081
mvn -pl client-service   spring-boot:run   # :8082
mvn -pl frontend-service spring-boot:run   # :8083
```

> **Realm déjà importé ?** Relancer avec `docker compose down -v && docker compose up -d`
> pour réimporter les rôles/utilisateurs.

### Tout-en-un via le script `start-all.sh`

Un script à la racine fait tout d'un coup : réimport du realm (rôles/utilisateurs),
build Maven, lancement des 3 services en arrière-plan (logs dans `/tmp/*.log`).

```bash
./start-all.sh          # (re)lance Keycloak + build + 3 services
./start-all.sh stop     # arrête tous les services et Keycloak (volume inclus)
```

Suivre les logs : `tail -f /tmp/{resource-server,client-service,frontend-service}.log`.

### Compilation native (GraalVM)

La JVM utilisée est **GraalVM 25** : chaque module exécutable expose un profil
Maven `native` (hérité du `spring-boot-starter-parent`) qui déclenche le
traitement AOT Spring Boot puis `native-maven-plugin` pour produire un binaire
natif (pas de JVM au runtime).

```bash
# Image native d'un module (nécessite GraalVM + native-image dans le PATH)
#
# IMPORTANT — projet multi-modules :
# Ne PAS invoquer `native:compile` au niveau du reactor avec `-pl <module> -am`,
# sinon le goal s'exécute sur le parent POM (packaging=pom) et échoue avec
# "Image classpath is empty". Il faut :
#   1. faire un `package` au niveau du reactor (génère le jar du module),
#   2. puis lancer `native:compile` DANS le dossier du module.

# 1) package au reactor (AOT + jar)
mvn -Pnative -DskipTests -pl resource-server -am package

# 2) compilation native dans le module
(cd resource-server  && mvn -Pnative -DskipTests native:compile)
(cd client-service   && mvn -Pnative -DskipTests native:compile)
(cd frontend-service && mvn -Pnative -DskipTests native:compile)

# Exécuter le binaire produit dans target/
./resource-server/target/resource-server
./client-service/target/client-service
./frontend-service/target/frontend-service
```

> La première compilation native est longue (plusieurs minutes par module) et
> consomme beaucoup de RAM. Pour un build conteneurisé, voir le goal
> `spring-boot:build-image` (profil `native` également supporté).

---

## 4. Utilisateurs & rôles (realm `demo`)

| Utilisateur | Mot de passe | Rôles | Ce qu'il voit sur `/home` |
|-------------|--------------|-------|---------------------------|
| `alice` | `alice` | `ADMIN`, `USER` | Espace USER **+** espace ADMIN (secret) |
| `bob`   | `bob`   | `USER`          | Espace USER ; zone ADMIN masquée + 403 sur API |
| `demo`  | `demo`  | `USER`          | Identique à `bob` |

UI conditionnelle via `sec:authorize="hasRole('ADMIN')"` (Thymeleaf Spring Security
extras). La page `/admin` est doublement protégée : `@PreAuthorize` **et** règle
`hasRole('ADMIN')` dans la `SecurityFilterChain`.

---

## 5. Endpoints

### `resource-server` (:8081)

| Méthode | Endpoint | Accès | Description |
|---|---|---|---|
| GET | `/api/public/hello`    | public                  | Pas de token |
| GET | `/api/message`         | authentifié             | Renvoie sub, roles, scope |
| GET | `/api/user/profile`    | `USER` ou `ADMIN`       | Zone utilisateur |
| GET | `/api/admin/dashboard` | `ADMIN`                 | Données sensibles |

Les rôles sont extraits du claim `realm_access.roles` du JWT Keycloak via un
`JwtAuthenticationConverter` qui les préfixe `ROLE_`.

### `client-service` (:8082)

| Méthode | Endpoint | Description |
|---|---|---|
| GET | `/client/call` | Récupère un token via Client Credentials puis appelle `/api/message`. |
| GET | `/client/call-as-user` | **Token Exchange** : échange le token utilisateur (Bearer entrant) auprès de Keycloak, puis appelle `/api/admin/dashboard` ou `/api/user/profile` selon le rôle. Nécessite un Bearer valide. |

### `frontend-service` (:8083)

| Route | Description |
|---|---|
| `/`              | Page d'accueil + bouton « Se connecter avec Keycloak » |
| `/home`          | Après login : claims, rôles, boutons HTMX |
| `/admin`         | Page réservée `ADMIN` |
| `/fragments/*`   | Fragments HTMX (`token`, `message`, `user`, `admin`, `clientm`) |
| `/fragments/clientm` | Transmet le token utilisateur à `client-service` (flow Token Exchange) |
| `/logout`        | RP-Initiated Logout (Keycloak) |

---

## 6. Tests manuels

```bash
# Endpoint public (sans token)
curl http://localhost:8081/api/public/hello

# Flow M2M
curl http://localhost:8082/client/call | jq

# Flow utilisateur : ouvrir http://localhost:8083 dans 2 navigateurs privés
#   - alice/alice → voit les zones USER + ADMIN
#   - bob/bob     → voit USER, reçoit 403 sur /api/admin/dashboard
```

Récupérer un access token utilisateur en ligne de commande (activer
`directAccessGrantsEnabled` sur `frontend-service` si besoin) :

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d "grant_type=password" -d "username=alice" -d "password=alice" \
  -d "client_id=frontend-service" -d "client_secret=ItZGNqwyezy7OGUQftBoftcYS4QsNGe1gQQ4xo+WiYU=" | jq -r .access_token)

curl -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/admin/dashboard
```

---

## 7. Clients Keycloak (realm `demo`)

| clientId           | Type         | Flow                                            | Secret                                         | Redirect URI                                       |
|--------------------|--------------|-------------------------------------------------|------------------------------------------------|----------------------------------------------------|
| `client-service`   | confidentiel | Client Credentials + Token Exchange (requester) | `a1brNuKzLvEFeqh37BgDaBrK3ucU1IpgSoay3yF2LGA=` | —                                                  |
| `frontend-service` | confidentiel | Authorization Code                              | `ItZGNqwyezy7OGUQftBoftcYS4QsNGe1gQQ4xo+WiYU=` | `http://localhost:8083/login/oauth2/code/keycloak` |

> Le clientId Keycloak de chaque service correspond à son `spring.application.name`
> (`client-service`, `frontend-service`), et les secrets sont de vrais secrets aléatoires
> (générés via `openssl rand -base64 32`), à ne pas réutiliser tels quels en production.
>
> Le client scope `client-service-audience` (mapper d'audience) est affecté par défaut à
> `frontend-service` : il ajoute `client-service` dans l'`aud` du token utilisateur, ce qui
> autorise `client-service` à échanger ce token pour lui-même via le flow Token Exchange.

---

## 8. Structure du projet

```
prez_oauth2/
├── pom.xml                          # parent : spring-boot-starter-parent 4.0.5, Java 25, nullability plugin
├── docker-compose.yml               # Keycloak 25.0.6
├── keycloak/realm-demo.json         # realm + rôles + clients + users
├── resource-server/                 # API REST protégée (:8081)
├── client-service/                  # Client OAuth2 M2M (:8082)
└── frontend-service/                # Front Thymeleaf + HTMX, OIDC (:8083)
```

Particularités Maven :
- `modelVersion` **4.1.0** sur tous les POMs ; `<subprojects>` (nouvelle syntaxe) à la place de `<modules>`.
- Héritage de `spring-boot-starter-parent` (BOM, plugin, encoding, java.version).
- `nullability-maven-plugin` 0.3.0 (extension) → configure ErrorProne 2.47.0 + NullAway 0.13.1
  en mode JSpecify pour tous les sous-projets, vérification null-safety à la compilation.

---

## 9. Concepts clés

| Concept                                 | Description                                                                                                                                                                                                                                                                                     |
|-----------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Client Credentials**                  | Flow OAuth2 sans utilisateur, service-account du client.                                                                                                                                                                                                                                        |
| **Authorization Code + PKCE**           | Flow standard OIDC : redirection navigateur → code → token, sécurisé par un `code_verifier`/`code_challenge` (S256). Activé côté Spring via `OAuth2AuthorizationRequestCustomizers.withPkce()` et exigé côté Keycloak via `pkce.code.challenge.method=S256`.                                    |
| **OIDC**                                | Couche d'identité sur OAuth2 : ID Token JWT décrivant l'utilisateur.                                                                                                                                                                                                                            |
| **OAuth 2.0 Token Exchange (RFC 8693)** | Un client (`client-service`) échange un token reçu (émis pour un autre client) contre un nouveau token émis pour lui-même, en conservant l'utilisateur (`sub`). Exposé côté Spring via `TokenExchangeOAuth2AuthorizedClientProvider` (grant `urn:ietf:params:oauth:grant-type:token-exchange`). |
| **JWKS**                                | Le resource-server valide les JWT via la clé publique exposée par Keycloak.                                                                                                                                                                                                                     |
| **`realm_access.roles`**                | Rôles realm Keycloak, mappés en `ROLE_*` côté Spring.                                                                                                                                                                                                                                           |
| **`sec:authorize`**                     | Attribut Thymeleaf qui rend conditionnellement selon `hasRole(...)`.                                                                                                                                                                                                                            |
| **HTMX**                                | Le front recharge des fragments HTML sans JS, via `hx-get` / `hx-target`.                                                                                                                                                                                                                       |
| **RP-Initiated Logout**                 | Logout déclenché par le frontend, propagé à Keycloak.                                                                                                                                                                                                                                           |
| **JSpecify `@NullMarked`**              | Non-null par défaut au niveau package ; `@Nullable` localement.                                                                                                                                                                                                                                 |
| **NullAway**                            | Vérifie statiquement la null-safety à la compilation (échec = build cassé).                                                                                                                                                                                                                     |

---

## 11. Déploiement Kubernetes (Helm)

Le projet inclut un chart Helm pour déployer les 3 services Spring dans un cluster Kubernetes (ex: Minikube, Kind), tout en conservant Keycloak dans Docker.

### 11.1 Préparation des images Docker

Un script est fourni pour construire les images Docker de tous les modules (ou d'un module spécifique) via Cloud Native Buildpacks (Spring Boot).

```bash
# Build de toutes les images (format prez-oauth2/<module>:latest)
./build-images.sh

# Build d'un module spécifique
./build-images.sh resource-server

# Build avec un tag spécifique
./build-images.sh -t 1.0.0

# Build en mode natif (GraalVM)
./build-images.sh -n
```

Ceci créera les images suivantes localement :
- `prez-oauth2/resource-server:latest`
- `prez-oauth2/client-service:latest`
- `prez-oauth2/frontend-service:latest`

### 11.2 Configuration de la connexion Keycloak

Le chart Helm crée un service `keycloak` dans K8s qui pointe vers l'hôte Docker.
Par défaut, il utilise l'IP `172.17.0.1`. Si votre IP de passerelle Docker est différente, modifiez le fichier `helm/prez-oauth2/values.yaml` ou passez l'argument `--set`.

### 11.3 Installation du Chart (via script)
...
```bash
# Depuis la racine du projet
./deploy-k8s.sh
```

Options du script :
- `-n, --namespace <ns>` : changer le namespace (défaut : `prez-oauth2`)
- `-k, --keycloak-host <ip>` : changer l'IP de Keycloak (défaut : `172.17.0.1`)
- `-i, --ingress-host <host>` : activer l'Ingress avec le host spécifié

### 11.4 Accès aux services

- **Via Ingress** : Si vous avez activé l'Ingress (ex: `-i prez-oauth2.local`), ajoutez le host à votre `/etc/hosts` pointant vers l'IP de votre cluster (127.0.0.1 pour Docker Desktop) et accédez via `http://prez-oauth2.local`.
- **Via LoadBalancer** : Si votre cluster supporte les LoadBalancers (ex: `minikube tunnel`), accédez via l'IP externe du service `frontend-service`.
- **Via Port-Forward** : Sinon, faites un port-forward :
  ```bash
  kubectl port-forward service/frontend-service 8083:8083 -n prez-oauth2
  ```
- **Redirection Keycloak** : Pour que le login fonctionne dans le navigateur, votre machine doit pouvoir résoudre le nom `keycloak` (utilisé par Spring en interne K8s pour la découverte OIDC).
  Ajoutez ceci à votre fichier `/etc/hosts` :
  ```text
  127.0.0.1 keycloak
  ```

---

## 12. Dépannage

| Symptôme                                                                           | Cause probable                                                                                                                                                                       | Remède                                                                                                                                                          |
|------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `Connection refused :8080`                                                         | Keycloak pas prêt                                                                                                                                                                    | Attendre 30 s, vérifier `docker logs keycloak`                                                                                                                  |
| Login OK mais pas de badge ADMIN                                                   | Realm pas réimporté                                                                                                                                                                  | `docker compose down -v && up -d`                                                                                                                               |
| `401 invalid_token` sur `/api/*`                                                   | Token expiré ou mauvais issuer                                                                                                                                                       | Vérifier `issuer-uri` dans `application.yml`                                                                                                                    |
| `403` sur `/api/admin/**` avec alice                                               | Realm non importé / mapping rôles KO                                                                                                                                                 | Inspecter le JWT sur https://jwt.io                                                                                                                             |
| `403` / `invalid_request` sur `/client/call-as-user`                               | Audience `client-service` absente du token utilisateur ou permissions insuffisantes                                                                                                  | Vérifier le client scope `client-service-audience` sur `frontend-service` (`docker compose down -v && up -d` après modif du realm)                              |
| `invalid_request: Standard token exchange is not enabled for the requested client` | L'attribut client `standard.token.exchange.enabled` n'est pas activé sur `client-service` (depuis Keycloak 26, le Token Exchange standard doit être activé explicitement par client) | Vérifier `"standard.token.exchange.enabled": "true"` dans les `attributes` de `client-service` (`realm-demo.json`), puis `docker compose down -v && up -d`      |
| `We are sorry... Invalid redirect uri` à la déconnexion                            | L'URI de post-logout `http://localhost:8083/` utilisée par `OidcClientInitiatedLogoutSuccessHandler` n'est pas déclarée pour `frontend-service`                                      | Vérifier l'attribut `"post.logout.redirect.uris": "http://localhost:8083/*"` sur `frontend-service` (`realm-demo.json`), puis `docker compose down -v && up -d` |
| Build cassé NullAway                                                               | Violation de nullité                                                                                                                                                                 | Annoter `@Nullable` ou gérer le `null` explicitement                                                                                                            |

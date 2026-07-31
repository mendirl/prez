# AGENTS.md — Guide pour agents IA travaillant sur `prez_oauth2`

Ce document est la **source de vérité** pour tout agent (Junie, Claude, etc.)
qui modifie ce projet. Lis-le en premier. Il complète le `README.md` (orienté
utilisateur) avec les conventions, contraintes et workflows internes.

---

## 1. Identité du projet

- **Nom** : `prez-oauth2` (groupId `com.example.oauth2`, version `1.0.0-SNAPSHOT`).
- **Objectif** : démonstration pédagogique de deux flows OAuth2/OIDC contre Keycloak.
- **Langue** : code en anglais, **documentation et messages utilisateur en français**.
  Conserver le français dans `README.md`, templates Thymeleaf et messages d'erreur.

---

## 2. Stack technique (à respecter)

| Couche | Choix | Notes |
|---|---|---|
| Build | Maven, **modelVersion 4.1.0** | Utiliser `<subprojects>`, pas `<modules>` |
| JDK | **Java 25** (`java.version=25`) | Préférer GraalVM |
| Framework | **Spring Boot 4.0.5** | Hérité via `spring-boot-starter-parent` |
| Sécurité | Spring Security 6 / OAuth2 Resource Server & Client | |
| Front | Spring MVC + **Thymeleaf** + **HTMX 2** | Pas de React/Vue |
| Null-safety | **JSpecify 1.0** + NullAway 0.13 + ErrorProne 2.47 | Vérifié à la compilation |
| IAM | **Keycloak 25.0.6** via docker-compose | Realm importé depuis `keycloak/realm-demo.json` |

**Ne pas** changer ces versions sans demande explicite. Toute mise à jour doit être
appliquée de façon cohérente sur tous les `pom.xml`.

---

## 3. Structure & modules

```
prez_oauth2/
├── pom.xml                    # parent (packaging pom), hérite spring-boot-starter-parent
├── docker-compose.yml         # Keycloak
├── keycloak/realm-demo.json   # realm, rôles, clients, users
├── resource-server/           # API REST :8081 (oauth2-resource-server)
├── client-service/            # client M2M :8082 (oauth2-client + Client Credentials)
└── frontend-service/          # front :8083 (oauth2-client + thymeleaf + htmx)
```

Chaque module suit la même arborescence :

```
<module>/
├── pom.xml
└── src/main/
    ├── java/com/example/<module>/
    │   ├── <X>Application.java
    │   ├── config/        # SecurityConfig, RestClient beans…
    │   └── controller/
    └── resources/
        ├── application.yml
        └── templates/     # (frontend uniquement)
```

---

## 4. Conventions de code

### Java
- Packages racine : `com.example.<module>` (`resourceserver`, `clientservice`, `frontend`).
- **Constructor injection** uniquement (pas de `@Autowired` sur champ).
- Imports : ordre Spring (`java`/`javax` → tiers → `org.springframework` → projet).
- Pas de Lombok (volontairement absent).
- Pas de wildcard imports.
- Indentation : 4 espaces.

### Nullité (obligatoire)
- Chaque **package** Java contient un `package-info.java` annoté
  `@org.jspecify.annotations.NullMarked` → non-null par défaut.
- Marquer explicitement `@org.jspecify.annotations.@Nullable` un paramètre,
  retour ou champ qui peut être `null`.
- Toute violation détectée par NullAway **casse le build** : ne jamais contourner
  via `@SuppressWarnings("NullAway")` sans justification dans un commentaire.
- Pour les types Spring nullables connus (`RestClient.body(Map.class)`, par ex.),
  vérifier explicitement la valeur ou la déclarer `@Nullable`.

### Maven
- `modelVersion 4.1.0` partout, namespace `http://maven.apache.org/POM/4.1.0`.
- Les enfants n'héritent **pas** de la version de Spring Boot : c'est le parent
  POM du projet qui hérite de `spring-boot-starter-parent`.
- Ne **pas** redéclarer de versions pour les artefacts gérés par le BOM Boot
  (ex. `spring-boot-starter-*`, `jspecify`, `thymeleaf-extras-springsecurity6`).
- Pas de propriété `maven.compiler.source/target` (gérée par `java.version`).
- Garder le parent POM minimal : `<subprojects>`, `<properties>`, `<build>` (plugin nullability).

### Sécurité — règles non négociables
- Pas de hardcoding de secrets autres que ceux de démo (`demo-secret`, `frontend-secret`)
  qui sont **uniquement** dans `keycloak/realm-demo.json` et `application.yml`.
- Toute nouvelle ressource HTTP doit être explicitement autorisée dans la
  `SecurityFilterChain` correspondante (matcher + `hasRole`/`authenticated`/`permitAll`).
- Les rôles Spring sont **préfixés `ROLE_`**, mappés depuis `realm_access.roles`
  du JWT Keycloak (voir `SecurityConfig` dans `resource-server` et `frontend-service`).
- Tester un endpoint sécurisé implique de penser : public / authentifié / rôle exact.

---

## 5. Workflow agent — étapes types

### A. Modification de code (`[CODE]`)
1. Lire les fichiers concernés (jamais deviner).
2. Appliquer les changements **minimaux**, cohérents avec le style existant.
3. Compiler : `mvn -q -DskipTests clean compile` doit retourner **exit 0**.
4. Si un endpoint sécurité ou un rôle est touché : vérifier les 3 modules
   (resource-server, frontend, realm Keycloak).
5. Mettre à jour `README.md` **uniquement** si le comportement public change
   (endpoints, utilisateurs, ports, dépendances utilisateur visibles).

### B. Ajout d'une dépendance
1. Vérifier d'abord si elle est gérée par le BOM Spring Boot → ne pas mettre
   de `<version>`.
2. Sinon, ajouter une `<version>` (et idéalement une `<property>`).
3. Refaire un `mvn clean compile` complet.

### C. Ajout d'un endpoint sécurisé
1. Ajouter le `@GetMapping` dans le contrôleur.
2. Ajouter la règle dans `SecurityConfig` (matcher + autorisation).
3. Ajouter / modifier le test manuel dans `README.md` section 6.
4. Si rôle nouveau : modifier `keycloak/realm-demo.json` + invalider
   le volume Keycloak (`docker compose down -v`).

### D. Modification du realm Keycloak
- Toujours signaler à l'utilisateur : « relance Keycloak avec `down -v && up -d` »
  car `--import-realm` ne réimporte pas si le realm existe déjà.

---

## 6. Commandes utiles (toujours non-interactives)

```bash
# Build complet
mvn -q -DskipTests clean compile

# Build avec packaging
mvn -q -DskipTests clean package

# Compiler un seul module
mvn -q -pl frontend-service -am -DskipTests compile

# Run d'un service
mvn -pl resource-server  spring-boot:run
mvn -pl client-service   spring-boot:run
mvn -pl frontend-service spring-boot:run

# Keycloak
docker compose up -d
docker compose down -v && docker compose up -d   # réimport realm
docker logs -f keycloak

# Vérifier qu'un token est bien émis
curl -s -X POST http://localhost:8080/realms/demo/protocol/openid-connect/token \
  -d "grant_type=client_credentials" \
  -d "client_id=demo-client" -d "client_secret=demo-secret" | jq
```

---

## 7. Anti-patterns à éviter

- ❌ Ajouter un `<version>` à `spring-boot-starter-*` (géré par parent).
- ❌ Remettre `<modules>` au lieu de `<subprojects>`.
- ❌ Supprimer un `package-info.java` ou son `@NullMarked`.
- ❌ Désactiver NullAway / ErrorProne pour faire passer un build.
- ❌ Injecter via `@Autowired` sur champ.
- ❌ Hardcoder une URL Keycloak ailleurs que dans `application.yml`.
- ❌ Commit avec build cassé ou tests rouges.
- ❌ Ajouter des dépendances lourdes (Lombok, MapStruct, etc.) sans demande.
- ❌ Toucher au dossier `target/` ou aux fichiers générés.

---

## 8. Ports & URLs (référence rapide)

| Service | URL |
|---|---|
| Keycloak admin | http://localhost:8080 (admin/admin) |
| OIDC discovery | http://localhost:8080/realms/demo/.well-known/openid-configuration |
| resource-server | http://localhost:8081 |
| client-service | http://localhost:8082 |
| frontend-service | http://localhost:8083 |

---

## 9. Utilisateurs de démo

| User | Pwd | Rôles |
|---|---|---|
| `alice` | `alice` | ADMIN, USER |
| `bob`   | `bob`   | USER |
| `demo`  | `demo`  | USER |

Ne **pas** ajouter de nouveaux utilisateurs sans en discuter (le projet est minimaliste).

---

## 10. Skills disponibles

Les skills spécialisés se trouvent dans `.junie/skills/`. Lire le `SKILL.md`
de chacun avant d'attaquer un domaine :

- `build-and-run/` — compiler, lancer, dépanner les builds Maven multi-modules.
- `keycloak-realm/` — modifier le realm (rôles, users, clients) sans casser l'import.
- `jspecify-nullsafety/` — gérer la null-safety, corriger les erreurs NullAway.
- `oauth2-flows/` — ajouter / modifier un flow OAuth2 / OIDC dans ce projet.

---

## 11. Définition de « terminé »

Avant de soumettre une tâche, vérifier :

- [ ] `mvn -q -DskipTests clean compile` retourne 0.
- [ ] Aucun `@SuppressWarnings("NullAway")` non justifié ajouté.
- [ ] Si endpoint ou realm modifié : `README.md` à jour.
- [ ] Pas de fichier hors scope modifié (target/, .idea/, etc.).
- [ ] Changements alignés avec la stack (pas de nouvelle techno furtive).
- [ ] Messages utilisateurs / commentaires en français cohérent avec l'existant.

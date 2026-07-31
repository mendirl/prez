# Skill: build-and-run

Compiler, lancer et dépanner ce projet Maven multi-modules (Spring Boot 4 / Java 25).

## Quand l'utiliser
- L'utilisateur signale une erreur de compilation Maven.
- Besoin de démarrer un ou plusieurs services localement.
- Mise à jour de version de dépendance / parent / JDK.

## Prérequis vérifiables
```bash
java -version                # doit afficher 25.x
mvn -v | head -1             # doit afficher 3.9.x ou 4.x
docker compose version
```

## Commandes canoniques

| But | Commande |
|---|---|
| Compile rapide tout le projet | `mvn -q -DskipTests clean compile` |
| Package complet (jars exécutables) | `mvn -q -DskipTests clean package` |
| Compile un seul module + dépendants | `mvn -q -pl <module> -am -DskipTests compile` |
| Run d'un service | `mvn -pl <module> spring-boot:run` |
| Inspecter une propriété effective | `mvn -q help:evaluate -Dexpression=<prop> -DforceStdout` |
| Voir l'arbre des dépendances | `mvn -pl <module> dependency:tree` |

## Ordre de démarrage des services
1. Keycloak (`docker compose up -d`) — attendre `curl :8080/realms/demo` OK.
2. `resource-server` (8081)
3. `client-service` (8082) ou `frontend-service` (8083) — indifférent ensuite.

## Erreurs fréquentes

| Message | Diagnostic | Correctif |
|---|---|---|
| `release version 25 not supported` | Mauvais JDK actif | `export JAVA_HOME=...` vers JDK 25 |
| `Non-resolvable parent POM ... spring-boot-starter-parent:4.0.5` | Pas de réseau / Maven offline | Réessayer en ligne, ou `mvn -U` |
| `NullAway: returning @Nullable expression from method with @NonNull return type` | Violation null-safety réelle | Voir skill `jspecify-nullsafety` |
| `Connection refused: localhost:8080` au runtime | Keycloak pas prêt | `docker logs keycloak` ; attendre import realm |
| `[ERROR] ... <modules>` invalide | Régression syntaxe Maven 4.1 | Réutiliser `<subprojects>/<subproject>` |

## Règles
- **Toujours** vérifier avec `mvn -q -DskipTests clean compile` (exit 0) avant de soumettre.
- Ne pas passer `-DskipTests` quand des tests existent (le projet n'en a pas pour le moment, donc OK).
- Ne pas committer `target/`.

# Skill: jspecify-nullsafety

Gérer la null-safety du projet : JSpecify 1.0 + NullAway 0.13 + ErrorProne 2.47,
configurés via `nullability-maven-plugin` (extension) dans le parent POM.

## Quand l'utiliser
- Le build échoue avec un message `NullAway: ...`.
- Ajout d'une nouvelle classe / nouveau package → besoin de `@NullMarked`.
- Manipulation d'une API tierce qui peut renvoyer `null`.

## Modèle mental
- **Tout package** Java contient un `package-info.java` annoté
  `@org.jspecify.annotations.NullMarked` → tous les types y sont **non-null par défaut**.
- Pour autoriser `null` : annoter localement avec `@org.jspecify.annotations.@Nullable`.
- NullAway vérifie ces contraintes **à la compilation**. Un build cassé = un vrai bug.

## Recettes

### Créer un nouveau package
1. Créer `src/main/java/com/example/<module>/<pkg>/package-info.java` :
   ```java
   @NullMarked
   package com.example.<module>.<pkg>;

   import org.jspecify.annotations.NullMarked;
   ```
2. Le faire pour **chaque** sous-package (l'annotation ne se propage pas).

### Consommer une API qui peut renvoyer null
```java
@Nullable Map<String, Object> body = restClient.get()...body(Map.class);
if (body == null) {
    throw new IllegalStateException("Réponse vide");
}
// body est désormais non-null dans la suite du code (flow analysis NullAway)
```

### Champ optionnel
```java
private @Nullable String correlationId;
```

## Erreurs typiques NullAway

| Message | Sens | Fix |
|---|---|---|
| `returning @Nullable expression from method with @NonNull return type` | Le retour de méthode peut être null mais signature non-null | Soit annoter le retour `@Nullable`, soit vérifier + lever exception |
| `passing @Nullable parameter ... where @NonNull is required` | Param non-null appelé avec une valeur potentiellement null | Vérifier null avant ou annoter param `@Nullable` |
| `dereferenced expression ... is @Nullable` | Déréférencement non protégé | Tester != null d'abord |
| `assigning @Nullable expression to @NonNull field` | Affectation à un champ non-null | Garde / `Objects.requireNonNull` / champ `@Nullable` |

## Interdits
- ❌ `@SuppressWarnings("NullAway")` sans **commentaire justificatif** au-dessus.
- ❌ Supprimer un `package-info.java` "parce que ça compile pas".
- ❌ Désactiver le plugin `nullability-maven-plugin` dans le parent POM.
- ❌ `Objects.requireNonNullElse` pour masquer une vraie null-source : préférer
  remonter le `@Nullable` dans la signature.

## Vérification

```bash
# Le build doit passer
mvn -q -DskipTests clean compile

# Vérifier que NullAway / ErrorProne sont bien actifs
mvn -DskipTests clean compile 2>&1 | grep -iE "nullaway|errorprone|nullability"
# → doit afficher : [nullability] Configuring ErrorProne 2.47.0 and NullAway 0.13.1
```

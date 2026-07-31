# Exemple d'Agent Koog avec Kotlin et Maven

Ce projet montre comment créer un agent simple en utilisant le framework **Koog** de JetBrains avec **Kotlin**, configuré pour se connecter à une API compatible OpenAI (comme LM Studio, Ollama, ou LocalAI).

## Prérequis

- **Java 25** (ou supérieur)
- **Maven**
- Une API compatible OpenAI en cours d'exécution (ex: `http://localhost:1234/v1/openai`)

## Configuration

L'agent est configuré dans `src/main/kotlin/example/Main.kt`. Il utilise `OpenAIClientSettings` pour rediriger les requêtes vers un serveur compatible OpenAI local.

### Code Clé (Kotlin)

```kotlin
val settings = OpenAIClientSettings(
    "http://localhost:1234/v1/openai",
    ConnectionTimeoutConfig(),
    "chat/completions", "responses", "embeddings", "moderations", "models"
)

val executor = PromptExecutor.builder()
    .openAI(apiKey, settings)
    .build()

// Pour les modèles personnalisés, spécifier les capacités et la longueur de contexte
val model = LLModel(
    LLMProvider.OpenAI,
    "qwen3-coder-next",
    listOf(
        LLMCapability.Completion,
        LLMCapability.Temperature,
        LLMCapability.OpenAIEndpoint.Completions
    ),
    32768L,
    4096L
)

// Enregistrer le modèle
OpenAIModels.addCustomModel(model)

val agent = AIAgent.builder()
    .promptExecutor(executor)
    .llmModel(model)
    .build()
```

## Exécution

Pour compiler et lancer l'exemple :

```bash
mvn compile exec:java -Dexec.mainClass="example.MainKt"
```

## Fonctionnalités

- Utilisation de `OpenAIClientSettings` pour les endpoints personnalisés.
- Utilisation de `AIAgent` avec Kotlin.
- Configuration dynamique d'un modèle LLM personnalisé (`qwen3-coder-next`).

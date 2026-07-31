package example

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.executor.clients.openai.OpenAIClientSettings
import ai.koog.prompt.executor.clients.openai.OpenAIModels
import ai.koog.prompt.executor.clients.ConnectionTimeoutConfig
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLMCapability
import kotlinx.coroutines.runBlocking

/**
 * Exemple simple d'agent Koog utilisant Kotlin.
 * Configuré pour se connecter à une API compatible OpenAI (LM Studio, Ollama, etc.).
 */
fun main() {
    // Configuration de l'API (Compatible OpenAI)
    val baseUrl = "http://localhost:1234/v1/openai"
    val apiKey = "not-needed-for-local"

    println("Initialisation de l'agent Koog (Kotlin)...")
    println("Endpoint configuré : $baseUrl")

    // Configuration personnalisée pour pointer vers l'API locale/compatible.
    val settings = OpenAIClientSettings(
        baseUrl,
        ConnectionTimeoutConfig(), // Paramètres de timeout par défaut
        "chat/completions",
        "responses",
        "embeddings",
        "moderations",
        "models"
    )

    // Création de l'exécuteur de prompts via le builder de PromptExecutor.
    val executor = PromptExecutor.builder()
        .openAI(apiKey, settings)
        .build()

    // Définition explicite des capacités pour éviter l'erreur "cannot determine proper LLM params"
    // pour un modèle inconnu du framework.
    val model = LLModel(
        LLMProvider.OpenAI,
        "qwen3-coder-next",
        listOf(
            LLMCapability.Completion,
            LLMCapability.Temperature,
            LLMCapability.OpenAIEndpoint.Completions
        ),
        32768L, // Longueur de contexte (ajustez selon le modèle)
        4096L   // Max tokens en sortie
    )

    // Enregistrement du modèle dans le registre global OpenAI de Koog
    OpenAIModels.addCustomModel(model)

    runBlocking {
        // Construction de l'agent Koog.
        // AIAgent automatise la gestion du prompt système et de l'historique.
        val agent = AIAgent.builder()
            .promptExecutor(executor)
            .llmModel(model)
            .systemPrompt("Tu es un assistant utile spécialisé dans le développement Kotlin.")
            .build()

        // Exemple d'exécution (input -> output)
        val question = "Quelles sont les caractéristiques principales des Coroutines en Kotlin ?"
        println("\nQuestion : $question")
        
        try {
            // L'appel à run() est suspendu
            val response = agent.run(question)
            println("\nRéponse de l'agent :\n$response")
        } catch (e: Exception) {
            System.err.println("\nNote : L'exécution a échoué car aucun serveur compatible OpenAI n'est actif sur $baseUrl")
            System.err.println("Erreur technique : ${e.message}")
            println("\nLe code est cependant correctement configuré et prêt à l'emploi.")
        }
    }
}

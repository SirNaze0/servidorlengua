package com.example.servidorlengua.service

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Service
class GeminiValidationService(
    @Value("\${gemini.api.key}") private val apiKey: String,
    private val objectMapper: ObjectMapper
) {

    private val client = WebClient.builder()
        .baseUrl("https://generativelanguage.googleapis.com")
        .defaultHeader("User-Agent", "Spring-WebClient/1.0")
        .clientConnector(
            ReactorClientHttpConnector(
                HttpClient.create()
                    .responseTimeout(Duration.ofSeconds(120))  // Timeout aumentado a 120s
                    .secure { sslSpec ->
                        sslSpec
                            .sslContext(
                                SslContextBuilder.forClient()
                                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                                    .build()
                            )
                            .handshakeTimeout(Duration.ofSeconds(30))
                    }
            )
        )
        .codecs { config ->
            config.defaultCodecs().maxInMemorySize(32 * 1024 * 1024) // Buffer aumentado a 32MB
            config.defaultCodecs().enableLoggingRequestDetails(true)
        }
        .build()

    /**
     * Valida una traducción usando Google Gemini 2.5 Flash
     */
    fun validateTranslation(originalSpanish: String, userQuechua: String): Mono<AIValidationResult> {
        // ✅ PROMPT EN ESPAÑOL OPTIMIZADO
        val prompt = """
NO PIENSES. NO ANALICES. RESPONDE INMEDIATAMENTE.

EJEMPLOS:
Español="Hola" Quechua="Allinllachu" → {"accuracy":95,"is_correct":true,"feedback":"correcto","errors":[],"suggestion":null}
Español="Adiós" Quechua="Tupananchiskama" → {"accuracy":90,"is_correct":true,"feedback":"bien","errors":[],"suggestion":null}

AHORA COPIA EL FORMATO EXACTO:
Español="$originalSpanish" Quechua="$userQuechua" →
""".trimIndent()

        val request = mapOf(
            "contents" to listOf(
                mapOf("parts" to listOf(mapOf("text" to prompt)))
            ),
            "generationConfig" to mapOf(
                "temperature" to 0.0,
                "maxOutputTokens" to 4096,
                "topK" to 1,
                "topP" to 1.0
            )
        )

        println("🚀 Enviando request a Gemini...")
        println("📦 Request body: ${objectMapper.writeValueAsString(request)}")

        return client.post()
            .uri("/v1/models/gemini-2.5-flash:generateContent?key=$apiKey")
            .header("Content-Type", "application/json")
            .bodyValue(request)
            .retrieve()
            .bodyToMono(String::class.java)  // ✅ Obtener como String
            .doOnSuccess { response ->
                println("✅ Respuesta COMPLETA recibida de Gemini")
                println("📄 Longitud de respuesta: ${response.length} caracteres")
                println("📄 Respuesta completa:\n$response")
            }
            .map { rawJson ->
                try {
                    println("🔄 Parseando JSON de Gemini...")
                    // Parsear la respuesta completa
                    val geminiResponse = objectMapper.readValue<GeminiResponse>(rawJson)
                    parseGeminiResponse(geminiResponse, originalSpanish, userQuechua)
                } catch (e: Exception) {
                    println("❌ Error parseando respuesta completa de Gemini: ${e.message}")
                    e.printStackTrace()
                    AIValidationResult(
                        isCorrect = null,
                        accuracyPercentage = null,
                        feedback = "Error al parsear respuesta de IA: ${e.message}",
                        errors = emptyList(),
                        suggestion = null,
                        originalText = originalSpanish,
                        userText = userQuechua
                    )
                }
            }
            .onErrorResume { error ->
                println("❌ Error en Gemini API: ${error.message}")
                error.printStackTrace()
                Mono.just(
                    AIValidationResult(
                        isCorrect = null,
                        accuracyPercentage = null,
                        feedback = "No se pudo validar con IA: ${error.message}",
                        errors = emptyList(),
                        suggestion = null,
                        originalText = originalSpanish,
                        userText = userQuechua
                    )
                )
            }
    }

    /**
     * Parsea la respuesta de Gemini y extrae el JSON
     */
    private fun parseGeminiResponse(
        response: GeminiResponse,
        originalSpanish: String,
        userQuechua: String
    ): AIValidationResult {
        return try {
            val text = response.candidates
                ?.firstOrNull()
                ?.content
                ?.parts
                ?.firstOrNull()
                ?.text
                ?: throw Exception("Respuesta vacía de Gemini")

            println("🔍 Respuesta raw de Gemini (texto extraído): $text")

            // Limpiar markdown y espacios primero
            var cleanText = text
                .replace("```json", "")
                .replace("```", "")
                .trim()

            // 🔍 EXTRACCIÓN ROBUSTA DE JSON
            // Busca el primer '{' y el último '}' para ignorar texto conversacional previo
            val startIndex = cleanText.indexOf("{")
            val endIndex = cleanText.lastIndexOf("}")
            
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                cleanText = cleanText.substring(startIndex, endIndex + 1)
            }

            println("🧹 JSON limpio ANTES de validación: $cleanText")

            // ✨ VALIDAR QUE EL JSON ESTÉ COMPLETO
            if (!cleanText.endsWith("}")) {
                println("⚠️ JSON truncado detectado, intentando recuperar...")

                // Contar llaves para detectar JSON incompleto
                val openBraces = cleanText.count { it == '{' }
                val closeBraces = cleanText.count { it == '}' }

                if (openBraces > closeBraces) {
                    // Cerrar strings abiertas si hay comillas impares
                    if (cleanText.count { it == '"' } % 2 != 0) {
                        cleanText += "\""
                    }
                    // Cerrar el objeto JSON
                    repeat(openBraces - closeBraces) {
                        cleanText += "\n}"
                    }
                    println("🔧 JSON reparado: $cleanText")
                }
            }

            println("✅ JSON limpio DESPUÉS de validación: $cleanText")

            // Deserializar usando ObjectMapper con kotlin-module
            val geminiResult = objectMapper.readValue<GeminiValidationResponse>(cleanText)

            println("✅ Validación parseada exitosamente:")
            println("   - Accuracy: ${geminiResult.accuracy}%")
            println("   - Is Correct: ${geminiResult.isCorrect}")
            println("   - Feedback: ${geminiResult.feedback}")

            AIValidationResult(
                isCorrect = geminiResult.isCorrect,
                accuracyPercentage = geminiResult.accuracy,
                feedback = geminiResult.feedback,
                errors = geminiResult.errors,
                suggestion = geminiResult.suggestion,
                originalText = originalSpanish,
                userText = userQuechua
            )

        } catch (e: Exception) {
            println("❌ Error parseando JSON interno: ${e.message}")
            e.printStackTrace()
            AIValidationResult(
                isCorrect = null,
                accuracyPercentage = null,
                feedback = "Error al procesar la validación: ${e.message}",
                errors = emptyList(),
                suggestion = null,
                originalText = originalSpanish,
                userText = userQuechua
            )
        }
    }
}

// ============================================
// MODELOS DE DATOS
// ============================================

@JsonIgnoreProperties(ignoreUnknown = true)  // ✅ Ignora campos extras
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonIgnoreProperties(ignoreUnknown = true)  // ✅ Ignora finishReason, index, etc.
data class GeminiCandidate(
    val content: GeminiContent?
)

@JsonIgnoreProperties(ignoreUnknown = true)  // ✅ Ignora "role" y otros campos
data class GeminiContent(
    val parts: List<GeminiPart>?
)

@JsonIgnoreProperties(ignoreUnknown = true)  // ✅ Por si acaso
data class GeminiPart(
    val text: String?
)

data class GeminiValidationResponse(
    val accuracy: Int,
    @JsonProperty("is_correct") val isCorrect: Boolean,
    val feedback: String,
    val errors: List<String>,
    val suggestion: String?
)

data class AIValidationResult(
    val isCorrect: Boolean?,
    val accuracyPercentage: Int?,
    val feedback: String,
    val errors: List<String>,
    val suggestion: String?,
    val originalText: String,
    val userText: String
)
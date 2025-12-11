package com.example.servidorlengua.service

import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import kotlin.math.max

@Service
class LibreTranslateValidationService(
    private val googleTranslationService: GoogleTranslationService
) {

    /**
     * Valida si el texto en quechua es una traducción correcta del español original
     * Usa Google Translate para back-translation (QU → ES)
     *
     * @param originalSpanish Texto original en español que se mostró al usuario
     * @param quechuaTranslation Texto en quechua que se quiere validar
     * @return ValidationResult con porcentaje de precisión
     */
    fun validateTranslation(originalSpanish: String, quechuaTranslation: String): Mono<ValidationResult> {
        // Back-translate usando Google: Quechua → Español
        return googleTranslationService.translate(quechuaTranslation, "qu", "es")
            .map { backTranslatedSpanish ->
                val accuracy = calculateSimilarity(originalSpanish, backTranslatedSpanish)
                val percentage = (accuracy * 100).toInt()

                ValidationResult(
                    isValid = accuracy >= 0.70, // 70% o más = válida
                    accuracyPercentage = percentage,
                    originalText = originalSpanish,
                    quechuaText = quechuaTranslation,
                    backTranslation = backTranslatedSpanish,
                    message = getValidationMessage(percentage)
                )
            }
            .onErrorResume { error ->
                // Si falla la validación, devolver resultado neutral
                Mono.just(
                    ValidationResult(
                        isValid = null,
                        accuracyPercentage = null,
                        originalText = originalSpanish,
                        quechuaText = quechuaTranslation,
                        backTranslation = null,
                        message = "No se pudo validar: ${error.message}"
                    )
                )
            }
    }

    /**
     * Calcula similitud usando distancia de Levenshtein
     * Retorna valor entre 0.0 (totalmente diferente) y 1.0 (idéntico)
     */
    private fun calculateSimilarity(text1: String, text2: String): Double {
        val s1 = text1.lowercase().trim()
        val s2 = text2.lowercase().trim()

        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val distance = levenshteinDistance(s1, s2)
        val maxLength = max(s1.length, s2.length)

        return 1.0 - (distance.toDouble() / maxLength)
    }

    /**
     * Algoritmo de distancia de Levenshtein
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // eliminación
                    dp[i][j - 1] + 1,      // inserción
                    dp[i - 1][j - 1] + cost // sustitución
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    /**
     * Genera mensaje descriptivo según el porcentaje
     */
    private fun getValidationMessage(percentage: Int): String {
        return when {
            percentage >= 90 -> "Excelente traducción"
            percentage >= 80 -> "Muy buena traducción"
            percentage >= 70 -> "Traducción aceptable"
            percentage >= 50 -> "Traducción con diferencias notables"
            else -> "Traducción incorrecta o muy diferente"
        }
    }

    // Resultado de validación
    data class ValidationResult(
        val isValid: Boolean?,              // true/false/null (si no se pudo validar)
        val accuracyPercentage: Int?,       // 0-100
        val originalText: String,           // Español original
        val quechuaText: String,            // Quechua a validar
        val backTranslation: String?,       // Español resultante del back-translate
        val message: String                 // Mensaje descriptivo
    )
}
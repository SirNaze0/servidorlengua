package com.example.servidorlengua.service

import com.example.servidorlengua.model.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

@Service
class LessonService(
    @Value("\${supabase.url}") private val supabaseUrl: String,
    @Value("\${supabase.key}") private val supabaseKey: String,
    private val googleTranslationService: GoogleTranslationService,
    private val validationService: LibreTranslateValidationService,
    private val geminiValidationService: GeminiValidationService
) {

    private val client = WebClient.builder()
        .baseUrl(supabaseUrl)
        .defaultHeader("apikey", supabaseKey)
        .defaultHeader("Authorization", "Bearer $supabaseKey")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build()

    // -------------------------------------------------------
    // 1. Obtener todas las lecciones
    // -------------------------------------------------------
    fun getAllLessons(): Mono<List<Lesson>> {
        return client.get()
            .uri("/rest/v1/lessons?select=*")
            .retrieve()
            .bodyToFlux(Lesson::class.java)
            .collectList()
    }

    // -------------------------------------------------------
    // 2. Obtener progreso por usuario
    // -------------------------------------------------------
    fun getUserProgress(userId: String): Mono<List<LessonProgress>> {
        return client.get()
            .uri("/rest/v1/lesson_progress?user_id=eq.$userId&select=*")
            .retrieve()
            .bodyToFlux(LessonProgress::class.java)
            .collectList()
    }

    // -------------------------------------------------------
    // 3. ACTUALIZAR PROGRESO (UPSERT — insert o update)
    // -------------------------------------------------------
    fun updateUserProgress(request: LessonProgressUpdateRequest): Mono<Void> {
        val update = mapOf(
            "user_id" to request.user_id,
            "lesson_id" to request.lesson_id,
            "completed" to request.completed,
            "minutes" to request.minutes
        )

        return client.post()
            .uri("/rest/v1/lesson_progress?on_conflict=user_id,lesson_id")
            .header("Prefer", "resolution=merge-duplicates")
            .bodyValue(update)
            .retrieve()
            .bodyToMono(Void::class.java)
    }

    // -------------------------------------------------------
    // 3B. ACTUALIZAR PROGRESO POR LOTES (SYNC)
    // -------------------------------------------------------
    fun updateUserProgressBatch(request: LessonProgressBatchRequest): Mono<Void> {
        return Flux.fromIterable(request.updates)
            .flatMap { update ->
                updateUserProgress(update)
                    .onErrorResume { e ->
                        println("Error syncing update: $update | Error: ${e.message}")
                        Mono.empty() // Continue with others even if one fails
                    }
            }
            .then()
    }

    // -------------------------------------------------------
    // 4. Resumen del progreso
    // -------------------------------------------------------
    fun getProgressSummary(userId: String): Mono<LessonProgressSummary> {
        return getUserProgress(userId).map { progressList ->
            val completedCount = progressList.count { it.completed }
            val inProgressCount = progressList.count { !it.completed && it.minutes > 0 }
            val totalMinutes = progressList.sumOf { it.minutes }

            LessonProgressSummary(
                completedLessons = completedCount,
                inProgressLessons = inProgressCount,
                totalMinutes = totalMinutes
            )
        }
    }

    // -------------------------------------------------------
    // 5. TRADUCIR TEXTO PARA LECCIÓN (ES → QU)
    // -------------------------------------------------------
    fun translateForLesson(spanishText: String): Mono<LessonTranslation> {
        return googleTranslationService.translate(spanishText, "es", "qu")
            .map { quechuaText ->
                LessonTranslation(
                    spanish = spanishText,
                    quechua = quechuaText
                )
            }
    }

    // -------------------------------------------------------
    // 6. VALIDAR RESPUESTA DEL USUARIO (con IA - Gemini)
    // -------------------------------------------------------
    fun validateUserAnswerWithAI(request: LessonAnswerRequest): Mono<LessonAnswerResponse> {
        return geminiValidationService.validateTranslation(
            originalSpanish = request.originalSpanish,
            userQuechua = request.userAnswer
        ).map { aiResult ->
            val percentage = aiResult.accuracyPercentage ?: 0

            LessonAnswerResponse(
                isCorrect = aiResult.isCorrect ?: false,
                accuracyPercentage = percentage,
                correctAnswer = request.correctQuechua,
                userAnswer = request.userAnswer,
                feedback = aiResult.feedback,
                validationDetails = LessonValidationDetails(
                    backTranslation = aiResult.suggestion,
                    message = if (aiResult.errors.isEmpty())
                        "Sin errores detectados"
                    else
                        aiResult.errors.joinToString(", ")
                )
            )
        }
    }

    // -------------------------------------------------------
    // 6B. VALIDAR RESPUESTA DEL USUARIO (método original - back-translation)
    // -------------------------------------------------------
    fun validateUserAnswer(request: LessonAnswerRequest): Mono<LessonAnswerResponse> {
        return validationService.validateTranslation(
            originalSpanish = request.originalSpanish,
            quechuaTranslation = request.userAnswer
        ).map { validationResult ->
            val percentage = validationResult.accuracyPercentage ?: 0

            LessonAnswerResponse(
                isCorrect = validationResult.isValid ?: false,
                accuracyPercentage = percentage,
                correctAnswer = request.correctQuechua,
                userAnswer = request.userAnswer,
                feedback = generateFeedback(percentage),
                validationDetails = LessonValidationDetails(
                    backTranslation = validationResult.backTranslation,
                    message = validationResult.message
                )
            )
        }
    }

    // -------------------------------------------------------
    // 7. FEEDBACK PERSONALIZADO SEGÚN PORCENTAJE
    // -------------------------------------------------------
    private fun generateFeedback(percentage: Int): String {
        return when (percentage) {
            in 95..100 -> "¡Perfecto! Tu traducción es excelente 🎉"
            in 85..94 -> "¡Muy bien! Tu traducción es muy buena 👏"
            in 70..84 -> "Bien hecho. Tu traducción es correcta ✓"
            in 50..69 -> "Casi correcto. Revisa algunos detalles 🤔"
            else -> "Intenta de nuevo. Revisa la traducción 📚"
        }
    }
}
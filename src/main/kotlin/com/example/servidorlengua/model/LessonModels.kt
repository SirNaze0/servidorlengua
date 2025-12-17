package com.example.servidorlengua.model

// ----------------------
// MODELO DE LECCIÓN
// ----------------------
data class Lesson(
    val id: Long,
    val title: String,
    val level: String,
    val duration: Int,
    val is_offline: Boolean,
    val tags: List<String>,
    val content: Any? = null
)

// ----------------------
// MODELO DE PROGRESO
// ----------------------
data class LessonProgress(
    val id: Long? = null,
    val user_id: String,
    val lesson_id: Long,
    val completed: Boolean,
    val minutes: Int
)

// ----------------------
// REQUEST PARA UPDATE PROGRESS
// ----------------------
data class LessonProgressUpdateRequest(
    val user_id: String,
    val lesson_id: Long,
    val completed: Boolean,
    val minutes: Int
)

// ----------------------
// RESPONSE DE RESUMEN PARA FRONTEND
// ----------------------
data class LessonProgressSummary(
    val completedLessons: Int,
    val inProgressLessons: Int,
    val totalMinutes: Int
)

// ----------------------
// MODELOS DE TRADUCCIÓN Y VALIDACIÓN
// ----------------------

/**
 * Request para traducir texto en una lección
 */
data class LessonTranslateRequest(
    val text: String
)

/**
 * Response con la traducción
 */
data class LessonTranslation(
    val spanish: String,
    val quechua: String
)

/**
 * Request cuando el usuario responde una lección
 */
data class LessonAnswerRequest(
    val originalSpanish: String,      // Texto en español que vio el usuario
    val correctQuechua: String,       // Traducción correcta esperada
    val userAnswer: String            // Respuesta del usuario en quechua
)

/**
 * Response con la validación de la respuesta
 */
data class LessonAnswerResponse(
    val isCorrect: Boolean,           // ¿Es correcta la respuesta?
    val accuracyPercentage: Int,      // Porcentaje de precisión (0-100)
    val correctAnswer: String,        // Respuesta correcta
    val userAnswer: String,           // Respuesta del usuario
    val feedback: String,             // Mensaje de feedback amigable
    val validationDetails: LessonValidationDetails?  // Detalles técnicos opcionales
)

/**
 * Detalles técnicos de la validación
 */
data class LessonValidationDetails(
    val backTranslation: String?,     // Traducción inversa para debug
    val message: String               // Mensaje técnico del validador
)
package com.example.servidorlengua.controller

import com.example.servidorlengua.model.*
import com.example.servidorlengua.service.LessonService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api/lessons")
@CrossOrigin(origins = ["*"])
class LessonController(
    private val lessonService: LessonService
) {

    // ----------------------------------------------
    // 1. OBTENER TODAS LAS LECCIONES
    // ----------------------------------------------
    @GetMapping
    fun getLessons() = lessonService.getAllLessons()

    // ----------------------------------------------
    // 2. OBTENER PROGRESO DEL USUARIO
    // ----------------------------------------------
    @GetMapping("/progress/{userId}")
    fun getUserProgress(@PathVariable userId: String) =
        lessonService.getUserProgress(userId)

    // ----------------------------------------------
    // 3. OBTENER RESUMEN (COMPLETADAS / PROGRESO / MINUTOS)
    // ----------------------------------------------
    @GetMapping("/summary/{userId}")
    fun getProgressSummary(@PathVariable userId: String) =
        lessonService.getProgressSummary(userId)

    // ----------------------------------------------
    // 4. ACTUALIZAR PROGRESO
    // ----------------------------------------------
    @PostMapping("/progress/update")
    fun updateProgress(@RequestBody request: LessonProgressUpdateRequest): Mono<Void> =
        lessonService.updateUserProgress(request)

    // ----------------------------------------------
    // 5. TRADUCIR TEXTO PARA LECCIÓN (ES → QU)
    // ----------------------------------------------
    @PostMapping("/translate")
    fun translateText(@RequestBody request: LessonTranslateRequest): Mono<LessonTranslation> =
        lessonService.translateForLesson(request.text)

    // ----------------------------------------------
    // 6. VALIDAR RESPUESTA DEL USUARIO (CON IA - GEMINI)
    // ----------------------------------------------
    @PostMapping("/validate-answer-ai")
    fun validateAnswerWithAI(@RequestBody request: LessonAnswerRequest): Mono<LessonAnswerResponse> =
        lessonService.validateUserAnswerWithAI(request)

    // ----------------------------------------------
    // 6B. VALIDAR RESPUESTA (MÉTODO CLÁSICO - BACK-TRANSLATION)
    // ----------------------------------------------
    @PostMapping("/validate-answer")
    fun validateAnswer(@RequestBody request: LessonAnswerRequest): Mono<LessonAnswerResponse> =
        lessonService.validateUserAnswer(request)
}
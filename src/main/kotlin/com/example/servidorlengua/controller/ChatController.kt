package com.example.servidorlengua.controller

import com.example.servidorlengua.service.GoogleTranslationService
import com.example.servidorlengua.service.SupabaseService
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api")
class ChatController(
    private val translationService: GoogleTranslationService,
    private val supabaseService: SupabaseService
) {

    @PostMapping("/translate")
    fun translate(@RequestBody request: TranslateRequest): Mono<Map<String, String>> {
        val source = request.source ?: "es"
        val target = request.target ?: "qu"
        return translationService.translate(request.q, source, target)
            .map { mapOf("translatedText" to it) }
    }

    @GetMapping("/chat/messages")
    fun getMessages(): Mono<List<SupabaseService.ChatMessage>> {
        return supabaseService.getMessages()
    }

    @PostMapping("/chat/messages")
    fun sendMessage(@RequestBody message: SendMessageRequest): Mono<Void> {
        return supabaseService.sendMessage(message.content, message.senderId, message.senderName)
    }

    data class TranslateRequest(
        val q: String,
        val source: String? = "es",
        val target: String? = "qu"
    )

    data class SendMessageRequest(
        val content: String,
        val senderId: String,
        val senderName: String
    )
}

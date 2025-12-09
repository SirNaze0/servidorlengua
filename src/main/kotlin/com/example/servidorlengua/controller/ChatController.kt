package com.example.servidorlengua.controller

import com.example.servidorlengua.service.GoogleTranslationService
import com.example.servidorlengua.service.SupabaseService
import com.example.servidorlengua.handler.ChatWebSocketHandler
import org.springframework.web.bind.annotation.*
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api")
class ChatController(
    private val translationService: GoogleTranslationService,
    private val supabaseService: SupabaseService,
    private val chatWebSocketHandler: ChatWebSocketHandler // Inject Handler
) {

    @PostMapping("/translate")
    fun translate(@RequestBody request: TranslateRequest): Mono<Map<String, String>> {
        val source = request.source ?: "es"
        val target = request.target ?: "qu"
        
        // 1. Intentar buscar en Validaciones de BD
        return supabaseService.getValidatedTranslation(request.q, source, target)
            .map { validatedText -> 
                mapOf("translatedText" to validatedText) 
            }
            .switchIfEmpty(
                // 2. Si no hay validación aprobada, usar API externa
                translationService.translate(request.q, source, target)
                    .map { mapOf("translatedText" to it) }
            )
    }

    @GetMapping("/chat/messages")
    fun getMessages(): Mono<List<SupabaseService.ChatMessage>> {
        return supabaseService.getMessages()
    }

    @PostMapping("/chat/messages")
    fun sendMessage(@RequestBody message: SendMessageRequest): Mono<Void> {
        return supabaseService.sendMessage(message.content, message.senderId, message.senderName)
            .then(Mono.fromRunnable {
                try {
                    // Broadcast to WebSocket after successful save
                    // Construct a message compatible with Frontend ChatMessageDTO
                    val chatMessage = mapOf(
                        "id" to System.currentTimeMillis(), // Temporary ID for real-time display
                        "content" to message.content,
                        "sender_id" to message.senderId,
                        "sender_name" to message.senderName,
                        "created_at" to java.time.Instant.now().toString()
                    )
                    chatWebSocketHandler.broadcast(chatMessage)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            })
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

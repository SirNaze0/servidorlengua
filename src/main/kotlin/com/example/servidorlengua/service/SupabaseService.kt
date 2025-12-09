package com.example.servidorlengua.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Service
class SupabaseService(
    @Value("\${supabase.url}") private val supabaseUrl: String,
    @Value("\${supabase.key}") private val supabaseKey: String
) {

    private val client = WebClient.builder()
        .baseUrl(supabaseUrl)
        .defaultHeader("apikey", supabaseKey)
        .defaultHeader("Authorization", "Bearer $supabaseKey")
        .defaultHeader(HttpHeaders.CONTENT_TYPE, "application/json")
        .build()

    fun getValidations(): Mono<List<Validation>> {
        return client.get()
            .uri("/rest/v1/validation?select=*&order=id.asc")
            .retrieve()
            .bodyToFlux(Validation::class.java)
            .collectList()
    }

    fun createValidation(frase: String, fraseTraducida: String): Mono<Void> {
        val validation = mapOf(
            "frase" to frase,
            "frase_traducida" to fraseTraducida,
            "nmr_validacion" to 0
        )
        return client.post()
            .uri("/rest/v1/validation")
            .bodyValue(validation)
            .retrieve()
            .bodyToMono(Void::class.java)
    }

    fun updateValidation(id: Long, fraseTraducida: String): Mono<Void> {
        val update = mapOf(
            "frase_traducida" to fraseTraducida
        )
        return client.patch()
            .uri("/rest/v1/validation?id=eq.$id")
            .bodyValue(update)
            .retrieve()
            .bodyToMono(Void::class.java)
    }

    fun incrementValidationCounter(id: Long): Mono<Void> {
        // Read-Modify-Write approach for prototype
        return client.get()
            .uri("/rest/v1/validation?id=eq.$id&select=nmr_validacion")
            .retrieve()
            .bodyToFlux(Validation::class.java)
            .next()
            .flatMap { currentValidation ->
                val newCount = currentValidation.nmrValidacion + 1
                val update = mapOf("nmr_validacion" to newCount)
                client.patch()
                    .uri("/rest/v1/validation?id=eq.$id")
                    .bodyValue(update)
                    .retrieve()
                    .bodyToMono(Void::class.java)
            }
    }

    // --- Message Methods ---

    fun getMessages(): Mono<List<ChatMessage>> {
        return client.get()
            .uri("/rest/v1/messages?select=*&order=created_at.asc")
            .retrieve()
            .bodyToFlux(ChatMessage::class.java)
            .collectList()
    }

    fun sendMessage(content: String, senderId: String, senderName: String): Mono<Void> {
        val message = mapOf(
            "content" to content,
            "sender_id" to senderId,
            "sender_name" to senderName
        )
        return client.post()
            .uri("/rest/v1/messages")
            .bodyValue(message)
            .retrieve()
            .bodyToMono(Void::class.java)
    }

    // --- Data Classes ---

    data class Validation(
        val id: Long,
        val frase: String,
        @com.fasterxml.jackson.annotation.JsonProperty("frase_traducida")
        val fraseTraducida: String,
        @com.fasterxml.jackson.annotation.JsonProperty("nmr_validacion")
        val nmrValidacion: Long
    )

    data class ChatMessage(
        val id: Long? = null,
        val content: String,
        @com.fasterxml.jackson.annotation.JsonProperty("sender_id")
        val senderId: String,
        @com.fasterxml.jackson.annotation.JsonProperty("sender_name")
        val senderName: String,
        @com.fasterxml.jackson.annotation.JsonProperty("created_at")
        val createdAt: String? = null
    )
}

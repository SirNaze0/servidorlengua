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

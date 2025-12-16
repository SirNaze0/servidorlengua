package com.example.servidorlengua.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

@Service
class GoogleTranslationService(
    @Value("\${google.api.key}") private val apiKey: String,
    @Value("\${google.translate.url}") private val baseUrl: String
) {

    private val client = WebClient.create()

    fun translate(text: String, sourceLang: String = "es", targetLang: String = "qu"): Mono<String> {
        val url = "$baseUrl?key=$apiKey"
        val request = mapOf(
            "q" to text,
            "source" to sourceLang,
            "target" to targetLang,
            "format" to "text"
        )

        return client.post()
            .uri(url)
            .bodyValue(request)
            .retrieve()
            .bodyToMono(TranslationResponse::class.java)
            .map { it.data.translations.firstOrNull()?.translatedText ?: text }
            .doOnError { e ->
                println("ERROR: Google Translation API failed: ${e.message}")
            }
            .onErrorReturn(text)
    }

    data class TranslationResponse(val data: TranslationData)
    data class TranslationData(val translations: List<TranslationItem>)
    data class TranslationItem(val translatedText: String)
}

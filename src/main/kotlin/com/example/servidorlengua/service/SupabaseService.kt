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
            .uri("/rest/v1/validation?id=eq.$id&select=*")
            .retrieve()
            .bodyToFlux(Validation::class.java)
            .next()
            .flatMap { currentValidation ->
                val newCount = (currentValidation.nmrValidacion ?: 0) + 1
                val update = mapOf("nmr_validacion" to newCount)
                client.patch()
                    .uri("/rest/v1/validation?id=eq.$id")
                    .bodyValue(update)
                    .retrieve()
                    .bodyToMono(Void::class.java)
            }
    }

    fun registerVote(validationId: Long, professorId: Long, isCorrect: Boolean): Mono<Void> {
        // 1. Verificar si ya votó
        return client.get()
            .uri { uriBuilder ->
                uriBuilder.path("/rest/v1/profesor-validacion")
                    .queryParam("id_validacion", "eq.$validationId")
                    .queryParam("idProsor", "eq.$professorId")
                    .queryParam("select", "*")
                    .build()
            }
            .retrieve()
            .bodyToFlux(ProfesorValidation::class.java)
            .collectList()
            .flatMap { votes ->
                if (votes.isNotEmpty()) {
                    Mono.error(RuntimeException("Ya has votado esta traducción"))
                } else {
                    // 2. Registrar voto
                    val vote = mapOf(
                        "id_validacion" to validationId,
                        "idProsor" to professorId,
                        "Correcto" to isCorrect
                    )
                    client.post()
                        .uri("/rest/v1/profesor-validacion")
                        .bodyValue(vote)
                        .retrieve()
                        .bodyToMono(Void::class.java)
                        .then(
                            // 3. Incrementar contador
                            incrementValidationCounter(validationId)
                        )
                }
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
        val nmrValidacion: Long?
    )
    fun login(username: String): Mono<com.example.servidorlengua.model.LoginResponse> {
        // 1. Buscar usuario por username en tabla 'usuarios'
        return client.get()
            .uri { uriBuilder ->
                uriBuilder.path("/rest/v1/usuarios")
                    .queryParam("username", "eq.$username")
                    .queryParam("select", "*")
                    .build()
            }
            .retrieve()
            .bodyToFlux(Usuario::class.java)
            .next() // Tomar el primero (debería ser único)
            .flatMap { usuario ->
                // 2. Si es profesor, buscar nombre en 'profesores'
                if (usuario.role == "profesor") {
                    client.get()
                        .uri { uriBuilder ->
                            uriBuilder.path("/rest/v1/profesores")
                                .queryParam("id_user", "eq.${usuario.id}")
                                .queryParam("select", "nombre")
                                .build()
                        }
                        .retrieve()
                        .bodyToFlux(Profesor::class.java)
                        .next()
                        .map { pro ->
                            com.example.servidorlengua.model.LoginResponse(
                                id = usuario.id.toString(),
                                username = usuario.username,
                                name = pro.nombre,
                                role = usuario.role
                            )
                        }
                } else {
                    // 3. Si es estudiante, buscar nombre en 'estudiantes'
                    client.get()
                        .uri { uriBuilder ->
                            uriBuilder.path("/rest/v1/estudiantes")
                                .queryParam("id_user", "eq.${usuario.id}")
                                .queryParam("select", "nombre")
                                .build()
                        }
                        .retrieve()
                        .bodyToFlux(Estudiante::class.java)
                        .next()
                        .map { est ->
                            com.example.servidorlengua.model.LoginResponse(
                                id = usuario.id.toString(),
                                username = usuario.username,
                                name = est.nombre,
                                role = usuario.role
                            )
                        }
                }
            }
    }

    fun registerUser(username: String, name: String, role: String): Mono<com.example.servidorlengua.model.LoginResponse> {
        // 1. Insertar en tabla 'usuarios'
        val newUser = mapOf(
            "username" to username,
            "role" to role
        )
        return client.post()
            .uri("/rest/v1/usuarios")
            .header("Prefer", "return=representation") // Para que devuelva el ID creado
            .bodyValue(newUser)
            .retrieve()
            .bodyToFlux(Usuario::class.java)
            .next()
            .flatMap { usuario ->
                // 2. Insertar en tabla rol correspondiente
                val details = mapOf(
                    "nombre" to name,
                    "id_user" to usuario.id
                )
                val table = if (role == "profesor") "profesores" else "estudiantes"
                
                client.post()
                    .uri("/rest/v1/$table")
                    .bodyValue(details)
                    .retrieve()
                    .toBodilessEntity() // No necesitamos la respuesta del detalle, solo que se guarde
                    .thenReturn(
                        com.example.servidorlengua.model.LoginResponse(
                            id = usuario.id.toString(),
                            username = usuario.username,
                            name = name,
                            role = usuario.role
                        )
                    )
            }
    }

    fun getValidatedTranslation(text: String, source: String, target: String): Mono<String> {
        // Lógica de dirección:
        // Si source="es" -> buscamos en 'frase', devolvemos 'frase_traducida'
        // Si source="qu" -> buscamos en 'frase_traducida', devolvemos 'frase'
        val isSpanishToQuechua = source == "es"
        val columnToSearch = if (isSpanishToQuechua) "frase" else "frase_traducida"
        val columnToReturn = if (isSpanishToQuechua) "frase_traducida" else "frase"

        return client.get()
            .uri { uriBuilder ->
                uriBuilder.path("/rest/v1/validation")
                    .queryParam(columnToSearch, "eq.$text")
                    .queryParam("select", "*")
                    .build()
            }
            .retrieve()
            .bodyToFlux(ValidationEntry::class.java)
            .next()
            .flatMap { validation ->
                // Si encontramos la frase, buscamos las validaciones de profesores
                client.get()
                    .uri { uriBuilder ->
                        uriBuilder.path("/rest/v1/profesor-validacion")
                            .queryParam("id_validacion", "eq.${validation.id}")
                            .queryParam("select", "*")
                            .build()
                    }
                    .retrieve()
                    .bodyToFlux(ProfesorValidation::class.java)
                    .collectList()
                    .flatMap { votes ->
                        if (votes.isEmpty()) Mono.empty()
                        else {
                            val totalVotes = votes.size
                            val correctVotes = votes.count { it.Correcto == true }
                            
                            // Regla: Más de la mitad de profesores que lo validaron
                            if (correctVotes > (totalVotes / 2)) {
                                val result = if (isSpanishToQuechua) validation.frase_traducida else validation.frase
                                if (result != null) Mono.just(result) else Mono.empty()
                            } else {
                                Mono.empty()
                            }
                        }
                    }
            }
    }

    data class Usuario(val id: Long, val username: String, val role: String)
    data class Profesor(val nombre: String)
    data class Estudiante(val nombre: String)
    
    data class ValidationEntry(
        val id: Long,
        val frase: String,
        @com.fasterxml.jackson.annotation.JsonProperty("frase_traducida")
        val frase_traducida: String?,
        val nmr_validacion: Long?
    )

    data class ProfesorValidation(
        @com.fasterxml.jackson.annotation.JsonProperty("IdProfValidacion")
        val id: Long,
        val id_validacion: Long,
        @com.fasterxml.jackson.annotation.JsonProperty("Correcto")
        val Correcto: Boolean?,
        @com.fasterxml.jackson.annotation.JsonProperty("idProsor")
        val idProsor: Long
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

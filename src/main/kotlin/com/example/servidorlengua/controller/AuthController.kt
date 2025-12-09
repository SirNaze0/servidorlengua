package com.example.servidorlengua.controller

import com.example.servidorlengua.model.LoginRequest
import com.example.servidorlengua.model.LoginResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AuthController(
    private val supabaseService: com.example.servidorlengua.service.SupabaseService
) {

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): reactor.core.publisher.Mono<ResponseEntity<Any>> {
        return supabaseService.login(request.username)
            .map { user ->
                ResponseEntity.ok(user as Any)
            }
            .defaultIfEmpty(
                ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Usuario no encontrado"))
            )
    }

    @PostMapping("/register")
    fun register(@RequestBody request: com.example.servidorlengua.model.RegisterRequest): reactor.core.publisher.Mono<ResponseEntity<Any>> {
        return supabaseService.registerUser(request.username, request.name, request.role)
            .map { user ->
                ResponseEntity.ok(user as Any)
            }
            .onErrorResume { e ->
                reactor.core.publisher.Mono.just(
                    ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to "Error al registrar: ${e.message}"))
                )
            }
    }
}

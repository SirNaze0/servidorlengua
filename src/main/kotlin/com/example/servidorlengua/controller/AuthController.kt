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
class AuthController {

    private val users = listOf(
        LoginResponse(id = "1", username = "profesor1", name = "Profesor Juan", role = "profesor"),
        LoginResponse(id = "2", username = "estudiante1", name = "Estudiante María", role = "estudiante"),
        LoginResponse(id = "3", username = "estudiante2", name = "Estudiante Carlos", role = "estudiante")
    )

    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<Any> {
        val user = users.find { it.username == request.username }
        return if (user != null) {
            ResponseEntity.ok(user)
        } else {
            ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to "Usuario no encontrado"))
        }
    }
}

package com.example.servidorlengua.model

data class RegisterRequest(
    val username: String,
    val password: String? = null,
    val name: String,
    val role: String
)

package com.example.servidorlengua.controller

import com.example.servidorlengua.service.SupabaseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api")
class ValidationController(
    private val supabaseService: SupabaseService
) {

    @GetMapping("/validations")
    fun getValidations(): Mono<List<SupabaseService.Validation>> {
        return supabaseService.getValidations()
    }

    @org.springframework.web.bind.annotation.PostMapping("/validations")
    fun createValidation(@org.springframework.web.bind.annotation.RequestBody request: CreateValidationRequest): Mono<Void> {
        return supabaseService.createValidation(request.frase, request.fraseTraducida)
    }

    @org.springframework.web.bind.annotation.PutMapping("/validations/{id}")
    fun updateValidation(
        @org.springframework.web.bind.annotation.PathVariable id: Long,
        @org.springframework.web.bind.annotation.RequestBody request: UpdateValidationRequest
    ): Mono<Void> {
        return supabaseService.updateValidation(id, request.fraseTraducida)
    }

    data class CreateValidationRequest(
        val frase: String,
        val fraseTraducida: String
    )

    data class UpdateValidationRequest(
        val fraseTraducida: String
    )
}

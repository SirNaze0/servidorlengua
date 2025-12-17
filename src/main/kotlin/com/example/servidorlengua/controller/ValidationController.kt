package com.example.servidorlengua.controller

import com.example.servidorlengua.service.SupabaseService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Mono

@RestController
@RequestMapping("/api")
class ValidationController(private val supabaseService: SupabaseService) {

    @GetMapping("/validations")
    fun getValidations(): Mono<List<SupabaseService.Validation>> {
        return supabaseService.getValidations()
    }

    @PostMapping("/validations")
    fun createValidation(@RequestBody request: CreateValidationRequest): Mono<Void> {
        return supabaseService.createValidation(request.frase, request.fraseTraducida)
    }

    @PutMapping("/validations/{id}")
    fun updateValidation(
        @PathVariable id: Long,
        @RequestBody request: UpdateValidationRequest
    ): Mono<Void> {
        return supabaseService.updateValidation(id, request.fraseTraducida)
    }

    @PostMapping("/validations/{id}/vote")
    fun voteValidation(
        @PathVariable id: Long,
        @RequestBody request: VoteValidationRequest
    ): Mono<Void> {
        return supabaseService.registerVote(id, request.professorId, request.isCorrect)
    }

    data class VoteValidationRequest(
        val professorId: Long,
        val isCorrect: Boolean
    )

    data class CreateValidationRequest(
        val frase: String,
        val fraseTraducida: String
    )

    data class UpdateValidationRequest(
        val fraseTraducida: String
    )
}

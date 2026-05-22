package at.hrechny.predictionsbot.foundation

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank

@Introspected
data class FoundationRequest(
    @field:NotBlank
    val name: String,
)

@Introspected
data class FoundationResponse(
    val message: String,
)

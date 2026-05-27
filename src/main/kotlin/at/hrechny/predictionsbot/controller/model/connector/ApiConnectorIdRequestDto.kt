package at.hrechny.predictionsbot.controller.model.connector

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank

@Introspected
class ApiConnectorIdRequestDto {
    @field:NotBlank
    var connectorEntityId: String? = null
}

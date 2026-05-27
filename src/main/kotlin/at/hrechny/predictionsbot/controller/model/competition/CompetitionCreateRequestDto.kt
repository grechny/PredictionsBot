package at.hrechny.predictionsbot.controller.model.competition

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import java.util.UUID

@Introspected
class CompetitionCreateRequestDto {
    @field:Null
    var id: UUID? = null

    @field:NotNull
    var name: String? = null

    @field:NotEmpty
    var connectorIds: MutableMap<String, String> = mutableMapOf()

    var active: Boolean = false

    fun isActive(): Boolean = active
}

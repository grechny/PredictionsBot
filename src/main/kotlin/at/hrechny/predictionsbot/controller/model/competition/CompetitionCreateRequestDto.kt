package at.hrechny.predictionsbot.controller.model.competition

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import java.util.UUID

@Introspected
class CompetitionCreateRequestDto {
    @field:Null
    var id: UUID? = null

    @field:NotNull
    var name: String? = null

    @field:NotNull
    var apiFootballId: Long? = null

    var active: Boolean = false

    fun isActive(): Boolean = active
}

package at.hrechny.predictionsbot.controller.model.competition

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import java.time.Year
import java.util.UUID

@Introspected
class SeasonCreateRequestDto {
    @field:Null
    var id: UUID? = null

    @field:NotNull
    var year: Year? = null

    var active: Boolean = true

    fun isActive(): Boolean = active
}

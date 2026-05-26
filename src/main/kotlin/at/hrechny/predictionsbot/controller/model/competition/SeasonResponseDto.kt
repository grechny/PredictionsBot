package at.hrechny.predictionsbot.controller.model.competition

import io.micronaut.core.annotation.Introspected
import java.time.Year
import java.util.UUID

@Introspected
class SeasonResponseDto {
    var id: UUID? = null
    var year: Year? = null
    var competition: String? = null
    var active: Boolean = true

    fun isActive(): Boolean = active
}

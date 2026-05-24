package at.hrechny.predictionsbot.model

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import java.time.Year
import java.util.UUID

@Introspected
class Season {
    var id: UUID? = null

    @field:NotNull
    var year: Year? = null

    @field:Null
    var competition: String? = null

    var active: Boolean = true

    fun isActive(): Boolean = active
}

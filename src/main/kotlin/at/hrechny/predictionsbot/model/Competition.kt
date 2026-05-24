package at.hrechny.predictionsbot.model

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Null
import java.util.UUID

@Introspected
class Competition {
    @field:Null
    var id: UUID? = null

    @field:NotNull
    var name: String? = null

    @field:NotNull
    var apiFootballId: Long? = null

    @field:NotNull
    var active: Boolean = false

    fun isActive(): Boolean = active
}

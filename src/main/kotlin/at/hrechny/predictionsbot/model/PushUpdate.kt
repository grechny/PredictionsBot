package at.hrechny.predictionsbot.model

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotNull

@Introspected
class PushUpdate {
    @field:NotNull
    var message: String? = null

    var updateCompetitionList: Boolean = false

    fun isUpdateCompetitionList(): Boolean = updateCompetitionList
}

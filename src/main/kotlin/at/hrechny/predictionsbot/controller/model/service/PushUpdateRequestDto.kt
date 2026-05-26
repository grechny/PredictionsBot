package at.hrechny.predictionsbot.controller.model.service

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotNull

@Introspected
class PushUpdateRequestDto {
    @field:NotNull
    var message: String? = null

    var updateCompetitionList: Boolean = false

    fun isUpdateCompetitionList(): Boolean = updateCompetitionList
}

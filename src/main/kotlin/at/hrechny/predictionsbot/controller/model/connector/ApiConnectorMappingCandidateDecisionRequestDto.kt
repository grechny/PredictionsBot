package at.hrechny.predictionsbot.controller.model.connector

import at.hrechny.predictionsbot.database.model.ApiConnectorMappingCandidateStatus
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotNull

@Introspected
class ApiConnectorMappingCandidateDecisionRequestDto {
    @field:NotNull
    var status: ApiConnectorMappingCandidateStatus? = null

    var suggestedValue: String? = null
    var decidedBy: String? = null
}

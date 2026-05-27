package at.hrechny.predictionsbot.controller.model.connector

import at.hrechny.predictionsbot.database.model.ApiConnectorMappingCandidateStatus
import at.hrechny.predictionsbot.database.model.ApiConnectorValueType
import io.micronaut.core.annotation.Introspected
import java.time.Instant
import java.util.UUID

@Introspected
class ApiConnectorMappingCandidateResponseDto {
    var id: UUID? = null
    var connectorCode: String? = null
    var valueType: ApiConnectorValueType? = null
    var rawValue: String? = null
    var contextJson: String? = null
    var suggestedValue: String? = null
    var suggestionConfidence: Int? = null
    var suggestionSource: String? = null
    var status: ApiConnectorMappingCandidateStatus? = null
    var firstSeenAt: Instant? = null
    var lastSeenAt: Instant? = null
    var decidedAt: Instant? = null
    var decidedBy: String? = null
}

package at.hrechny.predictionsbot.database.entity

import at.hrechny.predictionsbot.database.model.ApiConnectorMappingCandidateStatus
import at.hrechny.predictionsbot.database.model.ApiConnectorValueType
import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(
    name = "api_connector_mapping_candidates",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_api_connector_mapping_candidates_value",
            columnNames = ["connector_code", "value_type", "raw_value"],
        ),
    ],
    indexes = [
        Index(name = "idx_api_connector_mapping_candidates_status", columnList = "status, last_seen_at"),
    ],
)
class ApiConnectorMappingCandidateEntity : GeneratedIdEntity() {
    @field:Column(nullable = false, name = "connector_code")
    var connectorCode: String? = null

    @field:Column(nullable = false, name = "value_type")
    @field:Enumerated(EnumType.STRING)
    var valueType: ApiConnectorValueType? = null

    @field:Column(nullable = false, name = "raw_value", length = 512)
    var rawValue: String? = null

    @field:Column(name = "context_json", columnDefinition = "TEXT")
    var contextJson: String? = null

    @field:Column(name = "suggested_value")
    var suggestedValue: String? = null

    @field:Column(name = "suggestion_confidence")
    var suggestionConfidence: Int? = null

    @field:Column(name = "suggestion_source")
    var suggestionSource: String? = null

    @field:Column(nullable = false)
    @field:Enumerated(EnumType.STRING)
    var status: ApiConnectorMappingCandidateStatus? = null

    @field:Column(nullable = false, name = "first_seen_at", columnDefinition = "TIMESTAMP")
    var firstSeenAt: Instant? = null

    @field:Column(nullable = false, name = "last_seen_at", columnDefinition = "TIMESTAMP")
    var lastSeenAt: Instant? = null

    @field:Column(name = "decided_at", columnDefinition = "TIMESTAMP")
    var decidedAt: Instant? = null

    @field:Column(name = "decided_by")
    var decidedBy: String? = null
}

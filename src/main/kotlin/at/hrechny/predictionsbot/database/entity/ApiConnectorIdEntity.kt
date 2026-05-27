package at.hrechny.predictionsbot.database.entity

import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType
import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(
    name = "api_connector_ids",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_api_connector_ids_connector_entity",
            columnNames = ["connector_code", "entity_type", "connector_entity_id"],
        ),
    ],
    indexes = [
        Index(name = "idx_api_connector_ids_connector_internal", columnList = "connector_code, entity_type, internal_id"),
        Index(name = "idx_api_connector_ids_entity_internal", columnList = "entity_type, internal_id"),
    ],
)
class ApiConnectorIdEntity : GeneratedIdEntity() {
    @field:Column(nullable = false, name = "connector_code")
    var connectorCode: String? = null

    @field:Column(nullable = false)
    @field:Enumerated(EnumType.STRING)
    var entityType: ApiConnectorEntityType? = null

    @field:Column(nullable = false, name = "connector_entity_id")
    var connectorEntityId: String? = null

    @field:Column(nullable = false)
    var internalId: UUID? = null

    @field:Column(nullable = false, columnDefinition = "TIMESTAMP")
    var createdAt: Instant? = null

    @field:Column(nullable = false, columnDefinition = "TIMESTAMP")
    var updatedAt: Instant? = null
}

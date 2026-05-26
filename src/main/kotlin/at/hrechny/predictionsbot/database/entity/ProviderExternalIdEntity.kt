package at.hrechny.predictionsbot.database.entity

import at.hrechny.predictionsbot.database.model.ProviderExternalIdEntityType
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
    name = "provider_external_ids",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_provider_external_ids_provider_entity_external_scope",
            columnNames = ["provider_code", "entity_type", "external_id", "scope_key"],
        ),
    ],
    indexes = [
        Index(name = "idx_provider_external_ids_entity_internal", columnList = "entity_type, internal_id"),
    ],
)
class ProviderExternalIdEntity : GeneratedIdEntity() {
    @field:Column(nullable = false)
    var providerCode: String? = null

    @field:Column(nullable = false)
    @field:Enumerated(EnumType.STRING)
    var entityType: ProviderExternalIdEntityType? = null

    @field:Column(nullable = false)
    var externalId: String? = null

    @field:Column(nullable = false)
    var scopeKey: String? = null

    @field:Column(nullable = false)
    var internalId: UUID? = null

    @field:Column(nullable = false, columnDefinition = "TIMESTAMP")
    var createdAt: Instant? = null

    @field:Column(nullable = false, columnDefinition = "TIMESTAMP")
    var updatedAt: Instant? = null
}

package at.hrechny.predictionsbot.database.entity

import at.hrechny.predictionsbot.database.model.ApiConnectorCode
import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(
    name = "audit",
    indexes = [Index(name = "idx_api_key_provider", columnList = "api_key, api_provider", unique = true)],
)
class AuditEntity : GeneratedIdEntity() {
    @field:Column
    var apiKey: String? = null

    @field:Column(name = "api_provider")
    @field:Enumerated(EnumType.STRING)
    var apiConnectorCode: ApiConnectorCode? = null

    @field:Column
    var requestUri: String? = null

    @field:Column(columnDefinition = "TIMESTAMP")
    var requestDate: Instant? = null

    @field:Column
    var success: Boolean = false

    fun isSuccess(): Boolean = success
}

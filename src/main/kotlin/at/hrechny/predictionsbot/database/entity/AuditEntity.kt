package at.hrechny.predictionsbot.database.entity

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Introspected(accessKind = [Introspected.AccessKind.FIELD])
@Entity
@Table(
    name = "audit",
    indexes = [
        Index(name = "idx_audit_connector_name_request_date", columnList = "connector_name, request_date"),
    ],
)
class AuditEntity : GeneratedIdEntity() {
    @field:Column(name = "connector_name")
    var connectorName: String? = null

    @field:Column(length = 2048)
    var requestUri: String? = null

    @field:Column(columnDefinition = "TIMESTAMP")
    var requestDate: Instant? = null

    @field:Column
    var success: Boolean = false

    @field:Column(columnDefinition = "TEXT")
    var failureReason: String? = null

    fun isSuccess(): Boolean = success
}

package at.hrechny.predictionsbot.connector

import at.hrechny.predictionsbot.database.entity.AuditEntity
import at.hrechny.predictionsbot.database.repository.AuditRepository
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant

@Singleton
class ApiConnectorRequestAuditService(
    private val auditRepository: AuditRepository,
    private val clock: Clock,
) {
    fun recordRequest(
        connectorCode: String,
        requestUri: String,
        success: Boolean,
        failureReason: String?,
        quotaSnapshot: String?,
    ): AuditEntity = auditRepository.save(
        AuditEntity().apply {
            this.connectorCode = connectorCode
            this.requestUri = requestUri
            this.requestDate = Instant.now(clock)
            this.success = success
            this.failureReason = failureReason
            this.quotaSnapshot = quotaSnapshot
        },
    )

    fun countRequestsSince(connectorCode: String, since: Instant): Int =
        auditRepository.countAllByConnectorCodeAndRequestDateAfter(connectorCode, since)
}

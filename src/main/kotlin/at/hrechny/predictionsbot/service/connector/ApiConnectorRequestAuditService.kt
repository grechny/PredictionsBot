package at.hrechny.predictionsbot.service.connector

import at.hrechny.predictionsbot.database.entity.AuditEntity
import at.hrechny.predictionsbot.database.repository.AuditRepository
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant

@Singleton
open class ApiConnectorRequestAuditService(
    private val auditRepository: AuditRepository,
    private val clock: Clock,
) {
    @Transactional(propagation = TransactionDefinition.Propagation.REQUIRES_NEW)
    open fun recordRequest(
        connectorName: String,
        requestUri: String,
        success: Boolean,
        failureReason: String?,
    ): AuditEntity = auditRepository.save(
        AuditEntity().apply {
            this.connectorName = connectorName
            this.requestUri = requestUri
            this.requestDate = Instant.now(clock)
            this.success = success
            this.failureReason = failureReason
        },
    )

    fun countRequestsSince(connectorName: String, since: Instant): Int =
        auditRepository.countAllByConnectorNameAndRequestDateAfter(connectorName, since)
}

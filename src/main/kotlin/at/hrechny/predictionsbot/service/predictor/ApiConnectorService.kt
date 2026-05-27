package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.database.entity.ApiConnectorIdEntity
import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType
import at.hrechny.predictionsbot.database.repository.ApiConnectorIdRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
@Transactional
open class ApiConnectorService(
    private val apiConnectorIdRepository: ApiConnectorIdRepository,
) {
    open fun upsertId(
        connectorCode: String,
        entityType: ApiConnectorEntityType,
        connectorEntityId: String,
        internalId: UUID,
    ): ApiConnectorIdEntity {
        val now = Instant.now()
        val entity = apiConnectorIdRepository
            .findByConnectorCodeAndEntityTypeAndConnectorEntityId(
                connectorCode,
                entityType,
                connectorEntityId,
            )
            .orElseGet {
                ApiConnectorIdEntity().apply {
                    this.connectorCode = connectorCode
                    this.entityType = entityType
                    this.connectorEntityId = connectorEntityId
                    this.createdAt = now
                }
            }
        entity.internalId = internalId
        entity.updatedAt = now
        return apiConnectorIdRepository.save(entity)
    }
}

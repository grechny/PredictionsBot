package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.database.entity.ApiConnectorIdEntity
import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType
import at.hrechny.predictionsbot.database.repository.ApiConnectorIdRepository
import at.hrechny.predictionsbot.exception.NotFoundException
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.Optional
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

    open fun findInternalId(
        connectorCode: String,
        entityType: ApiConnectorEntityType,
        connectorEntityId: String,
    ): Optional<UUID> =
        apiConnectorIdRepository
            .findByConnectorCodeAndEntityTypeAndConnectorEntityId(connectorCode, entityType, connectorEntityId)
            .map(ApiConnectorIdEntity::internalId)

    open fun requireInternalId(
        connectorCode: String,
        entityType: ApiConnectorEntityType,
        connectorEntityId: String,
    ): UUID =
        findInternalId(connectorCode, entityType, connectorEntityId).orElseThrow {
            NotFoundException("No internal $entityType found for connector $connectorCode id $connectorEntityId")
        }

    open fun findConnectorEntityId(
        connectorCode: String,
        entityType: ApiConnectorEntityType,
        internalId: UUID,
    ): Optional<String> =
        apiConnectorIdRepository
            .findByConnectorCodeAndEntityTypeAndInternalId(connectorCode, entityType, internalId)
            .map(ApiConnectorIdEntity::connectorEntityId)

    open fun requireConnectorEntityId(
        connectorCode: String,
        entityType: ApiConnectorEntityType,
        internalId: UUID,
    ): String =
        findConnectorEntityId(connectorCode, entityType, internalId).orElseThrow {
            NotFoundException("No connector $connectorCode id found for internal $entityType $internalId")
        }

    open fun findConnectorIds(internalId: UUID, entityType: ApiConnectorEntityType): Map<String, String> =
        apiConnectorIdRepository.findAllByInternalIdAndEntityType(internalId, entityType)
            .associate { entity -> entity.connectorCode!! to entity.connectorEntityId!! }
}

package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.ApiConnectorIdEntity
import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.Optional
import java.util.UUID

@Singleton
@Transactional
class ApiConnectorIdRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: ApiConnectorIdEntity): ApiConnectorIdEntity = entityManager.merge(entity)

    fun findByConnectorCodeAndEntityTypeAndConnectorEntityId(
        connectorCode: String,
        entityType: ApiConnectorEntityType,
        connectorEntityId: String,
    ): Optional<ApiConnectorIdEntity> = entityManager
        .createQuery(
            """
            select p
            from ApiConnectorIdEntity p
            where p.connectorCode = :connectorCode
              and p.entityType = :entityType
              and p.connectorEntityId = :connectorEntityId
            """.trimIndent(),
            ApiConnectorIdEntity::class.java,
        )
        .setParameter("connectorCode", connectorCode)
        .setParameter("entityType", entityType)
        .setParameter("connectorEntityId", connectorEntityId)
        .setMaxResults(1)
        .resultStream
        .findFirst()

    fun findAllByInternalIdAndEntityType(
        internalId: UUID,
        entityType: ApiConnectorEntityType,
    ): List<ApiConnectorIdEntity> = entityManager
        .createQuery(
            """
            select p
            from ApiConnectorIdEntity p
            where p.internalId = :internalId
              and p.entityType = :entityType
            """.trimIndent(),
            ApiConnectorIdEntity::class.java,
        )
        .setParameter("internalId", internalId)
        .setParameter("entityType", entityType)
        .resultList
}

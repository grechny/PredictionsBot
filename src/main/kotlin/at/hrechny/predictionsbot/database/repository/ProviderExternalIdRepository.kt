package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.ProviderExternalIdEntity
import at.hrechny.predictionsbot.database.model.ProviderExternalIdEntityType
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import java.util.Optional
import java.util.UUID

@Singleton
@Transactional
class ProviderExternalIdRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: ProviderExternalIdEntity): ProviderExternalIdEntity = entityManager.merge(entity)

    fun findByProviderCodeAndEntityTypeAndExternalIdAndScopeKey(
        providerCode: String,
        entityType: ProviderExternalIdEntityType,
        externalId: String,
        scopeKey: String,
    ): Optional<ProviderExternalIdEntity> = entityManager
        .createQuery(
            """
            select p
            from ProviderExternalIdEntity p
            where p.providerCode = :providerCode
              and p.entityType = :entityType
              and p.externalId = :externalId
              and p.scopeKey = :scopeKey
            """.trimIndent(),
            ProviderExternalIdEntity::class.java,
        )
        .setParameter("providerCode", providerCode)
        .setParameter("entityType", entityType)
        .setParameter("externalId", externalId)
        .setParameter("scopeKey", scopeKey)
        .setMaxResults(1)
        .resultStream
        .findFirst()

    fun findAllByInternalIdAndEntityType(
        internalId: UUID,
        entityType: ProviderExternalIdEntityType,
    ): List<ProviderExternalIdEntity> = entityManager
        .createQuery(
            """
            select p
            from ProviderExternalIdEntity p
            where p.internalId = :internalId
              and p.entityType = :entityType
            """.trimIndent(),
            ProviderExternalIdEntity::class.java,
        )
        .setParameter("internalId", internalId)
        .setParameter("entityType", entityType)
        .resultList
}

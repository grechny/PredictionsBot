package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.database.entity.ProviderExternalIdEntity
import at.hrechny.predictionsbot.database.model.ProviderExternalIdEntityType
import at.hrechny.predictionsbot.database.repository.ProviderExternalIdRepository
import io.micronaut.transaction.annotation.Transactional
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
@Transactional
open class ProviderExternalIdService(
    private val providerExternalIdRepository: ProviderExternalIdRepository,
) {
    open fun scopeGlobal(): String = GLOBAL_SCOPE

    open fun scopeCompetition(competitionId: UUID): String = "competition:$competitionId"

    open fun scopeSeason(seasonId: UUID): String = "season:$seasonId"

    open fun upsertMapping(
        providerCode: String,
        entityType: ProviderExternalIdEntityType,
        externalId: String,
        scopeKey: String,
        internalId: UUID,
    ): ProviderExternalIdEntity {
        val now = Instant.now()
        val entity = providerExternalIdRepository
            .findByProviderCodeAndEntityTypeAndExternalIdAndScopeKey(providerCode, entityType, externalId, scopeKey)
            .orElseGet {
                ProviderExternalIdEntity().apply {
                    this.providerCode = providerCode
                    this.entityType = entityType
                    this.externalId = externalId
                    this.scopeKey = scopeKey
                    this.createdAt = now
                }
            }
        entity.internalId = internalId
        entity.updatedAt = now
        return providerExternalIdRepository.save(entity)
    }

    private companion object {
        const val GLOBAL_SCOPE = "global"
    }
}

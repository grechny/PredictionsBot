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
    open fun scopeGlobal(): String = GLOBAL_SCOPE

    open fun scopeCompetition(competitionId: UUID): String = "competition:$competitionId"

    open fun scopeSeason(seasonId: UUID): String = "season:$seasonId"

    open fun upsertId(
        connectorCode: String,
        entityType: ApiConnectorEntityType,
        connectorEntityId: String,
        scopeKey: String,
        internalId: UUID,
    ): ApiConnectorIdEntity {
        val now = Instant.now()
        val entity = apiConnectorIdRepository
            .findByConnectorCodeAndEntityTypeAndConnectorEntityIdAndScopeKey(
                connectorCode,
                entityType,
                connectorEntityId,
                scopeKey,
            )
            .orElseGet {
                ApiConnectorIdEntity().apply {
                    this.connectorCode = connectorCode
                    this.entityType = entityType
                    this.connectorEntityId = connectorEntityId
                    this.scopeKey = scopeKey
                    this.createdAt = now
                }
            }
        entity.internalId = internalId
        entity.updatedAt = now
        return apiConnectorIdRepository.save(entity)
    }

    private companion object {
        const val GLOBAL_SCOPE = "global"
    }
}

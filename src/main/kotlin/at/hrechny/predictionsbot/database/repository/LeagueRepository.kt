package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.LeagueEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.util.Optional
import java.util.UUID

@Singleton
class LeagueRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: LeagueEntity): LeagueEntity = entityManager.merge(entity)

    fun findById(id: UUID): Optional<LeagueEntity> = Optional.ofNullable(entityManager.find(LeagueEntity::class.java, id))
}

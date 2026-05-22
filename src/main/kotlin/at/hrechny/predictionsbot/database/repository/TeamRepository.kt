package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.TeamEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import io.micronaut.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Singleton
@Transactional
class TeamRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: TeamEntity): TeamEntity = entityManager.merge(entity)

    fun findById(id: UUID): Optional<TeamEntity> = Optional.ofNullable(entityManager.find(TeamEntity::class.java, id))

    fun findFirstByApiFootballId(apiFootballId: Long): Optional<TeamEntity> = entityManager
        .createQuery("select t from TeamEntity t where t.apiFootballId = :apiFootballId", TeamEntity::class.java)
        .setParameter("apiFootballId", apiFootballId)
        .setMaxResults(1)
        .resultStream
        .findFirst()
}

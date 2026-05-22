package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.CompetitionEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.util.Optional
import java.util.UUID

@Singleton
class CompetitionRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: CompetitionEntity): CompetitionEntity = entityManager.merge(entity)

    fun findById(id: UUID): Optional<CompetitionEntity> =
        Optional.ofNullable(entityManager.find(CompetitionEntity::class.java, id))

    fun findAll(): List<CompetitionEntity> =
        entityManager.createQuery("select c from CompetitionEntity c", CompetitionEntity::class.java).resultList
}

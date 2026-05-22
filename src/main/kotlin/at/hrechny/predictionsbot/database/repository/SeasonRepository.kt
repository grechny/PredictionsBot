package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.SeasonEntity
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import io.micronaut.transaction.annotation.Transactional
import java.util.Optional
import java.util.UUID

@Singleton
@Transactional
class SeasonRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: SeasonEntity): SeasonEntity = entityManager.merge(entity)

    fun findById(id: UUID): Optional<SeasonEntity> = Optional.ofNullable(entityManager.find(SeasonEntity::class.java, id))

    fun findAllByActiveIsTrue(): List<SeasonEntity> =
        entityManager.createQuery("select s from SeasonEntity s where s.active = true", SeasonEntity::class.java).resultList

    fun findAllByCompetitionId(competitionId: UUID): List<SeasonEntity> = entityManager
        .createQuery("select s from SeasonEntity s where s.competition.id = :competitionId", SeasonEntity::class.java)
        .setParameter("competitionId", competitionId)
        .resultList

    fun findFirstByCompetitionIdAndActiveIsTrue(competitionId: UUID): Optional<SeasonEntity> = entityManager
        .createQuery(
            "select s from SeasonEntity s where s.competition.id = :competitionId and s.active = true",
            SeasonEntity::class.java,
        )
        .setParameter("competitionId", competitionId)
        .setMaxResults(1)
        .resultStream
        .findFirst()

    fun countAllByActiveIsTrueAndCompetitionId(competitionId: UUID): Int = entityManager
        .createQuery(
            "select count(s) from SeasonEntity s where s.active = true and s.competition.id = :competitionId",
            Long::class.javaObjectType,
        )
        .setParameter("competitionId", competitionId)
        .singleResult
        .toInt()
}

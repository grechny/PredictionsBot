package at.hrechny.predictionsbot.database.repository

import at.hrechny.predictionsbot.database.entity.MatchEntity
import at.hrechny.predictionsbot.database.entity.SeasonEntity
import at.hrechny.predictionsbot.database.model.MatchStatus
import jakarta.inject.Singleton
import jakarta.persistence.EntityManager
import jakarta.transaction.Transactional
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Singleton
class MatchRepository(
    private val entityManager: EntityManager,
) {
    @Transactional
    fun save(entity: MatchEntity): MatchEntity = entityManager.merge(entity)

    fun findById(id: UUID): Optional<MatchEntity> = Optional.ofNullable(entityManager.find(MatchEntity::class.java, id))

    fun findFirstByRoundSeasonAndStartTimeAfterOrderByStartTimeAsc(
        seasonEntity: SeasonEntity,
        instant: Instant,
    ): Optional<MatchEntity> = entityManager
        .createQuery(
            """
            select m
            from MatchEntity m
            where m.round.season = :seasonEntity
              and m.startTime > :instant
            order by m.startTime asc
            """.trimIndent(),
            MatchEntity::class.java,
        )
        .setParameter("seasonEntity", seasonEntity)
        .setParameter("instant", instant)
        .setMaxResults(1)
        .resultStream
        .findFirst()

    fun findAllByStartTimeAfterAndStartTimeBeforeOrderByStartTimeAsc(
        from: Instant,
        until: Instant,
    ): List<MatchEntity> = entityManager
        .createQuery(
            """
            select m
            from MatchEntity m
            where m.startTime > :from
              and m.startTime < :until
            order by m.startTime asc
            """.trimIndent(),
            MatchEntity::class.java,
        )
        .setParameter("from", from)
        .setParameter("until", until)
        .resultList

    fun findAllByRoundSeasonAndStatusInAndStartTimeBefore(
        seasonEntity: SeasonEntity,
        statuses: List<MatchStatus>,
        time: Instant,
    ): List<MatchEntity> = entityManager
        .createQuery(
            """
            select m
            from MatchEntity m
            where m.round.season = :seasonEntity
              and m.status in :statuses
              and m.startTime < :time
            """.trimIndent(),
            MatchEntity::class.java,
        )
        .setParameter("seasonEntity", seasonEntity)
        .setParameter("statuses", statuses)
        .setParameter("time", time)
        .resultList

    fun findAllActive(seasonEntity: SeasonEntity): List<MatchEntity> =
        findAllByRoundSeasonAndStatusInAndStartTimeBefore(
            seasonEntity,
            listOf(MatchStatus.PLANNED, MatchStatus.STARTED),
            Instant.now(),
        )

    fun findUpcoming(seasonEntity: SeasonEntity): Optional<MatchEntity> =
        findFirstByRoundSeasonAndStartTimeAfterOrderByStartTimeAsc(seasonEntity, Instant.now())
}

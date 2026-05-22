package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class MatchRepository {

  private final EntityManager entityManager;

  public MatchRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  public MatchEntity save(MatchEntity entity) {
    return entityManager.merge(entity);
  }

  public Optional<MatchEntity> findById(UUID id) {
    return Optional.ofNullable(entityManager.find(MatchEntity.class, id));
  }

  public Optional<MatchEntity> findFirstByRoundSeasonAndStartTimeAfterOrderByStartTimeAsc(SeasonEntity seasonEntity, Instant instant) {
    return entityManager
        .createQuery("""
            select m
            from MatchEntity m
            where m.round.season = :seasonEntity
              and m.startTime > :instant
            order by m.startTime asc
            """, MatchEntity.class)
        .setParameter("seasonEntity", seasonEntity)
        .setParameter("instant", instant)
        .setMaxResults(1)
        .getResultStream()
        .findFirst();
  }

  public List<MatchEntity> findAllByStartTimeAfterAndStartTimeBeforeOrderByStartTimeAsc(Instant from, Instant until) {
    return entityManager
        .createQuery("""
            select m
            from MatchEntity m
            where m.startTime > :from
              and m.startTime < :until
            order by m.startTime asc
            """, MatchEntity.class)
        .setParameter("from", from)
        .setParameter("until", until)
        .getResultList();
  }

  public List<MatchEntity> findAllByRoundSeasonAndStatusInAndStartTimeBefore(
      SeasonEntity seasonEntity,
      List<MatchStatus> statuses,
      Instant time) {

    return entityManager
        .createQuery("""
            select m
            from MatchEntity m
            where m.round.season = :seasonEntity
              and m.status in :statuses
              and m.startTime < :time
            """, MatchEntity.class)
        .setParameter("seasonEntity", seasonEntity)
        .setParameter("statuses", statuses)
        .setParameter("time", time)
        .getResultList();
  }

  public List<MatchEntity> findAllActive(SeasonEntity seasonEntity) {
    return findAllByRoundSeasonAndStatusInAndStartTimeBefore(seasonEntity, Arrays.asList(MatchStatus.PLANNED, MatchStatus.STARTED), Instant.now());
  }

  public Optional<MatchEntity> findUpcoming(SeasonEntity seasonEntity) {
    return findFirstByRoundSeasonAndStartTimeAfterOrderByStartTimeAsc(seasonEntity, Instant.now());
  }
}

package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class SeasonRepository {

  private final EntityManager entityManager;

  public SeasonRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  public SeasonEntity save(SeasonEntity entity) {
    return entityManager.merge(entity);
  }

  public Optional<SeasonEntity> findById(UUID id) {
    return Optional.ofNullable(entityManager.find(SeasonEntity.class, id));
  }

  public List<SeasonEntity> findAllByActiveIsTrue() {
    return entityManager.createQuery("select s from SeasonEntity s where s.active = true", SeasonEntity.class).getResultList();
  }

  public List<SeasonEntity> findAllByCompetitionId(UUID competitionId) {
    return entityManager
        .createQuery("select s from SeasonEntity s where s.competition.id = :competitionId", SeasonEntity.class)
        .setParameter("competitionId", competitionId)
        .getResultList();
  }

  public Optional<SeasonEntity> findFirstByCompetitionIdAndActiveIsTrue(UUID competitionId) {
    return entityManager
        .createQuery("select s from SeasonEntity s where s.competition.id = :competitionId and s.active = true", SeasonEntity.class)
        .setParameter("competitionId", competitionId)
        .setMaxResults(1)
        .getResultStream()
        .findFirst();
  }

  public int countAllByActiveIsTrueAndCompetitionId(UUID competitionId) {
    return entityManager
        .createQuery("select count(s) from SeasonEntity s where s.active = true and s.competition.id = :competitionId", Long.class)
        .setParameter("competitionId", competitionId)
        .getSingleResult()
        .intValue();
  }
}

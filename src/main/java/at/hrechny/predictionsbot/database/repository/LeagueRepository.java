package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.LeagueEntity;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class LeagueRepository {

  private final EntityManager entityManager;

  public LeagueRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  public LeagueEntity save(LeagueEntity entity) {
    return entityManager.merge(entity);
  }

  public Optional<LeagueEntity> findById(UUID id) {
    return Optional.ofNullable(entityManager.find(LeagueEntity.class, id));
  }
}

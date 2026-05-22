package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.TeamEntity;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class TeamRepository {

  private final EntityManager entityManager;

  public TeamRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  public TeamEntity save(TeamEntity entity) {
    return entityManager.merge(entity);
  }

  public Optional<TeamEntity> findById(UUID id) {
    return Optional.ofNullable(entityManager.find(TeamEntity.class, id));
  }

  public Optional<TeamEntity> findFirstByApiFootballId(Long apiFootballId) {
    return entityManager
        .createQuery("select t from TeamEntity t where t.apiFootballId = :apiFootballId", TeamEntity.class)
        .setParameter("apiFootballId", apiFootballId)
        .setMaxResults(1)
        .getResultStream()
        .findFirst();
  }
}

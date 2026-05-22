package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class CompetitionRepository {

  private final EntityManager entityManager;

  public CompetitionRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  public CompetitionEntity save(CompetitionEntity entity) {
    return entityManager.merge(entity);
  }

  public Optional<CompetitionEntity> findById(UUID id) {
    return Optional.ofNullable(entityManager.find(CompetitionEntity.class, id));
  }

  public List<CompetitionEntity> findAll() {
    return entityManager.createQuery("select c from CompetitionEntity c", CompetitionEntity.class).getResultList();
  }
}

package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.UserEntity;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class UserRepository {

  private final EntityManager entityManager;

  public UserRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  public UserEntity save(UserEntity entity) {
    return entityManager.merge(entity);
  }

  public Optional<UserEntity> findById(Long id) {
    return Optional.ofNullable(entityManager.find(UserEntity.class, id));
  }

  public Optional<UserEntity> findByIdAndActiveIsTrue(Long id) {
    return entityManager
        .createQuery("select u from UserEntity u where u.id = :id and u.active = true", UserEntity.class)
        .setParameter("id", id)
        .getResultStream()
        .findFirst();
  }

  public List<UserEntity> findAllByActiveIsTrue() {
    return entityManager.createQuery("select u from UserEntity u where u.active = true", UserEntity.class).getResultList();
  }

  public List<UserEntity> findAllActiveByCompetitionsId(UUID competitionId) {
    return entityManager
        .createQuery("select u from UserEntity u join u.competitions c where u.active = true and c.id = :competitionId", UserEntity.class)
        .setParameter("competitionId", competitionId)
        .getResultList();
  }
}

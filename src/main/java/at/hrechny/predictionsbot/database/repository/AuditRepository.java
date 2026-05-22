package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.AuditEntity;
import at.hrechny.predictionsbot.database.model.ApiProvider;
import jakarta.inject.Singleton;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class AuditRepository {

  private final EntityManager entityManager;

  public AuditRepository(EntityManager entityManager) {
    this.entityManager = entityManager;
  }

  @Transactional
  public AuditEntity save(AuditEntity entity) {
    return entityManager.merge(entity);
  }

  public Optional<AuditEntity> findById(UUID id) {
    return Optional.ofNullable(entityManager.find(AuditEntity.class, id));
  }

  public int countAllByApiProviderAndApiKeyAndRequestDateAfter(ApiProvider apiProvider, String apiKey, Instant date) {
    return entityManager
        .createQuery("""
            select count(a)
            from AuditEntity a
            where a.apiProvider = :apiProvider
              and a.apiKey = :apiKey
              and a.requestDate > :date
            """, Long.class)
        .setParameter("apiProvider", apiProvider)
        .setParameter("apiKey", apiKey)
        .setParameter("date", date)
        .getSingleResult()
        .intValue();
  }
}

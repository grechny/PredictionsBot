package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.AuditEntity;
import at.hrechny.predictionsbot.database.model.ApiProvider;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditRepository extends CrudRepository<AuditEntity, UUID> {

  Optional<AuditEntity> getFirstByApiProviderAndApiKeyOrderByRequestDateDesc(ApiProvider apiProvider, String apiKey);

  int countAllByApiProviderAndApiKeyAndRequestDateAfter(ApiProvider apiProvider, String apiKey, Instant date);

}

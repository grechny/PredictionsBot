package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.TeamEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface TeamRepository extends CrudRepository<TeamEntity, UUID> {

  Optional<TeamEntity> findFirstByApiFootballId(Long apiFootballId);

}

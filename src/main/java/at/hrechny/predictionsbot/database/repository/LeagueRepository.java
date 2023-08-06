package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.LeagueEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LeagueRepository extends CrudRepository<LeagueEntity, UUID> {

  Optional<LeagueEntity> findByName(String name);

}

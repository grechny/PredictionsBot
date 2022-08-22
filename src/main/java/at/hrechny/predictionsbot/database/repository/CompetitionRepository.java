package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompetitionRepository extends CrudRepository<CompetitionEntity, UUID> {

}

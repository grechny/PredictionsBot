package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeasonRepository extends CrudRepository<SeasonEntity, UUID> {

  Iterable<SeasonEntity> findAllByActiveIsTrue();

  Iterable<SeasonEntity> findAllByCompetition_Id(UUID competitionId);

  Optional<SeasonEntity> findFirstByCompetition_IdAndActiveIsTrue(UUID competitionId);

  int countAllByActiveIsTrueAndCompetition_Id(UUID competitionId);

}

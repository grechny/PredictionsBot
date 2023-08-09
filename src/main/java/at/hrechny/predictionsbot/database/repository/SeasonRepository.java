package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeasonRepository extends ListCrudRepository<SeasonEntity, UUID> {

  List<SeasonEntity> findAllByActiveIsTrue();

  List<SeasonEntity> findAllByCompetitionId(UUID competitionId);

  Optional<SeasonEntity> findFirstByCompetitionIdAndActiveIsTrue(UUID competitionId);

  int countAllByActiveIsTrueAndCompetitionId(UUID competitionId);

}

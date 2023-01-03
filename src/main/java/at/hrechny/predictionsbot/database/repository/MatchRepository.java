package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MatchRepository extends CrudRepository<MatchEntity, UUID> {

  Optional<MatchEntity> findFirstByRoundSeasonAndStartTimeAfterOrderByStartTimeAsc(SeasonEntity seasonEntity, Instant instant);

  List<MatchEntity> findAllByStartTimeAfterAndStartTimeBeforeOrderByStartTimeAsc(Instant from, Instant until);

  List<MatchEntity> findAllByRoundSeasonAndStatusInAndStartTimeBefore(SeasonEntity seasonEntity, List<MatchStatus> statuses, Instant time);

  default List<MatchEntity> findAllActive(SeasonEntity seasonEntity) {
    return findAllByRoundSeasonAndStatusInAndStartTimeBefore(seasonEntity, Arrays.asList(MatchStatus.PLANNED, MatchStatus.STARTED), Instant.now());
  }

  default Optional<MatchEntity> findUpcoming(SeasonEntity seasonEntity) {
    return findFirstByRoundSeasonAndStartTimeAfterOrderByStartTimeAsc(seasonEntity, Instant.now());
  }

}

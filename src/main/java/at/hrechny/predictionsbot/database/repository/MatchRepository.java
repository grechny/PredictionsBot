package at.hrechny.predictionsbot.database.repository;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
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

  Optional<MatchEntity> findFirstByApiFootballId(Long apiFootballId);

  List<MatchEntity> findAllByStatusInAndStartTimeBefore(List<MatchStatus> statuses, Instant time);

  List<MatchEntity> findAllByStatusInAndStartTimeAfterOrderByStartTimeAsc(List<MatchStatus> statuses, Instant time);

  List<MatchEntity> findAllBySeason_IdAndRoundOrderByStartTimeAsc(UUID seasonId, Integer round);

  default List<MatchEntity> findAllActive() {
    return findAllByStatusInAndStartTimeBefore(Arrays.asList(MatchStatus.PLANNED, MatchStatus.STARTED), Instant.now());
  }

  default List<MatchEntity> findUpcoming() {
    return findAllByStatusInAndStartTimeAfterOrderByStartTimeAsc(List.of(MatchStatus.PLANNED), Instant.now());
  }

}

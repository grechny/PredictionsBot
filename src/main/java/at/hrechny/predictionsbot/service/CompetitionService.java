package at.hrechny.predictionsbot.service;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.model.Season;
import java.util.List;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;

public interface CompetitionService {

  UUID addCompetition(Competition competition);

  List<Competition> getCompetitions();

  UUID addSeason(UUID competitionId, Season season);

  void updateSeason(UUID competitionId, Season season);

  List<Season> getSeasons(UUID competitionId);

  List<MatchEntity> getFixtures(UUID seasonId, Integer round);

  List<MatchEntity> getUpcomingFixtures();

  @Scheduled
  void refreshFixtures();

  void refreshActiveFixtures();

}

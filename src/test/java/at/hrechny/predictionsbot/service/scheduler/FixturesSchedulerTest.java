package at.hrechny.predictionsbot.service.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixturesSchedulerTest {

  @Mock
  private CompetitionService competitionService;

  private FixturesScheduler fixturesScheduler;

  @BeforeEach
  void setUp() {
    fixturesScheduler = new FixturesScheduler(competitionService);
  }

  @Test
  void refreshFixturesRefreshesEveryActiveSeason() {
    var firstSeason = season();
    var secondSeason = season();
    when(competitionService.getActiveSeasons()).thenReturn(List.of(firstSeason, secondSeason));

    fixturesScheduler.refreshFixtures();

    verify(competitionService).refreshFixtures(firstSeason);
    verify(competitionService).refreshFixtures(secondSeason);
  }

  private SeasonEntity season() {
    var season = new SeasonEntity();
    season.setId(UUID.randomUUID());
    return season;
  }
}

package at.hrechny.predictionsbot.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.exception.FixturesSynchronizationException;
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
    fixturesScheduler = new FixturesScheduler(competitionService, true);
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

  @Test
  void refreshFixturesDoesNothingWhenSchedulerIsDisabled() {
    fixturesScheduler = new FixturesScheduler(competitionService, false);

    fixturesScheduler.refreshFixtures();

    verifyNoInteractions(competitionService);
  }

  @Test
  void refreshFixturesThrowsAggregatedFailureAfterRefreshingOtherSeasons() {
    var failedSeason = season("Premier League", "2025");
    var successfulSeason = season("Champions League", "2025");
    var providerFailure = new RuntimeException("REQUEST_ERROR: HTTP 429; headers={x-ratelimit-requests-remaining=98}");
    when(competitionService.getActiveSeasons()).thenReturn(List.of(failedSeason, successfulSeason));
    doThrow(providerFailure).when(competitionService).refreshFixtures(failedSeason);

    var thrown = catchThrowable(() -> fixturesScheduler.refreshFixtures());

    verify(competitionService).refreshFixtures(failedSeason);
    verify(competitionService).refreshFixtures(successfulSeason);
    assertThat(thrown).isInstanceOf(FixturesSynchronizationException.class);
    assertThat(thrown.getMessage()).contains("Premier League 2025");
    assertThat(thrown.getSuppressed()).containsExactly(providerFailure);
  }

  private SeasonEntity season() {
    return season(null, null);
  }

  private SeasonEntity season(String competitionName, String year) {
    var season = new SeasonEntity();
    season.setId(UUID.randomUUID());
    season.setYear(year);
    if (competitionName != null) {
      var competition = new CompetitionEntity();
      competition.setName(competitionName);
      season.setCompetition(competition);
    }
    return season;
  }
}

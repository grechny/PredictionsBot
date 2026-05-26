package at.hrechny.predictionsbot.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.exception.FixturesSynchronizationException;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import java.util.List;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FixturesSchedulerTest {

  @Mock
  private CompetitionService competitionService;

  @Mock
  private TelegramService telegramService;

  private FixturesScheduler fixturesScheduler;

  @BeforeEach
  void setUp() {
    fixturesScheduler = new FixturesScheduler(competitionService, telegramService, true);
  }

  @Test
  void refreshFixturesRefreshesEveryActiveSeason() {
    var firstSeason = season();
    var secondSeason = season();
    when(competitionService.getActiveSeasons()).thenReturn(List.of(firstSeason, secondSeason));

    fixturesScheduler.refreshFixtures();

    verify(competitionService).refreshFixturesOrThrow(firstSeason);
    verify(competitionService).refreshFixturesOrThrow(secondSeason);
    verifyNoInteractions(telegramService);
  }

  @Test
  void refreshFixturesDoesNothingWhenSchedulerIsDisabled() {
    fixturesScheduler = new FixturesScheduler(competitionService, telegramService, false);

    fixturesScheduler.refreshFixtures();

    verifyNoInteractions(competitionService, telegramService);
  }

  @Test
  void refreshFixturesReportsProviderFailuresAndContinuesWithOtherSeasons() {
    var failedSeason = season("Premier League", "2025");
    var successfulSeason = season("Champions League", "2025");
    var providerFailure = new RuntimeException("REQUEST_ERROR: HTTP 429; headers={x-ratelimit-requests-remaining=98}");
    when(competitionService.getActiveSeasons()).thenReturn(List.of(failedSeason, successfulSeason));
    doThrow(providerFailure).when(competitionService).refreshFixturesOrThrow(failedSeason);

    fixturesScheduler.refreshFixtures();

    verify(competitionService).refreshFixturesOrThrow(failedSeason);
    verify(competitionService).refreshFixturesOrThrow(successfulSeason);

    var reportCaptor = ArgumentCaptor.forClass(Exception.class);
    verify(telegramService).sendErrorReport(reportCaptor.capture());
    assertThat(reportCaptor.getValue()).isInstanceOf(FixturesSynchronizationException.class);
    assertThat(reportCaptor.getValue().getMessage()).contains("Premier League 2025");
    assertThat(reportCaptor.getValue().getSuppressed()).containsExactly(providerFailure);
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

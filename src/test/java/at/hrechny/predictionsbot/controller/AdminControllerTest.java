package at.hrechny.predictionsbot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.exception.RequestValidationException;
import at.hrechny.predictionsbot.controller.model.competition.CompetitionCreateRequestDto;
import at.hrechny.predictionsbot.controller.model.competition.CompetitionResponseDto;
import at.hrechny.predictionsbot.controller.model.competition.SeasonCreateRequestDto;
import at.hrechny.predictionsbot.controller.model.competition.SeasonUpdateRequestDto;
import at.hrechny.predictionsbot.controller.model.prediction.PredictionRequestDto;
import at.hrechny.predictionsbot.controller.model.service.PushUpdateRequestDto;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import io.micronaut.http.HttpStatus;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

  private static final Long USER_ID = 42L;

  @Mock
  private CompetitionService competitionService;

  @Mock
  private PredictionService predictionService;

  @Mock
  private UserService userService;

  @Mock
  private TelegramService telegramService;

  private CompetitionController competitionController;
  private PredictionController predictionController;
  private ServiceController serviceController;

  @BeforeEach
  void setUp() {
    competitionController = new CompetitionController(competitionService, telegramService);
    predictionController = new PredictionController(predictionService);
    serviceController = new ServiceController(userService, telegramService);
  }

  @Test
  void addCompetitionStoresCompetitionAndSendsTelegramCompetitionUpdate() {
    var competitionId = UUID.randomUUID();
    var request = competitionCreateRequest(null, "Premier League", 39L, true);
    when(competitionService.addCompetition(any(CompetitionCreateRequestDto.class))).thenReturn(competitionId);

    var response = competitionController.addCompetition(request);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body()).containsEntry("id", competitionId);
    verify(competitionService).addCompetition(argThat(competition ->
        competition.getId() == null
            && "Premier League".equals(competition.getName())
            && Long.valueOf(39L).equals(competition.getApiFootballId())
            && competition.isActive()));
    verify(telegramService).sendCompetition(competitionId);
  }

  @Test
  void addCompetitionRejectsClientProvidedIdBeforeSideEffects() {
    var request = competitionCreateRequest(UUID.randomUUID(), "Premier League", 39L, true);

    assertThatThrownBy(() -> competitionController.addCompetition(request))
        .isInstanceOf(RequestValidationException.class);

    verifyNoInteractions(competitionService, telegramService);
  }

  @Test
  void getCompetitionsReturnsCompetitionList() {
    var competition = competitionResponse(UUID.randomUUID(), "Premier League", 39L, true);
    when(competitionService.getCompetitions()).thenReturn(List.of(competition));

    var response = competitionController.getCompetitions();

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body()).containsExactly(competition);
  }

  @Test
  void addSeasonStoresSeasonAndPushesCompetitionUpdate() {
    var competitionId = UUID.randomUUID();
    var seasonId = UUID.randomUUID();
    var request = seasonCreateRequest(null, Year.of(2026), true);
    when(competitionService.addSeason(eq(competitionId), any(SeasonCreateRequestDto.class))).thenReturn(seasonId);

    var response = competitionController.addSeason(competitionId, request);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body()).containsEntry("id", seasonId);
    verify(competitionService).addSeason(eq(competitionId), argThat(season ->
        season.getId() == null
            && Year.of(2026).equals(season.getYear())
            && season.isActive()));
    verify(telegramService).pushUpdate(competitionId);
  }

  @Test
  void addSeasonRejectsClientProvidedIdBeforeSideEffects() {
    var competitionId = UUID.randomUUID();
    var request = seasonCreateRequest(UUID.randomUUID(), Year.of(2026), true);

    assertThatThrownBy(() -> competitionController.addSeason(competitionId, request))
        .isInstanceOf(RequestValidationException.class);

    verify(competitionService, never()).addSeason(eq(competitionId), any(SeasonCreateRequestDto.class));
    verifyNoInteractions(telegramService);
  }

  @Test
  void updateSeasonUsesPathSeasonIdAndPushesCompetitionUpdate() {
    var competitionId = UUID.randomUUID();
    var seasonId = UUID.randomUUID();
    var request = seasonUpdateRequest(null, Year.of(2026), false);

    var response = competitionController.updateSeason(competitionId, seasonId, request);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    verify(competitionService).updateSeason(eq(competitionId), argThat(season ->
        seasonId.equals(season.getId())
            && Year.of(2026).equals(season.getYear())
            && !season.isActive()));
    verify(telegramService).pushUpdate(competitionId);
  }

  @Test
  void updateSeasonRejectsMismatchedBodyIdBeforeSideEffects() {
    var competitionId = UUID.randomUUID();
    var seasonId = UUID.randomUUID();
    var request = seasonUpdateRequest(UUID.randomUUID(), Year.of(2026), true);

    assertThatThrownBy(() -> competitionController.updateSeason(competitionId, seasonId, request))
        .isInstanceOf(RequestValidationException.class);

    verify(competitionService, never()).updateSeason(eq(competitionId), any(SeasonUpdateRequestDto.class));
    verifyNoInteractions(telegramService);
  }

  @Test
  void refreshAllFixturesRefreshesEachActiveSeason() {
    var firstSeason = seasonEntity();
    var secondSeason = seasonEntity();
    when(competitionService.getActiveSeasons()).thenReturn(List.of(firstSeason, secondSeason));

    var response = competitionController.refreshFixtures();

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    verify(competitionService).refreshFixtures(firstSeason);
    verify(competitionService).refreshFixtures(secondSeason);
  }

  @Test
  void refreshCompetitionFixturesRefreshesCurrentSeason() {
    var competitionId = UUID.randomUUID();
    var season = seasonEntity();
    when(competitionService.getCurrentSeason(competitionId)).thenReturn(season);

    var response = competitionController.refreshFixtures(competitionId);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    verify(competitionService).refreshFixtures(season);
  }

  @Test
  void addPredictionsDelegatesToPredictionService() {
    var firstMatchId = UUID.randomUUID();
    var secondMatchId = UUID.randomUUID();
    var firstPrediction = prediction(firstMatchId, 2, 1, true);
    var secondPrediction = prediction(secondMatchId, 0, 0, false);

    var response = predictionController.addPredictions(USER_ID, List.of(firstPrediction, secondPrediction));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    verify(predictionService).savePredictions(eq(USER_ID), argThat(predictions ->
        predictions.size() == 2
            && firstMatchId.equals(predictions.get(0).getMatchId())
            && predictions.get(0).isDoubleUp()
            && secondMatchId.equals(predictions.get(1).getMatchId())
            && !predictions.get(1).isDoubleUp()));
  }

  @Test
  void pushUpdateSendsMessageToEveryActiveUser() {
    var firstUser = user(1L);
    var secondUser = user(2L);
    when(userService.getUsers()).thenReturn(List.of(firstUser, secondUser));

    var request = new PushUpdateRequestDto();
    request.setMessage("Refresh competitions");
    request.setUpdateCompetitionList(true);

    var response = serviceController.pushUpdate(request);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    verify(telegramService).pushUpdate(1L, "Refresh competitions", true);
    verify(telegramService).pushUpdate(2L, "Refresh competitions", true);
  }

  private CompetitionCreateRequestDto competitionCreateRequest(UUID id, String name, Long apiFootballId, boolean active) {
    var competition = new CompetitionCreateRequestDto();
    competition.setId(id);
    competition.setName(name);
    competition.setApiFootballId(apiFootballId);
    competition.setActive(active);
    return competition;
  }

  private CompetitionResponseDto competitionResponse(UUID id, String name, Long apiFootballId, boolean active) {
    var competition = new CompetitionResponseDto();
    competition.setId(id);
    competition.setName(name);
    competition.setApiFootballId(apiFootballId);
    competition.setActive(active);
    return competition;
  }

  private SeasonCreateRequestDto seasonCreateRequest(UUID id, Year year, boolean active) {
    var season = new SeasonCreateRequestDto();
    season.setId(id);
    season.setYear(year);
    season.setActive(active);
    return season;
  }

  private SeasonUpdateRequestDto seasonUpdateRequest(UUID id, Year year, boolean active) {
    var season = new SeasonUpdateRequestDto();
    season.setId(id);
    season.setYear(year);
    season.setActive(active);
    return season;
  }

  private PredictionRequestDto prediction(UUID matchId, int home, int away, boolean doubleUp) {
    var prediction = new PredictionRequestDto();
    prediction.setMatchId(matchId);
    prediction.setPredictionHome(home);
    prediction.setPredictionAway(away);
    prediction.setDoubleUp(doubleUp);
    return prediction;
  }

  private SeasonEntity seasonEntity() {
    var season = new SeasonEntity();
    season.setId(UUID.randomUUID());
    return season;
  }

  private UserEntity user(Long id) {
    var user = new UserEntity();
    user.setId(id);
    return user;
  }
}

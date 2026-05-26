package at.hrechny.predictionsbot.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.config.MessageResolver;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.PredictionEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.TeamEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.model.RoundType;
import at.hrechny.predictionsbot.controller.model.competition.CompetitionResponseDto;
import at.hrechny.predictionsbot.controller.model.league.LeagueCreateRequestDto;
import at.hrechny.predictionsbot.controller.model.league.LeagueResponseDto;
import at.hrechny.predictionsbot.controller.model.league.LeagueUpdateRequestDto;
import at.hrechny.predictionsbot.controller.model.prediction.ResultResponseDto;
import at.hrechny.predictionsbot.controller.model.user.UserResponseDto;
import at.hrechny.predictionsbot.exception.InputValidationException;
import at.hrechny.predictionsbot.exception.LimitExceededException;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.LeagueService;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import at.hrechny.predictionsbot.util.HashUtils;
import io.micronaut.http.HttpStatus;
import io.micronaut.views.ModelAndView;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TelegramWebAppControllerTest {

  private static final Long USER_ID = 42L;
  private static final String HASH = "valid-hash";

  @Mock
  private CompetitionService competitionService;

  @Mock
  private PredictionService predictionService;

  @Mock
  private LeagueService leagueService;

  @Mock
  private UserService userService;

  @Mock
  private HashUtils hashUtils;

  @Mock
  private TelegramService telegramService;

  private TelegramWebAppController controller;

  @BeforeEach
  void setUp() throws Exception {
    controller = new TelegramWebAppController(
        competitionService,
        predictionService,
        leagueService,
        userService,
        telegramService,
        hashUtils,
        new MessageResolver());
    setApplicationUrl(controller, "http://localhost");
    lenient().when(hashUtils.getHash(USER_ID.toString())).thenReturn(HASH);
  }

  @Test
  void predictionsRouteBuildsMobileWebappModelForUpcomingRound() {
    var competitionId = UUID.randomUUID();
    var user = userEntity();
    var season = season(competitionId, "Premier League");
    var firstRound = round(season, 1);
    var secondRound = round(season, 2);
    var earlyMatch = match(secondRound, "Arsenal", "Chelsea", Instant.parse("2026-05-24T15:00:00Z"), MatchStatus.PLANNED);
    var lateMatch = match(secondRound, "Liverpool", "Everton", Instant.parse("2026-05-24T17:00:00Z"), MatchStatus.PLANNED);
    firstRound.setMatches(List.of(match(firstRound, "Spurs", "Leeds", Instant.parse("2026-05-23T17:00:00Z"), MatchStatus.PLANNED)));
    secondRound.setMatches(List.of(lateMatch, earlyMatch));
    season.setRounds(List.of(firstRound, secondRound));

    when(userService.getUser(USER_ID)).thenReturn(user);
    when(competitionService.getUpcomingRound(competitionId)).thenReturn(secondRound);

    var response = controller.getPredictions(HASH, USER_ID, competitionId, null);
    var model = model(response);

    assertThat(view(response)).isEqualTo("predictions");
    assertThat(model.get("user")).isSameAs(user);
    assertThat(model.get("rounds")).isEqualTo(List.of(firstRound, secondRound));
    assertThat(model.get("fixtures")).isEqualTo(List.of(earlyMatch, lateMatch));
    assertThat(model.get("baseUrl"))
        .isEqualTo("http://localhost/webapp/" + HASH + "/users/" + USER_ID + "/predictions?competitionId=" + competitionId + "&round=");
  }

  @Test
  void predictionsRouteBuildsNoUpcomingMatchesModelWhenRoundIsEmpty() {
    var competitionId = UUID.randomUUID();
    var competition = new CompetitionResponseDto();
    competition.setName("Premier League");

    when(userService.getUser(USER_ID)).thenReturn(userEntity());
    when(competitionService.getUpcomingRound(competitionId)).thenReturn(null);
    when(competitionService.getCompetition(competitionId)).thenReturn(competition);

    var response = controller.getPredictions(HASH, USER_ID, competitionId, null);

    assertThat(view(response)).isEqualTo("no-upcoming-matches");
    assertThat(model(response)).containsEntry("competitionName", "Premier League");
  }

  @Test
  void resultsRouteRefreshesFixturesAndBuildsScoreTablesModel() {
    var competitionId = UUID.randomUUID();
    var user = userEntity();
    var season = season(competitionId, "Premier League");
    var firstRound = round(season, 1);
    var secondRound = round(season, 2);
    var finishedMatch = match(firstRound, "Arsenal", "Chelsea", Instant.parse("2026-05-20T17:00:00Z"), MatchStatus.FINISHED);
    finishedMatch.setHomeTeamScore(2);
    finishedMatch.setAwayTeamScore(1);
    finishedMatch.getPredictions().add(prediction(finishedMatch, user, 2, 1, true));
    var startedMatch = match(secondRound, "Liverpool", "Everton", Instant.parse("2026-05-21T17:00:00Z"), MatchStatus.STARTED);
    startedMatch.setHomeTeamScore(1);
    startedMatch.setAwayTeamScore(1);
    startedMatch.getPredictions().add(prediction(startedMatch, user, 1, 1, false));
    firstRound.setMatches(List.of(finishedMatch));
    secondRound.setMatches(List.of(startedMatch));
    season.setRounds(List.of(firstRound, secondRound));

    when(userService.getUser(USER_ID)).thenReturn(user);
    when(competitionService.getCurrentSeason(competitionId)).thenReturn(season);
    when(competitionService.hasNonFinishedMatches(season)).thenReturn(true);
    when(competitionService.getCompetition(competitionId)).thenReturn(competition("Premier League"));
    when(predictionService.getResults(season.getId())).thenReturn(List.of(result(USER_ID, "Alice", 2, 2, 10, null)));
    when(predictionService.getResults(anyList())).thenReturn(List.of(result(USER_ID, "Alice", 1, 1, 0, 5)));

    var response = controller.getResults(HASH, USER_ID, competitionId, null, null);
    var model = model(response);

    assertThat(view(response)).isEqualTo("results");
    assertThat(model.get("user")).isSameAs(user);
    assertThat(model.get("rounds")).isEqualTo(List.of(firstRound, secondRound));
    assertThat(model.get("matches")).isEqualTo(List.of(startedMatch, finishedMatch));
    assertThat(model.get("competitionName")).isEqualTo("Premier League");
    assertThat(model.get("baseUrl"))
        .isEqualTo("http://localhost/webapp/" + HASH + "/users/" + USER_ID + "/results?competitionId=" + competitionId + "&round=");
    var matchResults = (Map<?, ?>) model.get("matchResults");
    assertThat(matchResults.containsKey(startedMatch.getId().toString())).isTrue();
    assertThat(matchResults.containsKey(finishedMatch.getId().toString())).isTrue();
    verify(competitionService).refreshActiveFixtures(season.getId());
  }

  @Test
  void resultsRouteDoesNotRefreshWhenSeasonOnlyHasFinishedMatches() {
    var competitionId = UUID.randomUUID();
    var user = userEntity();
    var season = season(competitionId, "Premier League");
    var round = round(season, 1);
    var finishedMatch = match(round, "Arsenal", "Chelsea", Instant.parse("2026-05-20T17:00:00Z"), MatchStatus.FINISHED);
    finishedMatch.getPredictions().add(prediction(finishedMatch, user, 2, 1, false));
    round.setMatches(List.of(finishedMatch));
    season.setRounds(List.of(round));

    when(userService.getUser(USER_ID)).thenReturn(user);
    when(competitionService.getCurrentSeason(competitionId)).thenReturn(season);
    when(competitionService.hasNonFinishedMatches(season)).thenReturn(false);
    when(competitionService.getCompetition(competitionId)).thenReturn(competition("Premier League"));
    when(predictionService.getResults(season.getId())).thenReturn(List.of(result(USER_ID, "Alice", 1, 1, 3, null)));
    when(predictionService.getResults(anyList())).thenReturn(List.of(result(USER_ID, "Alice", 1, 1, 3, null)));

    var response = controller.getResults(HASH, USER_ID, competitionId, null, null);

    assertThat(view(response)).isEqualTo("results");
    verify(competitionService, never()).refreshActiveFixtures(season.getId());
    verify(telegramService, never()).sendErrorReport(any(Exception.class));
  }

  @Test
  void resultsRouteRendersStoredDataAndReportsRefreshFailure() {
    var competitionId = UUID.randomUUID();
    var user = userEntity();
    var season = season(competitionId, "Premier League");
    var round = round(season, 1);
    var startedMatch = match(round, "Liverpool", "Everton", Instant.parse("2026-05-21T17:00:00Z"), MatchStatus.STARTED);
    startedMatch.getPredictions().add(prediction(startedMatch, user, 1, 1, false));
    round.setMatches(List.of(startedMatch));
    season.setRounds(List.of(round));

    when(userService.getUser(USER_ID)).thenReturn(user);
    when(competitionService.getCurrentSeason(competitionId)).thenReturn(season);
    when(competitionService.hasNonFinishedMatches(season)).thenReturn(true);
    when(competitionService.refreshActiveFixtures(season.getId())).thenReturn(false);
    when(competitionService.getCompetition(competitionId)).thenReturn(competition("Premier League"));
    when(predictionService.getResults(season.getId())).thenReturn(List.of(result(USER_ID, "Alice", 1, 1, 3, null)));
    when(predictionService.getResults(anyList())).thenReturn(List.of(result(USER_ID, "Alice", 1, 1, 3, null)));

    var response = controller.getResults(HASH, USER_ID, competitionId, null, null);

    assertThat(view(response)).isEqualTo("results");
    assertThat(model(response)).doesNotContainKey("warning");
    verify(telegramService).sendErrorReport(any(Exception.class));
  }

  @Test
  void resultsRouteBuildsNoResultsModel() {
    var competitionId = UUID.randomUUID();
    var season = season(competitionId, "Premier League");
    season.setRounds(List.of());

    when(userService.getUser(USER_ID)).thenReturn(userEntity());
    when(competitionService.getCurrentSeason(competitionId)).thenReturn(season);
    when(competitionService.getCompetition(competitionId)).thenReturn(competition("Premier League"));
    when(predictionService.getResults(season.getId())).thenReturn(List.of());

    var response = controller.getResults(HASH, USER_ID, competitionId, null, null);

    assertThat(view(response)).isEqualTo("no-results");
    assertThat(model(response)).containsEntry("competitionName", "Premier League");
    verify(competitionService, never()).refreshActiveFixtures(season.getId());
  }

  @Test
  void leaguesRouteBuildsMaintenancePageModel() {
    var user = userEntity();
    when(userService.getUser(USER_ID)).thenReturn(user);

    var response = controller.getLeagues(HASH, USER_ID);

    assertThat(view(response)).isEqualTo("leagues");
    assertThat(model(response)).containsEntry("user", user);
  }

  @Test
  void createLeagueRouteDelegatesRequestToLeagueService() {
    var leagueId = UUID.randomUUID();
    var competitionId = UUID.randomUUID();
    var request = leagueCreateRequest("Office League", List.of(competitionId));
    when(leagueService.create(eq(USER_ID), any())).thenReturn(new LeagueResponseDto(leagueId));

    var response = controller.createLeague(HASH, USER_ID, request);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body().getId()).isEqualTo(leagueId);
    verify(leagueService).create(eq(USER_ID), argThat(leagueRequest ->
        "Office League".equals(leagueRequest.getName())
            && leagueRequest.getCompetitions().equals(List.of(competitionId))));
  }

  @Test
  void createLeagueRouteMapsValidationFailuresToBadRequest() {
    when(leagueService.create(any(), any())).thenThrow(new InputValidationException("invalid"));

    var response = controller.createLeague(HASH, USER_ID, leagueCreateRequest("ab", List.of()));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
    verify(userService, never()).getUser(USER_ID);
  }

  @Test
  void createLeagueRouteMapsLimitFailuresToConflict() {
    when(leagueService.create(any(), any())).thenThrow(new LimitExceededException("too many leagues"));

    var response = controller.createLeague(HASH, USER_ID, leagueCreateRequest("Office League", List.of()));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.CONFLICT.getCode());
  }

  @Test
  void updateLeagueRouteDelegatesRequestToLeagueService() {
    var leagueId = UUID.randomUUID();
    var competitionId = UUID.randomUUID();
    var request = leagueUpdateRequest("Updated League", List.of(competitionId));
    when(leagueService.update(eq(USER_ID), eq(leagueId), any())).thenReturn(new LeagueResponseDto(leagueId));

    var response = controller.updateLeague(HASH, USER_ID, leagueId, request);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body().getId()).isEqualTo(leagueId);
    verify(leagueService).update(eq(USER_ID), eq(leagueId), argThat(leagueRequest ->
        "Updated League".equals(leagueRequest.getName())
            && leagueRequest.getCompetitions().equals(List.of(competitionId))));
  }

  @Test
  void updateLeagueRouteMapsValidationFailuresToBadRequest() {
    var leagueId = UUID.randomUUID();
    when(leagueService.update(eq(USER_ID), eq(leagueId), any())).thenThrow(new InputValidationException("invalid"));

    var response = controller.updateLeague(HASH, USER_ID, leagueId, leagueUpdateRequest("Updated League", List.of()));

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
  }

  @Test
  void joinLeagueRouteDelegatesToLeagueService() {
    var leagueId = UUID.randomUUID();
    when(leagueService.join(USER_ID, leagueId)).thenReturn(new LeagueResponseDto(leagueId));

    var response = controller.joinLeague(HASH, USER_ID, leagueId);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body().getId()).isEqualTo(leagueId);
    verify(leagueService).join(USER_ID, leagueId);
  }

  @Test
  void joinLeagueRouteMapsValidationFailuresToBadRequest() {
    var leagueId = UUID.randomUUID();
    when(leagueService.join(USER_ID, leagueId)).thenThrow(new InputValidationException("invalid"));

    var response = controller.joinLeague(HASH, USER_ID, leagueId);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
  }

  @Test
  void deleteLeagueRouteDelegatesToLeagueService() {
    var leagueId = UUID.randomUUID();
    when(leagueService.delete(USER_ID, leagueId)).thenReturn(new LeagueResponseDto(leagueId));

    var response = controller.deleteLeague(HASH, USER_ID, leagueId);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.OK.getCode());
    assertThat(response.body().getId()).isEqualTo(leagueId);
    verify(leagueService).delete(USER_ID, leagueId);
  }

  @Test
  void deleteLeagueRouteMapsValidationFailuresToBadRequest() {
    var leagueId = UUID.randomUUID();
    when(leagueService.delete(USER_ID, leagueId)).thenThrow(new InputValidationException("invalid"));

    var response = controller.deleteLeague(HASH, USER_ID, leagueId);

    assertThat(response.getStatus().getCode()).isEqualTo(HttpStatus.BAD_REQUEST.getCode());
  }

  private void setApplicationUrl(TelegramWebAppController controller, String applicationUrl) throws Exception {
    Field field = TelegramWebAppController.class.getDeclaredField("applicationUrl");
    field.setAccessible(true);
    field.set(controller, applicationUrl);
  }

  private String view(ModelAndView<Map<String, Object>> response) {
    return response.getView().orElseThrow();
  }

  private Map<String, Object> model(ModelAndView<Map<String, Object>> response) {
    return response.getModel().orElseThrow();
  }

  private UserEntity userEntity() {
    var user = new UserEntity();
    user.setId(USER_ID);
    user.setUsername("Alice");
    user.setLanguage(Locale.ENGLISH);
    user.setTimezone(ZoneOffset.UTC);
    return user;
  }

  private SeasonEntity season(UUID competitionId, String competitionName) {
    var competition = new CompetitionEntity();
    competition.setId(competitionId);
    competition.setName(competitionName);

    var season = new SeasonEntity();
    season.setId(UUID.randomUUID());
    season.setCompetition(competition);
    season.setActive(true);
    season.setYear("2026");
    return season;
  }

  private RoundEntity round(SeasonEntity season, int orderNumber) {
    var round = new RoundEntity();
    round.setId(UUID.randomUUID());
    round.setSeason(season);
    round.setOrderNumber(orderNumber);
    round.setType(RoundType.SEASON);
    round.setApiFootballId("Regular Season - " + orderNumber);
    return round;
  }

  private MatchEntity match(RoundEntity round, String home, String away, Instant startTime, MatchStatus status) {
    var match = new MatchEntity();
    match.setId(UUID.randomUUID());
    match.setRound(round);
    match.setHomeTeam(team(home));
    match.setAwayTeam(team(away));
    match.setStartTime(startTime);
    match.setStatus(status);
    return match;
  }

  private TeamEntity team(String name) {
    var team = new TeamEntity();
    team.setId(UUID.randomUUID());
    team.setName(name);
    team.setLogoUrl("https://example.test/" + name + ".png");
    return team;
  }

  private PredictionEntity prediction(MatchEntity match, UserEntity user, int home, int away, boolean doubleUp) {
    var prediction = new PredictionEntity();
    prediction.setId(UUID.randomUUID());
    prediction.setMatch(match);
    prediction.setUser(user);
    prediction.setPredictionHome(home);
    prediction.setPredictionAway(away);
    prediction.setDoubleUp(doubleUp);
    return prediction;
  }

  private CompetitionResponseDto competition(String name) {
    var competition = new CompetitionResponseDto();
    competition.setName(name);
    return competition;
  }

  private LeagueCreateRequestDto leagueCreateRequest(String name, List<UUID> competitions) {
    var request = new LeagueCreateRequestDto();
    request.setName(name);
    request.setCompetitions(competitions);
    return request;
  }

  private LeagueUpdateRequestDto leagueUpdateRequest(String name, List<UUID> competitions) {
    var request = new LeagueUpdateRequestDto();
    request.setName(name);
    request.setCompetitions(competitions);
    return request;
  }

  private ResultResponseDto result(Long userId, String userName, int predictions, int guessed, int sum, Integer liveSum) {
    var result = new ResultResponseDto();
    var user = new UserResponseDto();
    user.setId(userId);
    user.setName(userName);
    result.setUser(user);
    result.setPredictions(predictions);
    result.setGuessed(guessed);
    result.setSum(sum);
    result.setLiveSum(liveSum);
    return result;
  }
}

package at.hrechny.predictionsbot.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import at.hrechny.predictionsbot.config.WebConfig;
import at.hrechny.predictionsbot.controller.filter.HashVerificationFilter;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.PredictionEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.TeamEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.model.RoundType;
import at.hrechny.predictionsbot.exception.InputValidationException;
import at.hrechny.predictionsbot.exception.LimitExceededException;
import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.model.LeagueResponse;
import at.hrechny.predictionsbot.model.Result;
import at.hrechny.predictionsbot.model.User;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.LeagueService;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.util.HashUtils;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.LocaleResolver;

@WebMvcTest(
    value = TelegramWebAppController.class,
    properties = {
        "application.url=http://localhost",
        "spring.cloud.vault.enabled=false",
        "spring.config.import=optional:file:./does-not-exist.properties"
    })
@Import({HashVerificationFilter.class, WebConfig.class})
class TelegramWebAppControllerTest {

  private static final Long USER_ID = 42L;
  private static final String HASH = "valid-hash";

  @Autowired
  private MockMvc mockMvc;

  @MockBean
  private LocaleResolver localeResolver;

  @MockBean
  private CompetitionService competitionService;

  @MockBean
  private PredictionService predictionService;

  @MockBean
  private LeagueService leagueService;

  @MockBean
  private UserService userService;

  @MockBean
  private HashUtils hashUtils;

  @MockBean
  private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @BeforeEach
  void setUp() {
    when(hashUtils.getHash(USER_ID.toString())).thenReturn(HASH);
  }

  @Test
  void webappRejectsInvalidHashBeforeControllerIsCalled() throws Exception {
    mockMvc.perform(get("/webapp/wrong-hash/users/{userId}/leagues", USER_ID))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(userService);
  }

  @Test
  void predictionsRouteRendersMobileWebappPageForUpcomingRound() throws Exception {
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

    mockMvc.perform(get("/webapp/{hash}/users/{userId}/predictions", HASH, USER_ID)
            .param("competitionId", competitionId.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("predictions"))
        .andExpect(model().attribute("rounds", hasSize(2)))
        .andExpect(model().attribute("fixtures", List.of(earlyMatch, lateMatch)))
        .andExpect(model().attribute("baseUrl",
            "http://localhost/webapp/" + HASH + "/users/" + USER_ID + "/predictions?competitionId=" + competitionId + "&round="))
        .andExpect(content().string(containsString("viewport")))
        .andExpect(content().string(containsString("Premier League")))
        .andExpect(content().string(containsString("Round 1")))
        .andExpect(content().string(containsString("Round 2")));
  }

  @Test
  void predictionsRouteRendersNoUpcomingMatchesWhenRoundIsEmpty() throws Exception {
    var competitionId = UUID.randomUUID();
    var competition = new Competition();
    competition.setName("Premier League");

    when(userService.getUser(USER_ID)).thenReturn(userEntity());
    when(competitionService.getUpcomingRound(competitionId)).thenReturn(null);
    when(competitionService.getCompetition(competitionId)).thenReturn(competition);

    mockMvc.perform(get("/webapp/{hash}/users/{userId}/predictions", HASH, USER_ID)
            .param("competitionId", competitionId.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("no-upcoming-matches"))
        .andExpect(model().attribute("competitionName", "Premier League"))
        .andExpect(content().string(containsString("No upcoming matches")))
        .andExpect(content().string(containsString(".setText(\"Close\")")));
  }

  @Test
  void resultsRouteRefreshesFixturesAndRendersScoreTables() throws Exception {
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
    when(competitionService.getCompetition(competitionId)).thenReturn(competition("Premier League"));
    when(predictionService.getResults(season.getId())).thenReturn(List.of(result(USER_ID, "Alice", 2, 2, 10, null)));
    when(predictionService.getResults(anyList())).thenReturn(List.of(result(USER_ID, "Alice", 1, 1, 0, 5)));

    mockMvc.perform(get("/webapp/{hash}/users/{userId}/results", HASH, USER_ID)
            .param("competitionId", competitionId.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("results"))
        .andExpect(model().attribute("rounds", hasSize(2)))
        .andExpect(model().attribute("matches", hasSize(2)))
        .andExpect(content().string(containsString("Premier League")))
        .andExpect(content().string(containsString("Predicts")))
        .andExpect(content().string(containsString("Alice")))
        .andExpect(content().string(containsString("Arsenal")))
        .andExpect(content().string(containsString("Chelsea")));

    verify(competitionService).refreshActiveFixtures(season.getId());
  }

  @Test
  void resultsRouteRendersNoResultsPageWithQuotedTelegramButtonText() throws Exception {
    var competitionId = UUID.randomUUID();
    var season = season(competitionId, "Premier League");
    season.setRounds(List.of());

    when(userService.getUser(USER_ID)).thenReturn(userEntity());
    when(competitionService.getCurrentSeason(competitionId)).thenReturn(season);
    when(competitionService.getCompetition(competitionId)).thenReturn(competition("Premier League"));
    when(predictionService.getResults(season.getId())).thenReturn(List.of());

    mockMvc.perform(get("/webapp/{hash}/users/{userId}/results", HASH, USER_ID)
            .param("competitionId", competitionId.toString()))
        .andExpect(status().isOk())
        .andExpect(view().name("no-results"))
        .andExpect(content().string(containsString("No available results")))
        .andExpect(content().string(containsString(".setText(\"Close\")")));

    verify(competitionService).refreshActiveFixtures(season.getId());
  }

  @Test
  void leaguesRouteRendersMaintenancePageWithQuotedTelegramButtonText() throws Exception {
    when(userService.getUser(USER_ID)).thenReturn(userEntity());

    mockMvc.perform(get("/webapp/{hash}/users/{userId}/leagues", HASH, USER_ID))
        .andExpect(status().isOk())
        .andExpect(view().name("leagues"))
        .andExpect(content().string(containsString("Manage Leagues of users")))
        .andExpect(content().string(containsString("Page is under development and will be available soon")))
        .andExpect(content().string(containsString(".setText(\"Close\")")));
  }

  @Test
  void leaguesRouteMapsValidationFailuresToBadRequest() throws Exception {
    var requestBody = "{\"name\":\"ab\",\"competitions\":[]}";
    when(leagueService.create(any(), any())).thenThrow(new InputValidationException("invalid"));

    mockMvc.perform(post("/webapp/{hash}/users/{userId}/leagues", HASH, USER_ID)
            .contentType("application/json")
            .content(requestBody))
        .andExpect(status().isBadRequest());

    verify(userService, never()).getUser(USER_ID);
  }

  @Test
  void createLeagueRouteDelegatesRequestToLeagueService() throws Exception {
    var leagueId = UUID.randomUUID();
    var competitionId = UUID.randomUUID();
    when(leagueService.create(eq(USER_ID), any())).thenReturn(new LeagueResponse(leagueId));

    mockMvc.perform(post("/webapp/{hash}/users/{userId}/leagues", HASH, USER_ID)
            .contentType("application/json")
            .content("""
                {
                  "name": "Office League",
                  "competitions": ["%s"]
                }
                """.formatted(competitionId)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(leagueId.toString()));

    verify(leagueService).create(eq(USER_ID), argThat(request ->
        "Office League".equals(request.getName())
            && request.getCompetitions().equals(List.of(competitionId))));
  }

  @Test
  void createLeagueRouteMapsLimitFailuresToConflict() throws Exception {
    when(leagueService.create(any(), any())).thenThrow(new LimitExceededException("too many leagues"));

    mockMvc.perform(post("/webapp/{hash}/users/{userId}/leagues", HASH, USER_ID)
            .contentType("application/json")
            .content("""
                {
                  "name": "Office League",
                  "competitions": []
                }
                """))
        .andExpect(status().isConflict());
  }

  @Test
  void updateLeagueRouteDelegatesRequestToLeagueService() throws Exception {
    var leagueId = UUID.randomUUID();
    var competitionId = UUID.randomUUID();
    when(leagueService.update(eq(USER_ID), eq(leagueId), any())).thenReturn(new LeagueResponse(leagueId));

    mockMvc.perform(put("/webapp/{hash}/users/{userId}/leagues/{leagueId}", HASH, USER_ID, leagueId)
            .contentType("application/json")
            .content("""
                {
                  "name": "Updated League",
                  "competitions": ["%s"]
                }
                """.formatted(competitionId)))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(leagueId.toString()));

    verify(leagueService).update(eq(USER_ID), eq(leagueId), argThat(request ->
        "Updated League".equals(request.getName())
            && request.getCompetitions().equals(List.of(competitionId))));
  }

  @Test
  void updateLeagueRouteMapsValidationFailuresToBadRequest() throws Exception {
    var leagueId = UUID.randomUUID();
    when(leagueService.update(eq(USER_ID), eq(leagueId), any())).thenThrow(new InputValidationException("invalid"));

    mockMvc.perform(put("/webapp/{hash}/users/{userId}/leagues/{leagueId}", HASH, USER_ID, leagueId)
            .contentType("application/json")
            .content("""
                {
                  "name": "Updated League",
                  "competitions": []
                }
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void joinLeagueRouteDelegatesToLeagueService() throws Exception {
    var leagueId = UUID.randomUUID();
    when(leagueService.join(USER_ID, leagueId)).thenReturn(new LeagueResponse(leagueId));

    mockMvc.perform(post("/webapp/{hash}/users/{userId}/leagues/{leagueId}", HASH, USER_ID, leagueId))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(leagueId.toString()));

    verify(leagueService).join(USER_ID, leagueId);
  }

  @Test
  void joinLeagueRouteMapsValidationFailuresToBadRequest() throws Exception {
    var leagueId = UUID.randomUUID();
    when(leagueService.join(USER_ID, leagueId)).thenThrow(new InputValidationException("invalid"));

    mockMvc.perform(post("/webapp/{hash}/users/{userId}/leagues/{leagueId}", HASH, USER_ID, leagueId))
        .andExpect(status().isBadRequest());
  }

  @Test
  void deleteLeagueRouteDelegatesToLeagueService() throws Exception {
    var leagueId = UUID.randomUUID();
    when(leagueService.delete(USER_ID, leagueId)).thenReturn(new LeagueResponse(leagueId));

    mockMvc.perform(delete("/webapp/{hash}/users/{userId}/leagues/{leagueId}", HASH, USER_ID, leagueId))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.id").value(leagueId.toString()));

    verify(leagueService).delete(USER_ID, leagueId);
  }

  @Test
  void deleteLeagueRouteMapsValidationFailuresToBadRequest() throws Exception {
    var leagueId = UUID.randomUUID();
    when(leagueService.delete(USER_ID, leagueId)).thenThrow(new InputValidationException("invalid"));

    mockMvc.perform(delete("/webapp/{hash}/users/{userId}/leagues/{leagueId}", HASH, USER_ID, leagueId))
        .andExpect(status().isBadRequest());
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

  private Competition competition(String name) {
    var competition = new Competition();
    competition.setName(name);
    return competition;
  }

  private Result result(Long userId, String userName, int predictions, int guessed, int sum, Integer liveSum) {
    var result = new Result();
    var user = new User();
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

package at.hrechny.predictionsbot.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.model.PushUpdate;
import at.hrechny.predictionsbot.model.Season;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import at.hrechny.predictionsbot.util.HashUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Year;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = {
        CompetitionController.class,
        PredictionController.class,
        ServiceController.class
    },
    properties = {
        "secrets.adminKey=admin",
        "spring.cloud.vault.enabled=false",
        "spring.config.import=optional:file:./does-not-exist.properties"
    })
@AutoConfigureMockMvc(addFilters = false)
class AdminControllerTest {

  private static final Long USER_ID = 42L;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @MockBean
  private CompetitionService competitionService;

  @MockBean
  private PredictionService predictionService;

  @MockBean
  private UserService userService;

  @MockBean
  private TelegramService telegramService;

  @MockBean
  private HashUtils hashUtils;

  @MockBean
  private JpaMetamodelMappingContext jpaMetamodelMappingContext;

  @Test
  void addCompetitionStoresCompetitionAndSendsTelegramCompetitionUpdate() throws Exception {
    var competitionId = UUID.randomUUID();
    var request = competition(null, "Premier League", 39L, true);
    when(competitionService.addCompetition(any(Competition.class))).thenReturn(competitionId);

    mockMvc.perform(post("/admin/competitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(competitionId.toString()));

    verify(competitionService).addCompetition(argThat(competition ->
        competition.getId() == null
            && "Premier League".equals(competition.getName())
            && Long.valueOf(39L).equals(competition.getApiFootballId())
            && competition.isActive()));
    verify(telegramService).sendCompetition(competitionId);
  }

  @Test
  void addCompetitionRejectsClientProvidedIdBeforeSideEffects() throws Exception {
    var request = competition(UUID.randomUUID(), "Premier League", 39L, true);

    mockMvc.perform(post("/admin/competitions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    verifyNoInteractions(competitionService, telegramService);
  }

  @Test
  void getCompetitionsReturnsCompetitionList() throws Exception {
    when(competitionService.getCompetitions())
        .thenReturn(List.of(competition(UUID.randomUUID(), "Premier League", 39L, true)));

    mockMvc.perform(get("/admin/competitions"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].name").value("Premier League"))
        .andExpect(jsonPath("$[0].apiFootballId").value(39))
        .andExpect(jsonPath("$[0].active").value(true));
  }

  @Test
  void addSeasonStoresSeasonAndPushesCompetitionUpdate() throws Exception {
    var competitionId = UUID.randomUUID();
    var seasonId = UUID.randomUUID();
    var request = season(null, Year.of(2026), true);
    when(competitionService.addSeason(eq(competitionId), any(Season.class))).thenReturn(seasonId);

    mockMvc.perform(post("/admin/competitions/{competitionId}/seasons", competitionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(seasonId.toString()));

    verify(competitionService).addSeason(eq(competitionId), argThat(season ->
        season.getId() == null
            && Year.of(2026).equals(season.getYear())
            && season.isActive()));
    verify(telegramService).pushUpdate(competitionId);
  }

  @Test
  void addSeasonRejectsClientProvidedIdBeforeSideEffects() throws Exception {
    var competitionId = UUID.randomUUID();
    var request = season(UUID.randomUUID(), Year.of(2026), true);

    mockMvc.perform(post("/admin/competitions/{competitionId}/seasons", competitionId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    verify(competitionService, never()).addSeason(eq(competitionId), any(Season.class));
    verifyNoInteractions(telegramService);
  }

  @Test
  void updateSeasonUsesPathSeasonIdAndPushesCompetitionUpdate() throws Exception {
    var competitionId = UUID.randomUUID();
    var seasonId = UUID.randomUUID();
    var request = season(null, Year.of(2026), false);

    mockMvc.perform(put("/admin/competitions/{competitionId}/seasons/{seasonId}", competitionId, seasonId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(competitionService).updateSeason(eq(competitionId), argThat(season ->
        seasonId.equals(season.getId())
            && Year.of(2026).equals(season.getYear())
            && !season.isActive()));
    verify(telegramService).pushUpdate(competitionId);
  }

  @Test
  void updateSeasonRejectsMismatchedBodyIdBeforeSideEffects() throws Exception {
    var competitionId = UUID.randomUUID();
    var seasonId = UUID.randomUUID();
    var request = season(UUID.randomUUID(), Year.of(2026), true);

    mockMvc.perform(put("/admin/competitions/{competitionId}/seasons/{seasonId}", competitionId, seasonId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest());

    verify(competitionService, never()).updateSeason(eq(competitionId), any(Season.class));
    verifyNoInteractions(telegramService);
  }

  @Test
  void refreshAllFixturesRefreshesEachActiveSeason() throws Exception {
    var firstSeason = seasonEntity();
    var secondSeason = seasonEntity();
    when(competitionService.getActiveSeasons()).thenReturn(List.of(firstSeason, secondSeason));

    mockMvc.perform(post("/admin/fixtures"))
        .andExpect(status().isOk());

    verify(competitionService).refreshFixtures(firstSeason);
    verify(competitionService).refreshFixtures(secondSeason);
  }

  @Test
  void refreshCompetitionFixturesRefreshesCurrentSeason() throws Exception {
    var competitionId = UUID.randomUUID();
    var season = seasonEntity();
    when(competitionService.getCurrentSeason(competitionId)).thenReturn(season);

    mockMvc.perform(post("/admin/fixtures/{competitionId}", competitionId))
        .andExpect(status().isOk());

    verify(competitionService).refreshFixtures(season);
  }

  @Test
  void addPredictionsDelegatesToPredictionService() throws Exception {
    var firstMatchId = UUID.randomUUID();
    var secondMatchId = UUID.randomUUID();

    mockMvc.perform(post("/admin/users/{userId}/predictions", USER_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                [
                  {"matchId":"%s","predictionHome":2,"predictionAway":1,"doubleUp":true},
                  {"matchId":"%s","predictionHome":0,"predictionAway":0,"doubleUp":false}
                ]
                """.formatted(firstMatchId, secondMatchId)))
        .andExpect(status().isOk());

    verify(predictionService).savePredictions(eq(USER_ID), argThat(predictions ->
        predictions.size() == 2
            && firstMatchId.equals(predictions.get(0).getMatchId())
            && predictions.get(0).isDoubleUp()
            && secondMatchId.equals(predictions.get(1).getMatchId())
            && !predictions.get(1).isDoubleUp()));
  }

  @Test
  void pushUpdateSendsMessageToEveryActiveUser() throws Exception {
    var firstUser = user(1L);
    var secondUser = user(2L);
    when(userService.getUsers()).thenReturn(List.of(firstUser, secondUser));

    var request = new PushUpdate();
    request.setMessage("Refresh competitions");
    request.setUpdateCompetitionList(true);

    mockMvc.perform(post("/admin")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk());

    verify(telegramService).pushUpdate(1L, "Refresh competitions", true);
    verify(telegramService).pushUpdate(2L, "Refresh competitions", true);
  }

  private Competition competition(UUID id, String name, Long apiFootballId, boolean active) {
    var competition = new Competition();
    competition.setId(id);
    competition.setName(name);
    competition.setApiFootballId(apiFootballId);
    competition.setActive(active);
    return competition;
  }

  private Season season(UUID id, Year year, boolean active) {
    var season = new Season();
    season.setId(id);
    season.setYear(year);
    season.setActive(active);
    return season;
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

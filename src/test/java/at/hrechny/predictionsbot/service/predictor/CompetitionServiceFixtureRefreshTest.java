package at.hrechny.predictionsbot.service.predictor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.ApiConnector;
import at.hrechny.predictionsbot.exception.ApiConnectorException;
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason;
import at.hrechny.predictionsbot.exception.FixturesSynchronizationException;
import at.hrechny.predictionsbot.connector.model.FixtureSyncStatus;
import at.hrechny.predictionsbot.connector.model.FixtureSyncDto;
import at.hrechny.predictionsbot.connector.model.RoundSyncDto;
import at.hrechny.predictionsbot.connector.model.RoundSyncType;
import at.hrechny.predictionsbot.connector.model.ScoreSyncDto;
import at.hrechny.predictionsbot.connector.model.TeamSyncDto;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.TeamEntity;
import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.model.RoundType;
import at.hrechny.predictionsbot.database.repository.MatchRepository;
import at.hrechny.predictionsbot.database.repository.SeasonRepository;
import at.hrechny.predictionsbot.database.repository.TeamRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceFixtureRefreshTest {

  @Mock
  private SeasonRepository seasonRepository;

  @Mock
  private TeamRepository teamRepository;

  @Mock
  private MatchRepository matchRepository;

  @Mock
  private ApiConnector apiConnector;

  @Mock
  private ApiConnectorService apiConnectorService;

  private CompetitionService competitionService;

  @BeforeEach
  void setUp() {
    lenient().when(apiConnector.getName())
        .thenReturn("api-football");
    lenient().when(apiConnectorService.findInternalId(anyString(), any(ApiConnectorEntityType.class), anyString()))
        .thenReturn(Optional.empty());
    lenient().when(seasonRepository.save(any(SeasonEntity.class))).thenAnswer(invocation -> {
      var season = invocation.getArgument(0, SeasonEntity.class);
      season.getRounds().stream()
          .flatMap(round -> round.getMatches().stream())
          .filter(match -> match.getId() == null)
          .forEach(match -> match.setId(UUID.randomUUID()));
      return season;
    });
    competitionService = new CompetitionService(
        null,
        seasonRepository,
        null,
        null,
        teamRepository,
        matchRepository,
        apiConnector,
        apiConnectorService);
  }

  @Test
  void refreshActiveFixturesDoesNotCallConnectorWhenNoActiveMatchesExist() {
    var season = season();
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of());

    competitionService.refreshActiveFixtures(season.getId());

    verifyNoInteractions(apiConnector);
    verify(seasonRepository, never()).save(season);
  }

  @Test
  void refreshActiveFixturesRequestsOnlyActiveFixtureIdsAndUpdatesExistingMatches() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var match = existingMatch(round, 100L, "Arsenal", "Chelsea");
    round.setMatches(List.of(match));
    season.setRounds(List.of(round));

    var kickoff = OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC);
    var fixture = fixture(
        100L,
        "Regular Season - 1",
        kickoff,
        FixtureSyncStatus.FINISHED,
        team(1L, "Arsenal", "arsenal-new.png"),
        team(2L, "Chelsea", "chelsea-new.png"),
        2,
        1);

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(match));
    when(apiConnector.getFixturesByExternalIds(List.of("100"))).thenReturn(List.of(fixture));

    competitionService.refreshActiveFixtures(season.getId());

    assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
    assertThat(match.getHomeTeamScore()).isEqualTo(2);
    assertThat(match.getAwayTeamScore()).isEqualTo(1);
    assertThat(match.getStartTime()).isEqualTo(kickoff.toInstant());
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshActiveFixturesLeavesExistingDataWhenConnectorFails() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var match = existingMatch(round, 100L, "Arsenal", "Chelsea");
    round.setMatches(List.of(match));
    season.setRounds(List.of(round));

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(match));
    when(apiConnector.getFixturesByExternalIds(anyList())).thenThrow(
        new ApiConnectorException("api-football", Reason.QUOTA_EXCEEDED, null, null));

    assertThatThrownBy(() -> competitionService.refreshActiveFixtures(season.getId()))
        .isInstanceOf(ApiConnectorException.class);

    assertThat(match.getStatus()).isEqualTo(MatchStatus.PLANNED);
    assertThat(match.getHomeTeamScore()).isNull();
    assertThat(match.getAwayTeamScore()).isNull();
    verify(seasonRepository, never()).save(season);
  }

  @Test
  void refreshActiveFixturesEmptyConnectorResponseLeavesExistingDataAndSavesSeason() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var match = existingMatch(round, 100L, "Arsenal", "Chelsea");
    match.setHomeTeamScore(1);
    match.setAwayTeamScore(0);
    match.setStatus(MatchStatus.STARTED);
    round.setMatches(new ArrayList<>(List.of(match)));
    season.setRounds(new ArrayList<>(List.of(round)));

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(match));
    when(apiConnector.getFixturesByExternalIds(List.of("100"))).thenReturn(List.of());

    competitionService.refreshActiveFixtures(season.getId());

    assertThat(match.getStatus()).isEqualTo(MatchStatus.STARTED);
    assertThat(match.getHomeTeamScore()).isEqualTo(1);
    assertThat(match.getAwayTeamScore()).isEqualTo(0);
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshActiveFixturesAppliesReturnedFixturesAndLeavesMissingActiveMatchesUnchanged() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var updatedMatch = existingMatch(round, 100L, "Arsenal", "Chelsea");
    var unchangedMatch = existingMatch(round, 101L, "Liverpool", "Everton");
    unchangedMatch.setHomeTeamScore(1);
    unchangedMatch.setAwayTeamScore(1);
    unchangedMatch.setStatus(MatchStatus.STARTED);
    round.setMatches(new ArrayList<>(List.of(updatedMatch, unchangedMatch)));
    season.setRounds(new ArrayList<>(List.of(round)));

    var kickoff = OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC);
    var fixture = fixture(
        100L,
        "Regular Season - 1",
        kickoff,
        FixtureSyncStatus.FINISHED,
        team(1L, "Arsenal", "arsenal.png"),
        team(2L, "Chelsea", "chelsea.png"),
        2,
        0);

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(updatedMatch, unchangedMatch));
    when(apiConnector.getFixturesByExternalIds(List.of("100", "101"))).thenReturn(List.of(fixture));

    competitionService.refreshActiveFixtures(season.getId());

    assertThat(updatedMatch.getStatus()).isEqualTo(MatchStatus.FINISHED);
    assertThat(updatedMatch.getHomeTeamScore()).isEqualTo(2);
    assertThat(updatedMatch.getAwayTeamScore()).isEqualTo(0);
    assertThat(unchangedMatch.getStatus()).isEqualTo(MatchStatus.STARTED);
    assertThat(unchangedMatch.getHomeTeamScore()).isEqualTo(1);
    assertThat(unchangedMatch.getAwayTeamScore()).isEqualTo(1);
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshActiveFixturesDoesNotCreateMatchWhenReturnedFixtureIdIsUnmapped() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var match = existingMatch(round, 100L, "Arsenal", "Chelsea");
    round.setMatches(new ArrayList<>(List.of(match)));
    season.setRounds(new ArrayList<>(List.of(round)));

    var fixture = fixture(
        999L,
        "Regular Season - 1",
        OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC),
        FixtureSyncStatus.FINISHED,
        team(1L, "Arsenal", "arsenal.png"),
        team(2L, "Chelsea", "chelsea.png"),
        2,
        0);

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(match));
    when(apiConnector.getFixturesByExternalIds(List.of("100"))).thenReturn(List.of(fixture));

    assertThatThrownBy(() -> competitionService.refreshActiveFixtures(season.getId()))
        .isInstanceOf(FixturesSynchronizationException.class)
        .hasMessageContaining("Missing connector mappings")
        .hasMessageContaining("matches=999");

    verify(teamRepository, never()).save(any(TeamEntity.class));
    verify(seasonRepository, never()).save(season);
  }

  @Test
  void refreshActiveFixturesDoesNotCreateRoundsInUpdateOnlyMode() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var match = existingMatch(round, 100L, "Arsenal", "Chelsea");
    round.setMatches(new ArrayList<>(List.of(match)));
    season.setRounds(new ArrayList<>(List.of(round)));

    var fixture = fixture(
        100L,
        "Regular Season - 2",
        OffsetDateTime.of(2026, 5, 28, 18, 30, 0, 0, ZoneOffset.UTC),
        FixtureSyncStatus.FINISHED,
        team(1L, "Arsenal", "arsenal.png"),
        team(2L, "Chelsea", "chelsea.png"),
        2,
        0);

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(match));
    when(apiConnector.getFixturesByExternalIds(List.of("100"))).thenReturn(List.of(fixture));

    assertThatThrownBy(() -> competitionService.refreshActiveFixtures(season.getId()))
        .isInstanceOf(FixturesSynchronizationException.class)
        .hasMessageContaining("Missing connector mappings")
        .hasMessageContaining("rounds=Regular Season - 2");

    verify(apiConnector, never()).getRounds(anyString(), anyString());
    verify(seasonRepository, never()).save(season);
  }

  @Test
  void refreshFixturesFailsWithAllMissingTeamMappingsForNewConnectorFixture() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    season.setRounds(List.of(round));

    var homeTeam = team(1L, "Arsenal", "arsenal.png");
    var awayTeam = team(2L, "Chelsea", "chelsea.png");
    var fixture = fixture(
        100L,
        "Regular Season - 1",
        OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC),
        FixtureSyncStatus.PLANNED,
        homeTeam,
        awayTeam,
        null,
        null);

    when(apiConnector.getSeasonFixtures("39", "2026")).thenReturn(List.of(fixture));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

    assertThatThrownBy(() -> competitionService.refreshFixtures(season))
        .isInstanceOf(FixturesSynchronizationException.class)
        .hasMessageContaining("Missing connector mappings")
        .hasMessageContaining("teams=Arsenal (1), Chelsea (2)");

    assertThat(round.getMatches()).isEmpty();
    verify(teamRepository, never()).save(any(TeamEntity.class));
    verify(seasonRepository, never()).save(season);
  }

  @Test
  void refreshFixturesCreatesMatchForNewConnectorFixtureWithMappedTeams() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    season.setRounds(List.of(round));
    var mappedHomeTeam = teamEntity(1L, "Arsenal", "arsenal.png");
    var mappedAwayTeam = teamEntity(2L, "Chelsea", "chelsea.png");

    var fixture = fixture(
        100L,
        "Regular Season - 1",
        OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC),
        FixtureSyncStatus.PLANNED,
        team(1L, "Arsenal", "arsenal.png"),
        team(2L, "Chelsea", "chelsea.png"),
        null,
        null);

    when(apiConnector.getSeasonFixtures("39", "2026")).thenReturn(List.of(fixture));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

    competitionService.refreshFixtures(season);

    assertThat(round.getMatches()).hasSize(1);
    var createdMatch = round.getMatches().get(0);
    assertThat(createdMatch.getHomeTeam()).isEqualTo(mappedHomeTeam);
    assertThat(createdMatch.getAwayTeam()).isEqualTo(mappedAwayTeam);
    assertThat(createdMatch.getStatus()).isEqualTo(MatchStatus.PLANNED);
    assertThat(createdMatch.getStartTime()).isNotNull();
    verify(apiConnectorService, never()).upsertId(
        eq("api-football"),
        eq(ApiConnectorEntityType.TEAM),
        anyString(),
        any(UUID.class));
    verify(apiConnectorService).upsertId("api-football", ApiConnectorEntityType.MATCH, "100", createdMatch.getId());
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshFixturesCreatesMissingRoundWhenConnectorReturnsUnknownRound() {
    var season = season();
    var existingRound = round(season, "Regular Season - 1");
    existingRound.setMatches(new ArrayList<>());
    season.setRounds(new ArrayList<>(List.of(existingRound)));

    var homeTeam = team(1L, "Arsenal", "arsenal.png");
    var awayTeam = team(2L, "Chelsea", "chelsea.png");
    teamEntity(1L, "Arsenal", "arsenal.png");
    teamEntity(2L, "Chelsea", "chelsea.png");
    var fixture = fixture(
        100L,
        "Regular Season - 2",
        OffsetDateTime.of(2026, 5, 28, 18, 30, 0, 0, ZoneOffset.UTC),
        FixtureSyncStatus.PLANNED,
        homeTeam,
        awayTeam,
        null,
        null);

    when(apiConnector.getSeasonFixtures("39", "2026")).thenReturn(List.of(fixture));
    when(apiConnector.getRounds("39", "2026")).thenReturn(List.of(
        roundDto("Regular Season - 1"),
        roundDto("Regular Season - 2")));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

    competitionService.refreshFixtures(season);

    assertThat(season.getRounds()).hasSize(2);
    var createdRound = season.getRounds().stream()
        .filter(round -> round.getType() == RoundType.SEASON && round.getOrderNumber() == 2)
        .findFirst()
        .orElseThrow();
    assertThat(createdRound.getOrderNumber()).isEqualTo(2);
    assertThat(createdRound.getMatches()).hasSize(1);
    verify(apiConnectorService).upsertId(
        "api-football",
        ApiConnectorEntityType.MATCH,
        "100",
        createdRound.getMatches().get(0).getId());
    verify(apiConnector).getRounds("39", "2026");
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshFixturesMovesExistingMatchToConnectorRoundWithoutDuplicatingIt() {
    var season = season();
    var firstRound = round(season, "Regular Season - 1");
    var secondRound = round(season, "Regular Season - 2");
    secondRound.setOrderNumber(2);
    var match = existingMatch(firstRound, 100L, "Arsenal", "Chelsea");
    firstRound.setMatches(new ArrayList<>(List.of(match)));
    secondRound.setMatches(new ArrayList<>());
    season.setRounds(new ArrayList<>(List.of(firstRound, secondRound)));

    var fixture = fixture(
        100L,
        "Regular Season - 2",
        OffsetDateTime.of(2026, 5, 28, 18, 30, 0, 0, ZoneOffset.UTC),
        FixtureSyncStatus.PLANNED,
        team(1L, "Arsenal", "arsenal.png"),
        team(2L, "Chelsea", "chelsea.png"),
        null,
        null);

    when(apiConnector.getSeasonFixtures("39", "2026")).thenReturn(List.of(fixture));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

    competitionService.refreshFixtures(season);

    assertThat(firstRound.getMatches()).isEmpty();
    assertThat(secondRound.getMatches()).containsExactly(match);
    verify(apiConnectorService).upsertId("api-football", ApiConnectorEntityType.MATCH, "100", match.getId());
    assertThat(season.getRounds().stream().flatMap(round -> round.getMatches().stream()).toList()).hasSize(1);
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshFixturesKeepsExistingRoundWhenConnectorRoundMapsToMultipleInternalRounds() {
    var season = season();
    var firstQualifyingRound = qualifyingRound(season);
    var secondQualifyingRound = qualifyingRound(season);
    firstQualifyingRound.setMatches(new ArrayList<>());
    var match = existingMatch(secondQualifyingRound, 100L, "KuPS", "Milsami Orhei");
    secondQualifyingRound.setMatches(new ArrayList<>(List.of(match)));
    season.setRounds(new ArrayList<>(List.of(firstQualifyingRound, secondQualifyingRound)));

    var fixture = fixture(
        100L,
        new RoundSyncDto("1st Qualifying Round", null, List.of(RoundSyncType.QUALIFYING)),
        OffsetDateTime.of(2026, 5, 28, 18, 30, 0, 0, ZoneOffset.UTC),
        FixtureSyncStatus.FINISHED,
        team(1L, "KuPS", "kups.png"),
        team(2L, "Milsami Orhei", "milsami.png"),
        2,
        0);

    when(apiConnector.getSeasonFixtures("39", "2026")).thenReturn(List.of(fixture));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

    competitionService.refreshFixtures(season);

    assertThat(match.getRound()).isEqualTo(secondQualifyingRound);
    assertThat(firstQualifyingRound.getMatches()).isEmpty();
    assertThat(secondQualifyingRound.getMatches()).containsExactly(match);
    assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshFixturesMapsConnectorStatusesToMatchStatuses() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var planned = existingMatch(round, 100L, "Arsenal", "Chelsea");
    var started = existingMatch(round, 101L, "Liverpool", "Everton");
    var finished = existingMatch(round, 102L, "Brighton", "Fulham");
    var notDefined = existingMatch(round, 103L, "Leeds", "Burnley");
    round.setMatches(new ArrayList<>(List.of(planned, started, finished, notDefined)));
    season.setRounds(new ArrayList<>(List.of(round)));

    var kickoff = OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC);
    when(apiConnector.getSeasonFixtures("39", "2026")).thenReturn(List.of(
        fixture(100L, "Regular Season - 1", kickoff, FixtureSyncStatus.PLANNED, team(1L, "Arsenal", null), team(2L, "Chelsea", null), null, null),
        fixture(101L, "Regular Season - 1", kickoff, FixtureSyncStatus.STARTED, team(1L, "Liverpool", null), team(2L, "Everton", null), 1, 0),
        fixture(102L, "Regular Season - 1", kickoff, FixtureSyncStatus.FINISHED, team(1L, "Brighton", null), team(2L, "Fulham", null), 2, 1),
        fixture(103L, "Regular Season - 1", kickoff, FixtureSyncStatus.NOT_DEFINED, team(1L, "Leeds", null), team(2L, "Burnley", null), null, null)));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

    competitionService.refreshFixtures(season);

    assertThat(planned.getStatus()).isEqualTo(MatchStatus.PLANNED);
    assertThat(planned.getStartTime()).isEqualTo(kickoff.toInstant());
    assertThat(started.getStatus()).isEqualTo(MatchStatus.STARTED);
    assertThat(finished.getStatus()).isEqualTo(MatchStatus.FINISHED);
    assertThat(notDefined.getStatus()).isEqualTo(MatchStatus.NOT_DEFINED);
    assertThat(notDefined.getStartTime()).isNull();
    verify(seasonRepository).save(season);
  }

  private SeasonEntity season() {
    var competition = new CompetitionEntity();
    competition.setId(UUID.randomUUID());
    competition.setName("Premier League");
    lenient().when(apiConnectorService.requireConnectorEntityId(
        "api-football",
        ApiConnectorEntityType.COMPETITION,
        competition.getId()))
        .thenReturn("39");

    var season = new SeasonEntity();
    season.setId(UUID.randomUUID());
    season.setCompetition(competition);
    season.setYear("2026");
    return season;
  }

  private RoundEntity round(SeasonEntity season, String roundName) {
    var round = new RoundEntity();
    round.setId(UUID.randomUUID());
    round.setSeason(season);
    round.setType(RoundType.SEASON);
    round.setOrderNumber(orderNumber(roundName));
    return round;
  }

  private RoundEntity qualifyingRound(SeasonEntity season) {
    var round = round(season, "Regular Season - 1");
    round.setType(RoundType.QUALIFYING);
    round.setOrderNumber(0);
    return round;
  }

  private int orderNumber(String roundName) {
    if (roundName.endsWith(" - 2")) {
      return 2;
    }
    return 1;
  }

  private MatchEntity existingMatch(RoundEntity round, Long connectorMatchId, String home, String away) {
    var match = new MatchEntity();
    match.setId(UUID.randomUUID());
    match.setRound(round);
    match.setHomeTeam(teamEntity(1L, home, home + ".png"));
    match.setAwayTeam(teamEntity(2L, away, away + ".png"));
    match.setStatus(MatchStatus.PLANNED);
    lenient().when(apiConnectorService.findInternalId(
        "api-football",
        ApiConnectorEntityType.MATCH,
        connectorMatchId.toString()))
        .thenReturn(Optional.of(match.getId()));
    lenient().when(apiConnectorService.requireConnectorEntityId(
        "api-football",
        ApiConnectorEntityType.MATCH,
        match.getId()))
        .thenReturn(connectorMatchId.toString());
    return match;
  }

  private TeamEntity teamEntity(Long connectorTeamId, String name, String logoUrl) {
    var team = new TeamEntity();
    team.setId(UUID.randomUUID());
    team.setName(name);
    team.setLogoUrl(logoUrl);
    lenient().when(apiConnectorService.findInternalId(
        "api-football",
        ApiConnectorEntityType.TEAM,
        connectorTeamId.toString()))
        .thenReturn(Optional.of(team.getId()));
    lenient().when(teamRepository.findById(team.getId())).thenReturn(Optional.of(team));
    return team;
  }

  private FixtureSyncDto fixture(
      Long id,
      String round,
      OffsetDateTime kickoff,
      FixtureSyncStatus status,
      TeamSyncDto homeTeam,
      TeamSyncDto awayTeam,
      Integer homeScore,
      Integer awayScore) {
    return new FixtureSyncDto(
        id.toString(),
        roundDto(round),
        kickoff == null ? null : kickoff.toInstant(),
        status,
        homeTeam,
        awayTeam,
        new ScoreSyncDto(homeScore, awayScore));
  }

  private FixtureSyncDto fixture(
      Long id,
      RoundSyncDto round,
      OffsetDateTime kickoff,
      FixtureSyncStatus status,
      TeamSyncDto homeTeam,
      TeamSyncDto awayTeam,
      Integer homeScore,
      Integer awayScore) {
    return new FixtureSyncDto(
        id.toString(),
        round,
        kickoff == null ? null : kickoff.toInstant(),
        status,
        homeTeam,
        awayTeam,
        new ScoreSyncDto(homeScore, awayScore));
  }

  private RoundSyncDto roundDto(String round) {
    return new RoundSyncDto(round, orderNumber(round), List.of(RoundSyncType.SEASON));
  }

  private TeamSyncDto team(Long id, String name, String logo) {
    return new TeamSyncDto(id.toString(), name, logo);
  }
}

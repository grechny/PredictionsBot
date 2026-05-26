package at.hrechny.predictionsbot.service.predictor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.football.FootballDataProvider;
import at.hrechny.predictionsbot.connector.football.FootballDataProviderException;
import at.hrechny.predictionsbot.connector.football.FootballDataProviderException.Reason;
import at.hrechny.predictionsbot.connector.football.model.FootballFixtureStatus;
import at.hrechny.predictionsbot.connector.football.model.FootballFixtureSyncDto;
import at.hrechny.predictionsbot.connector.football.model.FootballRoundSyncDto;
import at.hrechny.predictionsbot.connector.football.model.FootballScoreSyncDto;
import at.hrechny.predictionsbot.connector.football.model.FootballTeamSyncDto;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.TeamEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.model.RoundType;
import at.hrechny.predictionsbot.database.repository.MatchRepository;
import at.hrechny.predictionsbot.database.repository.SeasonRepository;
import at.hrechny.predictionsbot.database.repository.TeamRepository;
import at.hrechny.predictionsbot.model.ExternalApiProviderId;
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
  private FootballDataProvider footballDataProvider;

  @Mock
  private ProviderExternalIdService providerExternalIdService;

  private CompetitionService competitionService;

  @BeforeEach
  void setUp() {
    lenient().when(footballDataProvider.getProviderId())
        .thenReturn(new ExternalApiProviderId("api-football"));
    competitionService = new CompetitionService(
        null,
        seasonRepository,
        null,
        null,
        teamRepository,
        matchRepository,
        footballDataProvider,
        providerExternalIdService);
  }

  @Test
  void refreshActiveFixturesDoesNotCallProviderWhenNoActiveMatchesExist() {
    var season = season();
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of());

    competitionService.refreshActiveFixtures(season.getId());

    verifyNoInteractions(footballDataProvider);
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
        FootballFixtureStatus.FINISHED,
        team(1L, "Arsenal", "arsenal-new.png"),
        team(2L, "Chelsea", "chelsea-new.png"),
        2,
        1);

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(match));
    when(footballDataProvider.getFixturesByExternalIds(List.of("100"))).thenReturn(List.of(fixture));

    competitionService.refreshActiveFixtures(season.getId());

    assertThat(match.getStatus()).isEqualTo(MatchStatus.FINISHED);
    assertThat(match.getHomeTeamScore()).isEqualTo(2);
    assertThat(match.getAwayTeamScore()).isEqualTo(1);
    assertThat(match.getStartTime()).isEqualTo(kickoff.toInstant());
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshActiveFixturesLeavesExistingDataWhenProviderFails() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var match = existingMatch(round, 100L, "Arsenal", "Chelsea");
    round.setMatches(List.of(match));
    season.setRounds(List.of(round));

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(match));
    when(footballDataProvider.getFixturesByExternalIds(anyList())).thenThrow(
        new FootballDataProviderException("api-football", Reason.QUOTA_EXCEEDED, null, null));

    competitionService.refreshActiveFixtures(season.getId());

    assertThat(match.getStatus()).isEqualTo(MatchStatus.PLANNED);
    assertThat(match.getHomeTeamScore()).isNull();
    assertThat(match.getAwayTeamScore()).isNull();
    verify(seasonRepository, never()).save(season);
  }

  @Test
  void refreshActiveFixturesEmptyProviderResponseLeavesExistingDataAndSavesSeason() {
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
    when(footballDataProvider.getFixturesByExternalIds(List.of("100"))).thenReturn(List.of());

    competitionService.refreshActiveFixtures(season.getId());

    assertThat(match.getStatus()).isEqualTo(MatchStatus.STARTED);
    assertThat(match.getHomeTeamScore()).isEqualTo(1);
    assertThat(match.getAwayTeamScore()).isEqualTo(0);
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshFixturesCreatesTeamsAndMatchForNewProviderFixture() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    season.setRounds(List.of(round));

    var homeTeam = team(1L, "Arsenal", "arsenal.png");
    var awayTeam = team(2L, "Chelsea", "chelsea.png");
    var fixture = fixture(
        100L,
        "Regular Season - 1",
        OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC),
        FootballFixtureStatus.PLANNED,
        homeTeam,
        awayTeam,
        null,
        null);

    when(footballDataProvider.getSeasonFixtures("39", "2026")).thenReturn(List.of(fixture));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(teamRepository.findFirstByApiFootballId(1L)).thenReturn(Optional.empty());
    when(teamRepository.findFirstByApiFootballId(2L)).thenReturn(Optional.empty());
    when(teamRepository.save(any(TeamEntity.class))).thenAnswer(invocation -> {
      var team = invocation.getArgument(0, TeamEntity.class);
      team.setId(UUID.randomUUID());
      return team;
    });

    competitionService.refreshFixtures(season);

    assertThat(round.getMatches()).hasSize(1);
    var createdMatch = round.getMatches().get(0);
    assertThat(createdMatch.getApiFootballId()).isEqualTo(100L);
    assertThat(createdMatch.getHomeTeam().getApiFootballId()).isEqualTo(1L);
    assertThat(createdMatch.getHomeTeam().getName()).isEqualTo("Arsenal");
    assertThat(createdMatch.getAwayTeam().getApiFootballId()).isEqualTo(2L);
    assertThat(createdMatch.getAwayTeam().getName()).isEqualTo("Chelsea");
    assertThat(createdMatch.getStatus()).isEqualTo(MatchStatus.PLANNED);
    assertThat(createdMatch.getStartTime()).isNotNull();
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshFixturesCreatesMissingRoundWhenProviderReturnsUnknownRound() {
    var season = season();
    var existingRound = round(season, "Regular Season - 1");
    existingRound.setMatches(new ArrayList<>());
    season.setRounds(new ArrayList<>(List.of(existingRound)));

    var homeTeam = team(1L, "Arsenal", "arsenal.png");
    var awayTeam = team(2L, "Chelsea", "chelsea.png");
    var fixture = fixture(
        100L,
        "Regular Season - 2",
        OffsetDateTime.of(2026, 5, 28, 18, 30, 0, 0, ZoneOffset.UTC),
        FootballFixtureStatus.PLANNED,
        homeTeam,
        awayTeam,
        null,
        null);

    when(footballDataProvider.getSeasonFixtures("39", "2026")).thenReturn(List.of(fixture));
    when(footballDataProvider.getRounds("39", "2026")).thenReturn(List.of(
        roundDto("Regular Season - 1"),
        roundDto("Regular Season - 2")));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(teamRepository.findFirstByApiFootballId(1L)).thenReturn(Optional.empty());
    when(teamRepository.findFirstByApiFootballId(2L)).thenReturn(Optional.empty());
    when(teamRepository.save(any(TeamEntity.class))).thenAnswer(invocation -> {
      var team = invocation.getArgument(0, TeamEntity.class);
      team.setId(UUID.randomUUID());
      return team;
    });

    competitionService.refreshFixtures(season);

    assertThat(season.getRounds()).hasSize(2);
    var createdRound = season.getRounds().stream()
        .filter(round -> "Regular Season - 2".equals(round.getApiFootballId()))
        .findFirst()
        .orElseThrow();
    assertThat(createdRound.getOrderNumber()).isEqualTo(2);
    assertThat(createdRound.getMatches()).hasSize(1);
    assertThat(createdRound.getMatches().get(0).getApiFootballId()).isEqualTo(100L);
    verify(footballDataProvider).getRounds("39", "2026");
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshFixturesMovesExistingMatchToProviderRoundWithoutDuplicatingIt() {
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
        FootballFixtureStatus.PLANNED,
        team(1L, "Arsenal", "arsenal.png"),
        team(2L, "Chelsea", "chelsea.png"),
        null,
        null);

    when(footballDataProvider.getSeasonFixtures("39", "2026")).thenReturn(List.of(fixture));
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));

    competitionService.refreshFixtures(season);

    assertThat(firstRound.getMatches()).isEmpty();
    assertThat(secondRound.getMatches()).containsExactly(match);
    assertThat(secondRound.getMatches().get(0).getApiFootballId()).isEqualTo(100L);
    assertThat(season.getRounds().stream().flatMap(round -> round.getMatches().stream()).toList()).hasSize(1);
    verify(seasonRepository).save(season);
  }

  @Test
  void refreshFixturesMapsProviderStatusesToMatchStatuses() {
    var season = season();
    var round = round(season, "Regular Season - 1");
    var planned = existingMatch(round, 100L, "Arsenal", "Chelsea");
    var started = existingMatch(round, 101L, "Liverpool", "Everton");
    var finished = existingMatch(round, 102L, "Brighton", "Fulham");
    var notDefined = existingMatch(round, 103L, "Leeds", "Burnley");
    round.setMatches(new ArrayList<>(List.of(planned, started, finished, notDefined)));
    season.setRounds(new ArrayList<>(List.of(round)));

    var kickoff = OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC);
    when(footballDataProvider.getSeasonFixtures("39", "2026")).thenReturn(List.of(
        fixture(100L, "Regular Season - 1", kickoff, FootballFixtureStatus.PLANNED, team(1L, "Arsenal", null), team(2L, "Chelsea", null), null, null),
        fixture(101L, "Regular Season - 1", kickoff, FootballFixtureStatus.STARTED, team(3L, "Liverpool", null), team(4L, "Everton", null), 1, 0),
        fixture(102L, "Regular Season - 1", kickoff, FootballFixtureStatus.FINISHED, team(5L, "Brighton", null), team(6L, "Fulham", null), 2, 1),
        fixture(103L, "Regular Season - 1", kickoff, FootballFixtureStatus.NOT_DEFINED, team(7L, "Leeds", null), team(8L, "Burnley", null), null, null)));
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
    competition.setApiFootballId(39L);

    var season = new SeasonEntity();
    season.setId(UUID.randomUUID());
    season.setCompetition(competition);
    season.setYear("2026");
    return season;
  }

  private RoundEntity round(SeasonEntity season, String apiFootballId) {
    var round = new RoundEntity();
    round.setId(UUID.randomUUID());
    round.setSeason(season);
    round.setType(RoundType.SEASON);
    round.setOrderNumber(1);
    round.setApiFootballId(apiFootballId);
    return round;
  }

  private MatchEntity existingMatch(RoundEntity round, Long apiFootballId, String home, String away) {
    var match = new MatchEntity();
    match.setId(UUID.randomUUID());
    match.setRound(round);
    match.setApiFootballId(apiFootballId);
    match.setHomeTeam(teamEntity(1L, home, home + ".png"));
    match.setAwayTeam(teamEntity(2L, away, away + ".png"));
    match.setStatus(MatchStatus.PLANNED);
    return match;
  }

  private TeamEntity teamEntity(Long apiFootballId, String name, String logoUrl) {
    var team = new TeamEntity();
    team.setId(UUID.randomUUID());
    team.setApiFootballId(apiFootballId);
    team.setName(name);
    team.setLogoUrl(logoUrl);
    return team;
  }

  private FootballFixtureSyncDto fixture(
      Long id,
      String round,
      OffsetDateTime kickoff,
      FootballFixtureStatus status,
      FootballTeamSyncDto homeTeam,
      FootballTeamSyncDto awayTeam,
      Integer homeScore,
      Integer awayScore) {
    return new FootballFixtureSyncDto(
        id.toString(),
        round,
        kickoff == null ? null : kickoff.toInstant(),
        status,
        homeTeam,
        awayTeam,
        new FootballScoreSyncDto(homeScore, awayScore));
  }

  private FootballRoundSyncDto roundDto(String round) {
    return new FootballRoundSyncDto(round, round, null, null);
  }

  private FootballTeamSyncDto team(Long id, String name, String logo) {
    return new FootballTeamSyncDto(id.toString(), name, logo);
  }
}

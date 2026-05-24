package at.hrechny.predictionsbot.service.predictor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.apifootball.ApiFootballConnector;
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException;
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException.Reason;
import at.hrechny.predictionsbot.connector.apifootball.model.Fixture;
import at.hrechny.predictionsbot.connector.apifootball.model.FixtureData;
import at.hrechny.predictionsbot.connector.apifootball.model.FixtureStatusEnum;
import at.hrechny.predictionsbot.connector.apifootball.model.LeagueData;
import at.hrechny.predictionsbot.connector.apifootball.model.Score;
import at.hrechny.predictionsbot.connector.apifootball.model.ScoreDetail;
import at.hrechny.predictionsbot.connector.apifootball.model.Status;
import at.hrechny.predictionsbot.connector.apifootball.model.Team;
import at.hrechny.predictionsbot.connector.apifootball.model.TeamsData;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
  private ApiFootballConnector apiFootballConnector;

  private CompetitionService competitionService;

  @BeforeEach
  void setUp() {
    competitionService = new CompetitionService(
        null,
        seasonRepository,
        null,
        null,
        teamRepository,
        matchRepository,
        apiFootballConnector);
  }

  @Test
  void refreshActiveFixturesDoesNotCallProviderWhenNoActiveMatchesExist() {
    var season = season();
    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of());

    competitionService.refreshActiveFixtures(season.getId());

    verifyNoInteractions(apiFootballConnector);
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
        FixtureStatusEnum.FT,
        team(1L, "Arsenal", "arsenal-new.png"),
        team(2L, "Chelsea", "chelsea-new.png"),
        2,
        1);

    when(seasonRepository.findById(season.getId())).thenReturn(Optional.of(season));
    when(matchRepository.findAllActive(season)).thenReturn(List.of(match));
    when(apiFootballConnector.getFixtures(List.of(100L))).thenReturn(List.of(fixture));

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
    when(apiFootballConnector.getFixtures(anyList())).thenThrow(new ApiFootballConnectorException(Reason.QUOTA_EXCEEDED));

    competitionService.refreshActiveFixtures(season.getId());

    assertThat(match.getStatus()).isEqualTo(MatchStatus.PLANNED);
    assertThat(match.getHomeTeamScore()).isNull();
    assertThat(match.getAwayTeamScore()).isNull();
    verify(seasonRepository, never()).save(season);
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
        FixtureStatusEnum.NS,
        homeTeam,
        awayTeam,
        null,
        null);

    when(apiFootballConnector.getFixtures(39L, "2026")).thenReturn(List.of(fixture));
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

  private Fixture fixture(
      Long id,
      String round,
      OffsetDateTime kickoff,
      FixtureStatusEnum status,
      Team homeTeam,
      Team awayTeam,
      Integer homeScore,
      Integer awayScore) {
    var fixture = new Fixture();
    fixture.setFixture(fixtureData(id, kickoff, status));
    fixture.setLeague(leagueData(round));
    fixture.setTeams(teamsData(homeTeam, awayTeam));
    fixture.setGoals(score(homeScore, awayScore));

    var scoreDetail = new ScoreDetail();
    scoreDetail.setFulltime(score(homeScore, awayScore));
    fixture.setScore(scoreDetail);
    return fixture;
  }

  private FixtureData fixtureData(Long id, OffsetDateTime kickoff, FixtureStatusEnum status) {
    var fixtureData = new FixtureData();
    fixtureData.setId(id);
    fixtureData.setDate(kickoff);
    fixtureData.setStatus(status(status));
    return fixtureData;
  }

  private LeagueData leagueData(String round) {
    var leagueData = new LeagueData();
    leagueData.setId(39L);
    leagueData.setName("Premier League");
    leagueData.setRound(round);
    return leagueData;
  }

  private TeamsData teamsData(Team homeTeam, Team awayTeam) {
    var teamsData = new TeamsData();
    teamsData.setHome(homeTeam);
    teamsData.setAway(awayTeam);
    return teamsData;
  }

  private Team team(Long id, String name, String logo) {
    var team = new Team();
    team.setId(id);
    team.setName(name);
    team.setLogo(logo);
    return team;
  }

  private Status status(FixtureStatusEnum statusEnum) {
    var status = new Status();
    status.setStatus(statusEnum);
    return status;
  }

  private Score score(Integer home, Integer away) {
    var score = new Score();
    score.setHome(home);
    score.setAway(away);
    return score;
  }
}

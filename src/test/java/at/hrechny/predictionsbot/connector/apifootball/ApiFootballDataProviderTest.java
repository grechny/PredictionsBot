package at.hrechny.predictionsbot.connector.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import at.hrechny.predictionsbot.connector.FootballDataProviderException;
import at.hrechny.predictionsbot.model.FootballDataType;
import at.hrechny.predictionsbot.model.FootballFixtureStatus;
import at.hrechny.predictionsbot.model.FootballFixtureSyncDto;
import at.hrechny.predictionsbot.model.FootballFreshness;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFootballDataProviderTest {

  @Mock
  private ApiFootballConnector apiFootballConnector;

  private ApiFootballDataProvider provider;

  @BeforeEach
  void setUp() {
    provider = new ApiFootballDataProvider(apiFootballConnector, new ApiFootballFixtureMapper());
  }

  @Test
  void capabilitiesExposeApiFootballProviderFeatures() {
    var capabilities = provider.getCapabilities();

    assertThat(provider.providerCode()).isEqualTo("api-football");
    assertThat(capabilities.getDataTypes()).containsExactlyInAnyOrder(
        FootballDataType.ROUNDS,
        FootballDataType.SEASON_FIXTURES,
        FootballDataType.LIVE_FIXTURES,
        FootballDataType.SCORES,
        FootballDataType.TEAMS,
        FootballDataType.PROVIDER_METADATA);
    assertThat(capabilities.getFreshness()).isEqualTo(FootballFreshness.NEAR_REAL_TIME);
    assertThat(capabilities.getSupportsLiveScores()).isTrue();
  }

  @Test
  void getRoundsMapsRoundNamesToProviderNeutralDtos() {
    when(apiFootballConnector.getRounds(39L, "2026")).thenReturn(List.of("Regular Season - 1", "Final"));

    var rounds = provider.getRounds("39", "2026");

    assertThat(rounds).hasSize(2);
    assertThat(rounds.get(0).getExternalId()).isEqualTo("Regular Season - 1");
    assertThat(rounds.get(0).getName()).isEqualTo("Regular Season - 1");
    assertThat(rounds.get(1).getExternalId()).isEqualTo("Final");
    verify(apiFootballConnector).getRounds(39L, "2026");
  }

  @Test
  void getSeasonFixturesMapsRawFixtureToProviderNeutralDto() {
    var kickoff = OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC);
    when(apiFootballConnector.getFixtures(39L, "2026")).thenReturn(List.of(
        fixture(
            100L,
            "Regular Season - 1",
            kickoff,
            FixtureStatusEnum.FT,
            team(1L, "Arsenal", "arsenal.png"),
            team(2L, "Chelsea", "chelsea.png"),
            score(2, 1),
            score(9, 9))));

    var fixtures = provider.getSeasonFixtures("39", "2026");

    assertThat(fixtures).hasSize(1);
    var mappedFixture = fixtures.get(0);
    assertThat(mappedFixture.getExternalId()).isEqualTo("100");
    assertThat(mappedFixture.getRoundExternalId()).isEqualTo("Regular Season - 1");
    assertThat(mappedFixture.getStartTime()).isEqualTo(kickoff.toInstant());
    assertThat(mappedFixture.getStatus()).isEqualTo(FootballFixtureStatus.FINISHED);
    assertThat(mappedFixture.getHomeTeam().getExternalId()).isEqualTo("1");
    assertThat(mappedFixture.getHomeTeam().getName()).isEqualTo("Arsenal");
    assertThat(mappedFixture.getHomeTeam().getLogoUrl()).isEqualTo("arsenal.png");
    assertThat(mappedFixture.getAwayTeam().getExternalId()).isEqualTo("2");
    assertThat(mappedFixture.getScore().getHome()).isEqualTo(2);
    assertThat(mappedFixture.getScore().getAway()).isEqualTo(1);
  }

  @Test
  void getSeasonFixturesFallsBackToGoalsWhenFulltimeScoreIsMissing() {
    when(apiFootballConnector.getFixtures(39L, "2026")).thenReturn(List.of(
        fixture(
            100L,
            "Regular Season - 1",
            OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC),
            FixtureStatusEnum.LIVE,
            team(1L, "Arsenal", null),
            team(2L, "Chelsea", null),
            score(null, null),
            score(1, 0))));

    var fixtures = provider.getSeasonFixtures("39", "2026");

    assertThat(fixtures.get(0).getScore().getHome()).isEqualTo(1);
    assertThat(fixtures.get(0).getScore().getAway()).isEqualTo(0);
  }

  @Test
  void getSeasonFixturesMapsApiFootballStatusesToProviderNeutralStatuses() {
    var kickoff = OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC);
    when(apiFootballConnector.getFixtures(39L, "2026")).thenReturn(List.of(
        fixture(100L, "Regular Season - 1", kickoff, FixtureStatusEnum.NS, team(1L, "A", null), team(2L, "B", null), score(null, null), score(null, null)),
        fixture(101L, "Regular Season - 1", kickoff, FixtureStatusEnum.LIVE, team(3L, "C", null), team(4L, "D", null), score(null, null), score(null, null)),
        fixture(102L, "Regular Season - 1", kickoff, FixtureStatusEnum.FT, team(5L, "E", null), team(6L, "F", null), score(2, 1), score(2, 1)),
        fixture(103L, "Regular Season - 1", kickoff, FixtureStatusEnum.TBD, team(7L, "G", null), team(8L, "H", null), score(null, null), score(null, null))));

    var fixtures = provider.getSeasonFixtures("39", "2026");

    assertThat(fixtures)
        .extracting(FootballFixtureSyncDto::getStatus)
        .containsExactly(
            FootballFixtureStatus.PLANNED,
            FootballFixtureStatus.STARTED,
            FootballFixtureStatus.FINISHED,
            FootballFixtureStatus.NOT_DEFINED);
  }

  @Test
  void getFixturesByExternalIdsRequestsNumericFixtureIdsAndMapsResponses() {
    when(apiFootballConnector.getFixtures(List.of(100L))).thenReturn(List.of(
        fixture(
            100L,
            "Regular Season - 1",
            OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC),
            FixtureStatusEnum.NS,
            team(1L, "Arsenal", null),
            team(2L, "Chelsea", null),
            score(null, null),
            score(null, null))));

    var fixtures = provider.getFixturesByExternalIds(List.of("100"));

    assertThat(fixtures).hasSize(1);
    assertThat(fixtures.get(0).getExternalId()).isEqualTo("100");
    assertThat(fixtures.get(0).getStatus()).isEqualTo(FootballFixtureStatus.PLANNED);
    verify(apiFootballConnector).getFixtures(List.of(100L));
  }

  @Test
  void connectorExceptionsAreTranslatedToProviderNeutralExceptions() {
    when(apiFootballConnector.getRounds(39L, "2026")).thenThrow(
        new ApiFootballConnectorException(Reason.QUOTA_EXCEEDED, null, null));

    assertThatThrownBy(() -> provider.getRounds("39", "2026"))
        .isInstanceOfSatisfying(FootballDataProviderException.class, exception -> {
          assertThat(exception.getReason()).isEqualTo(FootballDataProviderException.Reason.QUOTA_EXCEEDED);
          assertThat(exception.getMessage()).contains("api-football");
        });
  }

  private Fixture fixture(
      Long id,
      String round,
      OffsetDateTime kickoff,
      FixtureStatusEnum status,
      Team homeTeam,
      Team awayTeam,
      Score fulltimeScore,
      Score goals) {
    var fixture = new Fixture();
    fixture.setFixture(fixtureData(id, kickoff, status));
    fixture.setLeague(leagueData(round));
    fixture.setTeams(teamsData(homeTeam, awayTeam));
    fixture.setGoals(goals);

    var scoreDetail = new ScoreDetail();
    scoreDetail.setFulltime(fulltimeScore);
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

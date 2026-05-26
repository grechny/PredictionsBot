package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.model.FixtureSyncDto;
import at.hrechny.predictionsbot.connector.model.FixtureSyncStatus;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Fixture;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixtureData;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixtureStatusEnum;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.LeagueData;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Score;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.ScoreDetail;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Status;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Team;
import at.hrechny.predictionsbot.connector.impl.apifootball.model.TeamsData;
import at.hrechny.predictionsbot.exception.ApiConnectorException;
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFootballConnectorTest {

  @Mock
  private ApiFootballClient apiFootballClient;

  private ApiFootballConnector connector;

  @BeforeEach
  void setUp() {
    connector = new ApiFootballConnector(apiFootballClient, new ApiFootballFixtureMapper());
  }

  @Test
  void exposesApiFootballConnectorCode() {
    assertThat(connector.getCode()).isEqualTo("api-football");
  }

  @Test
  void getRoundsMapsRoundNamesToConnectorDtos() {
    when(apiFootballClient.getRounds(39L, "2026")).thenReturn(List.of("Regular Season - 1", "Final"));

    var rounds = connector.getRounds("39", "2026");

    assertThat(rounds).hasSize(2);
    assertThat(rounds.get(0).getExternalId()).isEqualTo("Regular Season - 1");
    assertThat(rounds.get(0).getName()).isEqualTo("Regular Season - 1");
    assertThat(rounds.get(1).getExternalId()).isEqualTo("Final");
    verify(apiFootballClient).getRounds(39L, "2026");
  }

  @Test
  void getSeasonFixturesMapsRawFixtureToConnectorDto() {
    var kickoff = OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC);
    when(apiFootballClient.getFixtures(39L, "2026")).thenReturn(List.of(
        fixture(
            100L,
            "Regular Season - 1",
            kickoff,
            FixtureStatusEnum.FT,
            team(1L, "Arsenal", "arsenal.png"),
            team(2L, "Chelsea", "chelsea.png"),
            score(2, 1),
            score(9, 9))));

    var fixtures = connector.getSeasonFixtures("39", "2026");

    assertThat(fixtures).hasSize(1);
    var mappedFixture = fixtures.get(0);
    assertThat(mappedFixture.getExternalId()).isEqualTo("100");
    assertThat(mappedFixture.getRoundExternalId()).isEqualTo("Regular Season - 1");
    assertThat(mappedFixture.getStartTime()).isEqualTo(kickoff.toInstant());
    assertThat(mappedFixture.getStatus()).isEqualTo(FixtureSyncStatus.FINISHED);
    assertThat(mappedFixture.getHomeTeam().getExternalId()).isEqualTo("1");
    assertThat(mappedFixture.getHomeTeam().getName()).isEqualTo("Arsenal");
    assertThat(mappedFixture.getHomeTeam().getLogoUrl()).isEqualTo("arsenal.png");
    assertThat(mappedFixture.getAwayTeam().getExternalId()).isEqualTo("2");
    assertThat(mappedFixture.getScore().getHome()).isEqualTo(2);
    assertThat(mappedFixture.getScore().getAway()).isEqualTo(1);
  }

  @Test
  void getSeasonFixturesFallsBackToGoalsWhenFulltimeScoreIsMissing() {
    when(apiFootballClient.getFixtures(39L, "2026")).thenReturn(List.of(
        fixture(
            100L,
            "Regular Season - 1",
            OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC),
            FixtureStatusEnum.LIVE,
            team(1L, "Arsenal", null),
            team(2L, "Chelsea", null),
            score(null, null),
            score(1, 0))));

    var fixtures = connector.getSeasonFixtures("39", "2026");

    assertThat(fixtures.get(0).getScore().getHome()).isEqualTo(1);
    assertThat(fixtures.get(0).getScore().getAway()).isEqualTo(0);
  }

  @Test
  void getSeasonFixturesMapsApiFootballStatusesToConnectorStatuses() {
    var kickoff = OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC);
    when(apiFootballClient.getFixtures(39L, "2026")).thenReturn(List.of(
        fixture(100L, "Regular Season - 1", kickoff, FixtureStatusEnum.NS, team(1L, "A", null), team(2L, "B", null), score(null, null), score(null, null)),
        fixture(101L, "Regular Season - 1", kickoff, FixtureStatusEnum.LIVE, team(3L, "C", null), team(4L, "D", null), score(null, null), score(null, null)),
        fixture(102L, "Regular Season - 1", kickoff, FixtureStatusEnum.FT, team(5L, "E", null), team(6L, "F", null), score(2, 1), score(2, 1)),
        fixture(103L, "Regular Season - 1", kickoff, FixtureStatusEnum.TBD, team(7L, "G", null), team(8L, "H", null), score(null, null), score(null, null))));

    var fixtures = connector.getSeasonFixtures("39", "2026");

    assertThat(fixtures)
        .extracting(FixtureSyncDto::getStatus)
        .containsExactly(
            FixtureSyncStatus.PLANNED,
            FixtureSyncStatus.STARTED,
            FixtureSyncStatus.FINISHED,
            FixtureSyncStatus.NOT_DEFINED);
  }

  @Test
  void getFixturesByExternalIdsRequestsNumericFixtureIdsAndMapsResponses() {
    when(apiFootballClient.getFixtures(List.of(100L))).thenReturn(List.of(
        fixture(
            100L,
            "Regular Season - 1",
            OffsetDateTime.of(2026, 5, 21, 18, 30, 0, 0, ZoneOffset.UTC),
            FixtureStatusEnum.NS,
            team(1L, "Arsenal", null),
            team(2L, "Chelsea", null),
            score(null, null),
            score(null, null))));

    var fixtures = connector.getFixturesByExternalIds(List.of("100"));

    assertThat(fixtures).hasSize(1);
    assertThat(fixtures.get(0).getExternalId()).isEqualTo("100");
    assertThat(fixtures.get(0).getStatus()).isEqualTo(FixtureSyncStatus.PLANNED);
    verify(apiFootballClient).getFixtures(List.of(100L));
  }

  @Test
  void clientConnectorExceptionsArePropagated() {
    when(apiFootballClient.getRounds(39L, "2026")).thenThrow(
        new ApiConnectorException("api-football", Reason.QUOTA_EXCEEDED, null, null));

    assertThatThrownBy(() -> connector.getRounds("39", "2026"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception -> {
          assertThat(exception.getReason()).isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED);
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

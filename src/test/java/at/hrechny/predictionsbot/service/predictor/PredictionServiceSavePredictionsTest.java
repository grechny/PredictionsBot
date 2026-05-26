package at.hrechny.predictionsbot.service.predictor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.PredictionEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.TeamEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.repository.MatchRepository;
import at.hrechny.predictionsbot.exception.RequestValidationException;
import at.hrechny.predictionsbot.mapper.UserMapper;
import at.hrechny.predictionsbot.controller.model.prediction.PredictionRequestDto;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PredictionServiceSavePredictionsTest {

  private static final Long USER_ID = 42L;

  @Mock
  private UserMapper userMapper;

  @Mock
  private MatchRepository matchRepository;

  @Mock
  private UserService userService;

  private PredictionService predictionService;
  private UserEntity user;

  @BeforeEach
  void setUp() {
    predictionService = new PredictionService(userMapper, matchRepository, null, userService, new PredictionResultsCalculator());
    user = new UserEntity();
    user.setId(USER_ID);
  }

  @Test
  void savePredictionsCreatesAndUpdatesPredictionsForFutureRound() {
    when(userService.getUser(USER_ID)).thenReturn(user);
    var round = roundWithSeason(true);
    var firstMatch = match(round, Instant.now().plusSeconds(3600));
    var secondMatch = match(round, Instant.now().plusSeconds(7200));
    var existingPrediction = existingPrediction(firstMatch, user, 0, 0, false);
    round.setMatches(List.of(firstMatch, secondMatch));

    when(matchRepository.findById(firstMatch.getId())).thenReturn(Optional.of(firstMatch));
    when(matchRepository.findById(secondMatch.getId())).thenReturn(Optional.of(secondMatch));

    predictionService.savePredictions(USER_ID, List.of(
        prediction(firstMatch.getId(), 2, 1, true),
        prediction(secondMatch.getId(), 1, 1, false)));

    assertThat(firstMatch.getPredictions()).containsExactly(existingPrediction);
    assertThat(existingPrediction.getPredictionHome()).isEqualTo(2);
    assertThat(existingPrediction.getPredictionAway()).isEqualTo(1);
    assertThat(existingPrediction.isDoubleUp()).isTrue();
    assertThat(existingPrediction.getUpdatedAt()).isNotNull();

    assertThat(secondMatch.getPredictions()).hasSize(1);
    var createdPrediction = secondMatch.getPredictions().get(0);
    assertThat(createdPrediction.getUser()).isEqualTo(user);
    assertThat(createdPrediction.getMatch()).isEqualTo(secondMatch);
    assertThat(createdPrediction.getPredictionHome()).isEqualTo(1);
    assertThat(createdPrediction.getPredictionAway()).isEqualTo(1);
    assertThat(createdPrediction.isDoubleUp()).isFalse();
    assertThat(createdPrediction.getUpdatedAt()).isNotNull();

    verify(matchRepository).save(firstMatch);
    verify(matchRepository).save(secondMatch);
  }

  @Test
  void savePredictionsRequiresExactlyOneDoubleUpForRound() {
    when(userService.getUser(USER_ID)).thenReturn(user);
    var round = roundWithSeason(true);
    var firstMatch = match(round, Instant.now().plusSeconds(3600));
    var secondMatch = match(round, Instant.now().plusSeconds(7200));
    round.setMatches(List.of(firstMatch, secondMatch));

    when(matchRepository.findById(firstMatch.getId())).thenReturn(Optional.of(firstMatch));
    when(matchRepository.findById(secondMatch.getId())).thenReturn(Optional.of(secondMatch));

    assertThatThrownBy(() -> predictionService.savePredictions(USER_ID, List.of(
        prediction(firstMatch.getId(), 2, 1, false),
        prediction(secondMatch.getId(), 1, 1, false))))
        .isInstanceOf(RequestValidationException.class)
        .hasMessage("User has no/more than one double up for for the round");
  }

  @Test
  void savePredictionsSkipsMatchesThatAlreadyStarted() {
    when(userService.getUser(USER_ID)).thenReturn(user);
    var round = roundWithSeason(true);
    var startedMatch = match(round, Instant.now().minusSeconds(60));
    round.setMatches(List.of(startedMatch));
    when(matchRepository.findById(startedMatch.getId())).thenReturn(Optional.of(startedMatch));

    predictionService.savePredictions(USER_ID, List.of(prediction(startedMatch.getId(), 2, 1, true)));

    assertThat(startedMatch.getPredictions()).isEmpty();
    verify(matchRepository, never()).save(startedMatch);
  }

  private RoundEntity roundWithSeason(boolean active) {
    var season = new SeasonEntity();
    season.setId(UUID.randomUUID());
    season.setActive(active);

    var round = new RoundEntity();
    round.setId(UUID.randomUUID());
    round.setOrderNumber(1);
    round.setSeason(season);
    season.setRounds(List.of(round));
    return round;
  }

  private MatchEntity match(RoundEntity round, Instant startTime) {
    var match = new MatchEntity();
    match.setId(UUID.randomUUID());
    match.setRound(round);
    match.setStartTime(startTime);
    match.setStatus(MatchStatus.PLANNED);
    match.setHomeTeam(team("Home"));
    match.setAwayTeam(team("Away"));
    return match;
  }

  private TeamEntity team(String name) {
    var team = new TeamEntity();
    team.setId(UUID.randomUUID());
    team.setName(name);
    return team;
  }

  private PredictionEntity existingPrediction(
      MatchEntity match,
      UserEntity user,
      int home,
      int away,
      boolean doubleUp) {
    var prediction = new PredictionEntity();
    prediction.setId(UUID.randomUUID());
    prediction.setMatch(match);
    prediction.setUser(user);
    prediction.setPredictionHome(home);
    prediction.setPredictionAway(away);
    prediction.setDoubleUp(doubleUp);
    match.getPredictions().add(prediction);
    return prediction;
  }

  private PredictionRequestDto prediction(UUID matchId, int home, int away, boolean doubleUp) {
    var prediction = new PredictionRequestDto();
    prediction.setMatchId(matchId);
    prediction.setPredictionHome(home);
    prediction.setPredictionAway(away);
    prediction.setDoubleUp(doubleUp);
    return prediction;
  }
}

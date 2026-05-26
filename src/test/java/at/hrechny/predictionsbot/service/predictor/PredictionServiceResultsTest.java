package at.hrechny.predictionsbot.service.predictor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.PredictionEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.controller.model.prediction.ResultResponseDto;
import at.hrechny.predictionsbot.controller.model.user.UserResponseDto;
import at.hrechny.predictionsbot.mapper.UserMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PredictionServiceResultsTest {

  @Mock
  private UserMapper userMapper;

  private PredictionService predictionService;

  @BeforeEach
  void setUp() {
    predictionService = new PredictionService(userMapper, null, null, null, new PredictionResultsCalculator());
  }

  @Test
  void getResultsCalculatesFinishedAndLiveScoresByUser() {
    var alice = userEntity(1L, "alice");
    var bob = userEntity(2L, "bob");
    var charlie = userEntity(3L, "charlie");
    when(userMapper.entityToModel(alice)).thenReturn(user(1L, "alice"));
    when(userMapper.entityToModel(bob)).thenReturn(user(2L, "bob"));
    when(userMapper.entityToModel(charlie)).thenReturn(user(3L, "charlie"));

    var matches = List.of(
        match(MatchStatus.FINISHED, 2, 1,
            prediction(alice, 2, 1, true),
            prediction(bob, 0, 0, false)),
        match(MatchStatus.FINISHED, 3, 1,
            prediction(alice, 2, 0, false),
            prediction(bob, 3, 1, false)),
        match(MatchStatus.FINISHED, 0, 2,
            prediction(alice, 0, 1, false)),
        match(MatchStatus.FINISHED, 1, 0,
            prediction(alice, 0, 1, false)),
        match(MatchStatus.STARTED, 1, 1,
            prediction(alice, 1, 1, false),
            prediction(charlie, 1, 1, false)));

    var results = predictionService.getResults(matches);

    assertThat(results).hasSize(3);
    assertThat(results.get(0).getUser().getName()).isEqualTo("alice");

    Map<String, ResultResponseDto> byName = results.stream()
        .collect(Collectors.toMap(result -> result.getUser().getName(), result -> result));
    assertThat(byName.get("alice").getPredictions()).isEqualTo(4);
    assertThat(byName.get("alice").getGuessed()).isEqualTo(3);
    assertThat(byName.get("alice").getSum()).isEqualTo(15);
    assertThat(byName.get("alice").getPredictionsLive()).isEqualTo(1);
    assertThat(byName.get("alice").getGuessedLive()).isEqualTo(1);
    assertThat(byName.get("alice").getLiveSum()).isEqualTo(5);
    assertThat(byName.get("alice").getTotalPredictions()).isEqualTo(5);
    assertThat(byName.get("alice").getTotalGuessed()).isEqualTo(4);
    assertThat(byName.get("alice").getTotalSum()).isEqualTo(20);

    assertThat(byName.get("bob").getPredictions()).isEqualTo(2);
    assertThat(byName.get("bob").getGuessed()).isEqualTo(1);
    assertThat(byName.get("bob").getSum()).isEqualTo(5);
    assertThat(byName.get("bob").getTotalSum()).isEqualTo(5);

    assertThat(byName.get("charlie").getPredictions()).isZero();
    assertThat(byName.get("charlie").getPredictionsLive()).isEqualTo(1);
    assertThat(byName.get("charlie").getTotalPredictions()).isEqualTo(1);
    assertThat(byName.get("charlie").getTotalSum()).isEqualTo(5);
  }

  private MatchEntity match(MatchStatus status, int homeScore, int awayScore, PredictionEntity... predictions) {
    var match = new MatchEntity();
    match.setId(UUID.randomUUID());
    match.setStatus(status);
    match.setHomeTeamScore(homeScore);
    match.setAwayTeamScore(awayScore);
    for (var prediction : predictions) {
      prediction.setMatch(match);
      match.getPredictions().add(prediction);
    }
    return match;
  }

  private PredictionEntity prediction(UserEntity user, int home, int away, boolean doubleUp) {
    var prediction = new PredictionEntity();
    prediction.setId(UUID.randomUUID());
    prediction.setUser(user);
    prediction.setPredictionHome(home);
    prediction.setPredictionAway(away);
    prediction.setDoubleUp(doubleUp);
    return prediction;
  }

  private UserEntity userEntity(Long id, String username) {
    var user = new UserEntity();
    user.setId(id);
    user.setUsername(username);
    return user;
  }

  private UserResponseDto user(Long id, String name) {
    var user = new UserResponseDto();
    user.setId(id);
    user.setName(name);
    return user;
  }
}

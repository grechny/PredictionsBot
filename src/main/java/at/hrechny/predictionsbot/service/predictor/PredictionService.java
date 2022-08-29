package at.hrechny.predictionsbot.service.predictor;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.PredictionEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.repository.MatchRepository;
import at.hrechny.predictionsbot.database.repository.SeasonRepository;
import at.hrechny.predictionsbot.mapper.UserMapper;
import at.hrechny.predictionsbot.model.Prediction;
import at.hrechny.predictionsbot.model.Result;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PredictionService {

  private final UserMapper userMapper;
  private final MatchRepository matchRepository;
  private final SeasonRepository seasonRepository;
  private final CompetitionService competitionService;
  private final UserService userService;

  @Transactional
  public void savePredictions(Long userId, List<Prediction> predictions) {
    log.info("Saving predictions for the user {}", userId);
    var user = userService.getUser(userId);

    if (CollectionUtils.isEmpty(predictions)) {
      log.warn("No predictions found to save");
      return;
    }

    var predictionEntities = new ArrayList<PredictionEntity>();
    predictions.forEach(prediction -> {
      var matchEntity = matchRepository.findById(prediction.getMatchId())
          .orElseThrow(() -> new IllegalArgumentException("Match with id " + prediction.getMatchId() + " not found"));

      if (Instant.now().isAfter(matchEntity.getStartTime())) {
        log.warn("Not possible to save prediction for the match {} - match already started", matchEntity.getId());
        return;
      }

      var predictionEntity = matchEntity.getPrediction(userId).orElseGet(() -> createPredictionEntity(user, matchEntity));

      predictionEntity.setPredictionHome(prediction.getPredictionHome());
      predictionEntity.setPredictionAway(prediction.getPredictionAway());
      predictionEntity.setDoubleUp(prediction.isDoubleUp());
      predictionEntity.setUpdatedAt(Instant.now());

      predictionEntities.add(predictionEntity);
      matchRepository.save(matchEntity);
    });

    if (CollectionUtils.isEmpty(predictionEntities)) {
      log.warn("No predictions saved");
    } else {
      validatePredictions(userId, predictionEntities);
      log.info("All predictions for the user {} has been successfully saved", userId);
    }
  }

  public List<Result> getResults(UUID competitionId) {
    competitionService.refreshActiveFixtures();
    var season = seasonRepository.findFirstByCompetition_IdAndActiveIsTrue(competitionId)
        .orElseThrow(() -> new IllegalArgumentException("No active season found for the competition " + competitionId));
    return getResults(season.getMatches());
  }

  public List<Result> getResults(UUID competitionId, int round) {
    competitionService.refreshActiveFixtures();

    var season = seasonRepository.findFirstByCompetition_IdAndActiveIsTrue(competitionId)
        .orElseThrow(() -> new IllegalArgumentException("No active season found for the competition " + competitionId));
    var matchesOfRound = season.getMatches().stream().filter(match -> match.getRound() == round).toList();
    return getResults(matchesOfRound);
  }

  private List<Result> getResults(List<MatchEntity> matches) {
    var predictions = matches.stream()
        .filter(match -> match.getStatus() == MatchStatus.FINISHED)
        .flatMap(match -> match.getPredictions().stream())
        .collect(Collectors.groupingBy(PredictionEntity::getUser));

    var predictionsLive = matches.stream()
        .filter(match -> match.getStatus() == MatchStatus.STARTED)
        .flatMap(match -> match.getPredictions().stream())
        .collect(Collectors.groupingBy(PredictionEntity::getUser));

    var results = new ArrayList<Result>();
    for (var user : SetUtils.union(predictions.keySet(), predictionsLive.keySet())) {
      var result = new Result();
      result.setUser(userMapper.entityToModel(user));
      result.setPredictions(predictions.get(user).size());
      result.setPredictionsLive(CollectionUtils.isNotEmpty(predictionsLive.get(user)) ? predictionsLive.get(user).size() : 0);
      result.setGuessed(calculateGuessed(predictions.get(user)));
      result.setSum(calculateResults(predictions.get(user)));
      result.setLiveSum(calculateResults(predictionsLive.get(user)));
      results.add(result);
    }
    return results.stream().sorted(Comparator.comparingInt(Result::getSum).reversed()).toList();
  }

  private Integer calculateGuessed(List<PredictionEntity> predictionEntities) {
    if (CollectionUtils.isEmpty(predictionEntities)) {
      return 0;
    }

    AtomicInteger sum = new AtomicInteger();
    predictionEntities.forEach(prediction -> {
      var match = prediction.getMatch();

      //draw hit
      if (match.getHomeTeamScore() - match.getAwayTeamScore() == prediction.getPredictionHome() - prediction.getPredictionAway()) {
        sum.incrementAndGet();
      //winner hit
      } else if (match.getHomeTeamScore() > match.getAwayTeamScore() == prediction.getPredictionHome() > prediction.getPredictionAway()) {
        sum.incrementAndGet();
      }
    });
    return sum.intValue();
  }

  private Integer calculateResults(List<PredictionEntity> predictionEntities) {
    if (CollectionUtils.isEmpty(predictionEntities)) {
      return 0;
    }

    AtomicInteger sum = new AtomicInteger();
    predictionEntities.forEach(prediction -> {
      int result = 0;
      var match = prediction.getMatch();

      //exact hit
      if (match.getHomeTeamScore().equals(prediction.getPredictionHome()) && match.getAwayTeamScore().equals(prediction.getPredictionAway())) {
        result = 5;
        //difference hit
      } else if (match.getHomeTeamScore() - match.getAwayTeamScore() == prediction.getPredictionHome() - prediction.getPredictionAway()) {
        result = 3;
        //winner hit
      } else if (match.getHomeTeamScore() > match.getAwayTeamScore() == prediction.getPredictionHome() > prediction.getPredictionAway()) {
        result = 2;
      }

      if (prediction.isDoubleUp()) {
        result *= 2;
      }

      sum.addAndGet(result);
    });

    return sum.intValue();
  }

  private PredictionEntity createPredictionEntity(UserEntity user, MatchEntity matchEntity) {
    var predictionEntity = new PredictionEntity();
    predictionEntity.setUser(user);
    predictionEntity.setMatch(matchEntity);
    matchEntity.getPredictions().add(predictionEntity);
    return predictionEntity;
  }

  private void validatePredictions(Long userId, Iterable<PredictionEntity> predictions) {
    var predictionEntities = new ArrayList<PredictionEntity>();
    predictions.forEach(predictionEntities::add);

    var seasons = predictionEntities.stream().map(prediction -> prediction.getMatch().getSeason()).distinct().toList();
    if (seasons.size() > 1) {
      throw new IllegalArgumentException("Updating of predictions of different competitions/seasons at once is not supported");
    }

    var season = seasons.get(0);
    if (!season.isActive()) {
      throw new IllegalArgumentException("Season is not active");
    }

    var rounds = predictionEntities.stream().map(prediction -> prediction.getMatch().getRound()).distinct().toList();
    if (rounds.size() > 1) {
      throw new IllegalArgumentException("Updating of predictions of different rounds at once is not supported");
    }

    var round = rounds.get(0);
    season.getMatches().stream().filter(match -> match.getRound().equals(round)).forEach(match -> {
      var predictionsOfMatch = match.getPredictions().stream().filter(prediction -> prediction.getUser().getId().equals(userId));
      if (predictionsOfMatch.count() > 1) {
        throw new IllegalArgumentException("User can not make more than one prediction for the match");
      }
    });

    var doubleUpOfTheRound = season.getMatches().stream()
        .filter(match -> match.getRound().equals(round))
        .map(MatchEntity::getPredictions)
        .flatMap(Collection::stream)
        .filter(prediction -> prediction.getUser().getId().equals(userId))
        .filter(PredictionEntity::isDoubleUp);
    if (doubleUpOfTheRound.count() != 1) {
      throw new IllegalArgumentException("User has no/more than one double up for for the round");
    }
  }

}
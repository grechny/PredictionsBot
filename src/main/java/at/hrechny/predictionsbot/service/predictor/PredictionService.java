package at.hrechny.predictionsbot.service.predictor;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.PredictionEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.repository.MatchRepository;
import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.exception.RequestValidationException;
import at.hrechny.predictionsbot.mapper.UserMapper;
import at.hrechny.predictionsbot.model.Prediction;
import at.hrechny.predictionsbot.model.Result;
import at.hrechny.predictionsbot.util.ObjectUtils;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PredictionService {

  private final UserMapper userMapper;
  private final MatchRepository matchRepository;
  private final CompetitionService competitionService;
  private final UserService userService;

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
          .orElseThrow(() -> new NotFoundException("Match with id " + prediction.getMatchId() + " not found"));

      if (matchEntity.getStartTime() != null && Instant.now().isAfter(matchEntity.getStartTime())) {
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
      log.info("All predictions for the user {} have been successfully saved", userId);
    }
  }

  public List<Result> getResults(UUID seasonId) {
    var season = competitionService.getSeason(seasonId);
    var matches = season.getRounds().stream().flatMap(roundEntity -> roundEntity.getMatches().stream()).toList();
    return getResults(matches);
  }

  public List<Result> getResults(List<MatchEntity> matches) {
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
      result.setPredictions(predictions.get(user) != null ? predictions.get(user).size() : 0);
      result.setGuessed(calculateGuessed(predictions.get(user)));
      result.setSum(calculateResults(predictions.get(user)));
      if (CollectionUtils.isNotEmpty(predictionsLive.get(user))) {
        result.setPredictionsLive(predictionsLive.get(user).size());
        result.setGuessedLive(calculateGuessed(predictionsLive.get(user)));
        result.setLiveSum(calculateResults(predictionsLive.get(user)));
      }
      results.add(result);
    }
    return results.stream().sorted(Comparator.comparingInt(Result::getTotalSum).reversed()).toList();
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
      } else if (match.getHomeTeamScore() > match.getAwayTeamScore() && prediction.getPredictionHome() > prediction.getPredictionAway()) {
        sum.incrementAndGet();
      } else if (match.getHomeTeamScore() < match.getAwayTeamScore() && prediction.getPredictionHome() < prediction.getPredictionAway()) {
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
      //winner hit (home)
      } else if (match.getHomeTeamScore() > match.getAwayTeamScore() && prediction.getPredictionHome() > prediction.getPredictionAway()) {
        result = 2;
      //winner hit (away)
      } else if (match.getHomeTeamScore() < match.getAwayTeamScore() && prediction.getPredictionHome() < prediction.getPredictionAway()) {
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

    var seasons = predictionEntities.stream().map(prediction -> prediction.getMatch().getRound().getSeason()).distinct().toList();
    if (seasons.size() > 1) {
      throw new RequestValidationException("Updating of predictions of different competitions/seasons at once is not supported");
    }

    var season = seasons.get(0);
    if (!season.isActive()) {
      throw new RequestValidationException("Season is not active");
    }

    var rounds = predictionEntities.stream().map(prediction -> prediction.getMatch().getRound()).distinct().toList();
    if (!isSameRound(rounds)) {
      throw new RequestValidationException("Updating of predictions of different rounds at once is not supported");
    }

    var roundOrderNumber = rounds.get(0).getOrderNumber();
    var matches = rounds.get(0).getSeason().getRounds().stream()
        .filter(round -> round.getOrderNumber() == roundOrderNumber)
        .flatMap(round -> round.getMatches().stream()).toList();
    matches.forEach(match -> {
      var predictionsOfMatch = match.getPredictions().stream().filter(prediction -> prediction.getUser().getId().equals(userId));
      if (predictionsOfMatch.count() > 1) {
        throw new RequestValidationException("User can not make more than one prediction for the match");
      }
    });

    var doubleUpOfTheRound = matches.stream()
        .map(MatchEntity::getPredictions)
        .flatMap(Collection::stream)
        .filter(prediction -> prediction.getUser().getId().equals(userId))
        .filter(PredictionEntity::isDoubleUp);
    if (doubleUpOfTheRound.count() != 1) {
      throw new RequestValidationException("User has no/more than one double up for for the round");
    }
  }

  private boolean isSameRound(List<RoundEntity> rounds) {
    if (rounds.size() > 1) {
      var seasonCount = rounds.stream().filter(ObjectUtils.distinctByKey(round -> round.getSeason().getId())).count();
      var roundOrderNumberCount = rounds.stream().filter(ObjectUtils.distinctByKey(RoundEntity::getOrderNumber)).count();
      return seasonCount == 1 && roundOrderNumberCount == 1;
    }
    return true;
  }

}

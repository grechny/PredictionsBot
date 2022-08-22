package at.hrechny.predictionsbot.service;

import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.model.Prediction;
import at.hrechny.predictionsbot.model.Result;
import java.util.List;
import java.util.UUID;

public interface PredictionService {

  void saveUser(UserEntity userEntity);

  UserEntity getUser(Long userId);

  void savePredictions(Long userId, List<Prediction> predictions);

  List<Result> getResults(UUID competitionId);

  List<Result> getRoundResults(UUID competitionId, int round);

}

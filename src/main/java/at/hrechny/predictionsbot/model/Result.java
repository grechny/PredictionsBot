package at.hrechny.predictionsbot.model;

import lombok.Data;

@Data
public class Result {

  private User user;

  private Integer predictions;

  private Integer predictionsLive;

  private Integer guessed;

  private Integer guessedLive;

  private Integer sum;

  private Integer liveSum;

  public Integer getTotalPredictions() {
    return predictionsLive != null ? predictions + predictionsLive : predictions;
  }

  public Integer getTotalGuessed() {
    return guessedLive != null ? guessed + guessedLive : guessed;
  }

  public Integer getTotalSum() {
    return liveSum != null ? sum + liveSum : sum;
  }

}

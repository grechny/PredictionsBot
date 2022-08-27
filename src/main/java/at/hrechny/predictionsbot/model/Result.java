package at.hrechny.predictionsbot.model;

import lombok.Data;

@Data
public class Result {

  private User user;

  private Integer predictions;

  private Integer predictionsLive;

  private Integer guessed;

  private Integer sum;

  private Integer liveSum;

}

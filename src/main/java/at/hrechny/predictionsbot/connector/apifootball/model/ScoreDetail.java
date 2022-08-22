package at.hrechny.predictionsbot.connector.apifootball.model;

import lombok.Data;

@Data
public class ScoreDetail {

  private Score halftime;
  private Score fulltime;
  private Score extratime;
  private Score penalty;

}

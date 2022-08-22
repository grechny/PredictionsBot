package at.hrechny.predictionsbot.model;

import java.util.UUID;
import lombok.Data;

@Data
public class Prediction {

  private UUID matchId;

  private int predictionHome;

  private int predictionAway;

  private boolean doubleUp;

}

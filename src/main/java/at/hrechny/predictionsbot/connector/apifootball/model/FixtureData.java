package at.hrechny.predictionsbot.connector.apifootball.model;

import java.time.OffsetDateTime;
import lombok.Data;

@Data
public class FixtureData {

  private Long id;
  private OffsetDateTime date;
  private Status status;

}

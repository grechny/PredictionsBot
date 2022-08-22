package at.hrechny.predictionsbot.connector.apifootball.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Status {

  @JsonProperty("long")
  private String statusLong;

  @JsonProperty("short")
  private FixtureStatusEnum status;

  private Integer elapsed;

}

package at.hrechny.predictionsbot.model;

import java.util.UUID;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
import lombok.Data;

@Data
public class Competition {

  @Null
  private UUID id;

  @NotNull
  private String name;

  @NotNull
  private Long apiFootballId;

}

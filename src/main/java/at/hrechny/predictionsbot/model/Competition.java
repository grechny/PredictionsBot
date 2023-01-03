package at.hrechny.predictionsbot.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.util.UUID;
import lombok.Data;

@Data
public class Competition {

  @Null
  private UUID id;

  @NotNull
  private String name;

  @NotNull
  private Long apiFootballId;

  @NotNull
  private boolean active;

}

package at.hrechny.predictionsbot.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Null;
import java.time.Year;
import java.util.UUID;
import lombok.Data;

@Data
public class Season {

  private UUID id;

  @NotNull
  private Year year;

  @Null
  private String competition;

  private boolean active = true;

}

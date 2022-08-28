package at.hrechny.predictionsbot.model;

import java.time.Year;
import java.util.UUID;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Null;
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

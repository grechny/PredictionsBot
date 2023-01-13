package at.hrechny.predictionsbot.model;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PushUpdate {

  @NotNull
  private String message;

  private boolean updateCompetitionList;

}

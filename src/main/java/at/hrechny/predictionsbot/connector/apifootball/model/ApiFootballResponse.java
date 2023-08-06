package at.hrechny.predictionsbot.connector.apifootball.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class ApiFootballResponse<T> {

  private List<String> errors;
  private List<T> response;

}

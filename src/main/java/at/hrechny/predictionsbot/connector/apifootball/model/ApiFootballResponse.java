package at.hrechny.predictionsbot.connector.apifootball.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public abstract class ApiFootballResponse<T> {

  protected List<String> errors;
  protected List<T> response;

}

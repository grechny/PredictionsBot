package at.hrechny.predictionsbot.connector.apifootball.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Setter
@Getter
@ToString
public abstract class ApiFootballResponse<T> {

  private List<String> errors;
  private List<T> response;

}

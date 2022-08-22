package at.hrechny.predictionsbot.connector.apifootball.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoundsResponse extends ApiFootballResponse<String> {

  List<String> errors;
  List<String> response;

}

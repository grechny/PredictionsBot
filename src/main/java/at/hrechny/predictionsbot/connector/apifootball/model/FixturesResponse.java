package at.hrechny.predictionsbot.connector.apifootball.model;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FixturesResponse extends ApiFootballResponse<Fixture> {

  List<String> errors;
  List<Fixture> response;

}

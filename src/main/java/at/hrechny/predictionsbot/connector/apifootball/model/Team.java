package at.hrechny.predictionsbot.connector.apifootball.model;

import lombok.Data;

@Data
public class Team {

  private Long id;
  private String name;
  private String logo;
  private boolean winner;

}

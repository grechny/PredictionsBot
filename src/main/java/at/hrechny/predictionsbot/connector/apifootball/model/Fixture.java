package at.hrechny.predictionsbot.connector.apifootball.model;

import lombok.Data;

@Data
public class Fixture {

  private FixtureData fixture;
  private LeagueData league;
  private TeamsData teams;
  private Score goals;
  private ScoreDetail score;

}

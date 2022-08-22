package at.hrechny.predictionsbot.connector.apifootball.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum FixtureStatusEnum {

  TBD("TBD", "Time To Be Defined"),
  NS("NS", "Not Started"),
  _1H("1H", "First Half, Kick Off"),
  HT("HT", "Halftime"),
  _2H("2H", "Second Half, 2nd Half Started"),
  ET("ET", "Extra Time"),
  P("P", "Penalty In Progress"),
  FT("FT", "Match Finished"),
  AET("AET", "Match Finished After Extra Time"),
  PEN("PEN", "Match Finished After Penalty"),
  BT("BT", "Break Time (in Extra Time)"),
  SUSP("SUSP", "Match Suspended"),
  INT("INT", "Match Interrupted"),
  PST("PST", "Match Postponed"),
  CANC("CANC", "Match Cancelled"),
  ABD("ABD", "Match Abandoned"),
  AWD("AWD", "Technical Loss"),
  WO("WO", "WalkOver"),
  LIVE("LIVE", "In Progress");

  private final String value;
  private final String description;

  FixtureStatusEnum(String value, String description) {
    this.value = value;
    this.description = description;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static FixtureStatusEnum fromValue(String value) {
    for (FixtureStatusEnum b : FixtureStatusEnum.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    return TBD;
  }

}

package at.hrechny.predictionsbot.database.model;

import at.hrechny.predictionsbot.exception.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;

@Getter
public enum RoundType {

  QUALIFYING(null, List.of(".*Play-offs.*", "Preliminary Round", ".*Qualifying.*", "Relegation Round")),
  SEASON("$round", List.of("Regular Season.*")),
  GROUP_STAGE("$round", List.of("Group.*")),
  ROUND_OF_16("1 / 8", List.of("Round of 16")),
  ROUND_OF_16_RETURN("1 / 8", List.of("Round of 16")),
  QUARTER_FINAL("1 / 4", List.of("Quarter-finals")),
  QUARTER_FINAL_RETURN("1 / 4", List.of("Quarter-finals")),
  SEMI_FINAL("1 / 2", List.of("Semi-finals")),
  SEMI_FINAL_RETURN("1 / 2", List.of("Semi-finals")),
  THIRD_PLACE_FINAL("\uD83E\uDD49", List.of("3rd Place Final")),
  FINAL("\uD83C\uDFC6", List.of("Final"));

  private final String name;
  private final List<String> aliasNames;

  RoundType(String name, List<String> aliasNames) {
    this.name = name;
    this.aliasNames = aliasNames;
  }

  public static List<RoundType> getByAlias(String aliasName) {
    var roundTypes = new ArrayList<RoundType>();
    for (var roundType : RoundType.values()) {
      if (roundType.getAliasNames().stream().anyMatch(pattern -> Pattern.matches(pattern, aliasName))) {
        roundTypes.add(roundType);
      }
    }

    if (roundTypes.isEmpty()) {
      throw new NotFoundException("No round type found by alias name " + aliasName);
    }

    return roundTypes;
  }

}

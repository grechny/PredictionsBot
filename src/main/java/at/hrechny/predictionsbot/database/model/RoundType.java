package at.hrechny.predictionsbot.database.model;

import at.hrechny.predictionsbot.exception.NotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;

@Getter
public enum RoundType {

  QUALIFYING(null, List.of("Preliminary Round", ".*Qualifying.*", "Relegation Round", ".*Play-offs.*")),
  SEASON("$round", List.of("Regular Season.*")),
  GROUP_STAGE("$round", List.of("Group.*", "League Stage.*")),
  ROUND_OF_32("1 / 16", List.of("Round of 32", "Knockout Round Play-offs")),
  ROUND_OF_32_RETURN("1 / 16", List.of("Round of 32")),
  ROUND_OF_16("1 / 8", List.of("Round of 16")),
  ROUND_OF_16_RETURN("1 / 8", List.of("Round of 16", "8th Finals")),
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

    // Special case for "Knockout Round Play-offs"
    if ("Knockout Round Play-offs".equals(aliasName)) {
      roundTypes.add(ROUND_OF_32);
      return roundTypes;
    }

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

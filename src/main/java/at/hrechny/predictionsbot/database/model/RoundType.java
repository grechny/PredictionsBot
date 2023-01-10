package at.hrechny.predictionsbot.database.model;

import at.hrechny.predictionsbot.exception.NotFoundException;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;

@Getter
public enum RoundType {

  QUALIFYING(null, List.of("Play-offs", "Preliminary Round", ".*Qualifying.*")),
  SEASON("$round", List.of("Regular Season.*")),
  GROUP_STAGE("$round", List.of("Group.*")),
  ROUND_OF_16("1 / 8", List.of("Round of 16")),
  QUARTER_FINAL("1 / 4", List.of("Quarter-finals")),
  SEMI_FINAL("1 / 2", List.of("Semi-finals")),
  THIRD_PLACE_FINAL("&#x1f949;", List.of("3rd Place Final")),
  FINAL("&#x1f3c6;", List.of("Final"));

  private final String name;
  private final List<String> aliasNames;

  RoundType(String name, List<String> aliasNames) {
    this.name = name;
    this.aliasNames = aliasNames;
  }

  public static RoundType getByAlias(String aliasName) {
    for (var roundType : RoundType.values()) {
      if (roundType.getAliasNames().stream().anyMatch(pattern -> Pattern.matches(pattern, aliasName))) {
        return roundType;
      }
    }

    throw new NotFoundException("No round type found by alias name " + aliasName);
  }

}

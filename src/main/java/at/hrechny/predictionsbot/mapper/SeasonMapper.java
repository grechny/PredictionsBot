package at.hrechny.predictionsbot.mapper;

import at.hrechny.predictionsbot.model.Season;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = YearMapper.class)
public interface SeasonMapper {

  @Mapping(target = "competition", source = "competition.name")
  Season entityToModel(SeasonEntity source);

  @Mapping(target = "matches", ignore = true)
  @Mapping(target = "competition", ignore = true)
  @Mapping(target = "apiFootballRounds", ignore = true)
  void updateEntity(@MappingTarget SeasonEntity entity, Season model);

  @Mapping(target = "id", source = "season.id")
  @Mapping(target = "competition", source = "competition")
  @Mapping(target = "matches", ignore = true)
  @Mapping(target = "apiFootballRounds", ignore = true)
  SeasonEntity modelToEntity(CompetitionEntity competition, Season season);

}

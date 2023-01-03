package at.hrechny.predictionsbot.mapper;

import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.model.Season;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(uses = YearMapper.class)
public interface SeasonMapper {

  @Mapping(target = "competition", source = "competition.name")
  Season entityToModel(SeasonEntity source);

  @Mapping(target = "rounds", ignore = true)
  @Mapping(target = "competition", ignore = true)
  void updateEntity(@MappingTarget SeasonEntity entity, Season model);

  @Mapping(target = "id", source = "season.id")
  @Mapping(target = "competition", source = "competition")
  @Mapping(target = "rounds", ignore = true)
  SeasonEntity modelToEntity(CompetitionEntity competition, Season season);

}

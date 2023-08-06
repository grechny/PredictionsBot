package at.hrechny.predictionsbot.mapper;

import at.hrechny.predictionsbot.config.MapperConfig;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.model.Competition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

@Mapper(config = MapperConfig.class)
public interface CompetitionMapper {

  @Mapping(target = "active", source = "source", qualifiedByName = "isActive")
  Competition entityToModel(CompetitionEntity source);

  @Mapping(target = "seasons", ignore = true)
  CompetitionEntity modelToEntity(Competition source);

  @Named("isActive")
  default boolean isActive(CompetitionEntity entity) {
    return entity.getSeasons().stream().anyMatch(SeasonEntity::isActive);
  }

}

package at.hrechny.predictionsbot.mapper;

import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface CompetitionMapper {

  Competition entityToModel(CompetitionEntity source);

  @Mapping(target = "seasons", ignore = true)
  CompetitionEntity modelToEntity(Competition source);

}

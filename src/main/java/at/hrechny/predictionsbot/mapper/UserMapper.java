package at.hrechny.predictionsbot.mapper;

import at.hrechny.predictionsbot.config.MapperConfig;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapperConfig.class)
public interface UserMapper {

  @Mapping(target = "name", source = "username")
  User entityToModel(UserEntity source);

}

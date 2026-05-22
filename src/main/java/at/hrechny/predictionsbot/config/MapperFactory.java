package at.hrechny.predictionsbot.config;

import at.hrechny.predictionsbot.mapper.CompetitionMapper;
import at.hrechny.predictionsbot.mapper.SeasonMapper;
import at.hrechny.predictionsbot.mapper.UserMapper;
import io.micronaut.context.annotation.Factory;
import jakarta.inject.Singleton;
import org.mapstruct.factory.Mappers;

@Factory
public class MapperFactory {

  @Singleton
  CompetitionMapper competitionMapper() {
    return Mappers.getMapper(CompetitionMapper.class);
  }

  @Singleton
  SeasonMapper seasonMapper() {
    return Mappers.getMapper(SeasonMapper.class);
  }

  @Singleton
  UserMapper userMapper() {
    return Mappers.getMapper(UserMapper.class);
  }
}

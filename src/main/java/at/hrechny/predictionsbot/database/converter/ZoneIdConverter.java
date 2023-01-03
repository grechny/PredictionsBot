package at.hrechny.predictionsbot.database.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.ZoneId;

@Converter
public class ZoneIdConverter implements AttributeConverter<ZoneId, String> {

  @Override
  public String convertToDatabaseColumn(ZoneId attribute) {
    return attribute.getId();
  }

  @Override
  public ZoneId convertToEntityAttribute(String dbData) {
    return ZoneId.of( dbData );
  }

}

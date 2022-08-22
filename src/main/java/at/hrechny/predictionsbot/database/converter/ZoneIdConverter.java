package at.hrechny.predictionsbot.database.converter;

import java.time.ZoneId;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

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

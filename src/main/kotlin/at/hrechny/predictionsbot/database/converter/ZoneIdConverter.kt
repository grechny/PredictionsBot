package at.hrechny.predictionsbot.database.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.ZoneId

@Converter
class ZoneIdConverter : AttributeConverter<ZoneId, String> {
    override fun convertToDatabaseColumn(attribute: ZoneId?): String? = attribute?.id

    override fun convertToEntityAttribute(dbData: String?): ZoneId? = dbData?.let(ZoneId::of)
}

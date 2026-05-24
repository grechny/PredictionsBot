package at.hrechny.predictionsbot.mapper

import jakarta.inject.Singleton
import java.time.Year
import org.apache.commons.lang3.StringUtils

@Singleton
class YearMapper {
    fun asString(year: Year?): String? = year?.toString()

    fun asYear(string: String?): Year? =
        if (StringUtils.isBlank(string)) {
            null
        } else {
            Year.parse(string)
        }
}

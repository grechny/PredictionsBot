package at.hrechny.predictionsbot.config

import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import jakarta.inject.Singleton
import java.util.Optional

enum class ConnectorProxyMode(
    val configurationValue: String,
) {
    NONE("none"),
    WEBSHARE_IO("webshare.io"),
    ;

    companion object {
        fun fromConfiguration(value: String?): ConnectorProxyMode {
            if (value.isNullOrBlank()) {
                return NONE
            }
            return entries.firstOrNull { mode -> mode.configurationValue == value.trim().lowercase() }
                ?: throw IllegalArgumentException(
                    "Unsupported connector proxy mode '$value'. Supported values: " +
                        entries.joinToString { mode -> mode.configurationValue },
                )
        }
    }
}

@Singleton
class ConnectorProxyModeConverter : TypeConverter<String, ConnectorProxyMode> {
    override fun convert(
        value: String,
        targetType: Class<ConnectorProxyMode>,
        context: ConversionContext,
    ): Optional<ConnectorProxyMode> =
        Optional.of(ConnectorProxyMode.fromConfiguration(value))
}

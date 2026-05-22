package at.hrechny.predictionsbot.config

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import java.io.IOException
import java.io.UncheckedIOException
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory

class JsonBodyHandler<W>(
    private val wClass: Class<W>,
) : HttpResponse.BodyHandler<W> {
    override fun apply(responseInfo: HttpResponse.ResponseInfo): HttpResponse.BodySubscriber<W> = asJSON(wClass)

    companion object {
        private val log = LoggerFactory.getLogger(JsonBodyHandler::class.java)

        @JvmStatic
        fun <T> asJSON(targetType: Class<T>): HttpResponse.BodySubscriber<T> {
            val upstream = HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8)

            return HttpResponse.BodySubscribers.mapping(upstream) { body ->
                try {
                    val objectMapper = ObjectMapper()
                    objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    objectMapper.registerModule(JavaTimeModule())
                    objectMapper.readValue(body, targetType)
                } catch (exception: IOException) {
                    log.error("Failed to deserialize JSON response for {}: {}", targetType.simpleName, body, exception)
                    throw UncheckedIOException(exception)
                }
            }
        }
    }
}

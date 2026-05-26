package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.impl.apifootball.model.ApiFootballResponse
import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

@Singleton
open class ApiFootballResponseParser(
    @param:Value("\${connectors.api-football.apiKey}")
    private val apiKey: String,
) {
    private val objectMapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(JavaTimeModule())

    open fun <T, G : ApiFootballResponse<T>> parse(
        connectorCode: String,
        requestUri: String,
        statusCode: Int,
        reasonPhrase: String?,
        headers: Map<String, String>,
        body: String,
        clazz: Class<G>,
    ): G {
        val normalizedHeaders = headers.mapKeys { (name, _) -> name.lowercase() }
        if (statusCode !in 200..299) {
            throw ApiConnectorException(
                connectorCode,
                reasonFor(statusCode, normalizedHeaders),
                responseDetails(requestUri, statusCode, reasonPhrase, normalizedHeaders, body),
            )
        }

        val response = try {
            objectMapper.readValue(body, clazz)
        } catch (exception: Exception) {
            throw ApiConnectorException(
                connectorCode,
                Reason.INVALID_RESPONSE,
                "Failed to parse API-Football response for $requestUri: ${sanitizeBody(body)}",
                exception,
            )
        }

        if (response == null) {
            throw ApiConnectorException(connectorCode, Reason.INVALID_RESPONSE, "API-Football response is null for $requestUri")
        }
        if (response.errors.isNotEmpty()) {
            throw ApiConnectorException(
                connectorCode,
                Reason.INVALID_RESPONSE,
                "API-Football response contains errors for $requestUri: ${response.errors}",
            )
        }
        if (response.response == null) {
            throw ApiConnectorException(
                connectorCode,
                Reason.INVALID_RESPONSE,
                "API-Football response has null response field for $requestUri",
            )
        }
        return response
    }

    private fun reasonFor(statusCode: Int, headers: Map<String, String>): Reason =
        when {
            statusCode == 429 && headers.containsKey(RETRY_AFTER_HEADER) -> Reason.TOO_OFTEN_REQUESTS
            statusCode == 429 && headers[REQUESTS_REMAINING_HEADER]?.toIntOrNull() == 0 -> Reason.QUOTA_EXCEEDED
            else -> Reason.REQUEST_ERROR
        }

    private fun responseDetails(
        requestUri: String,
        statusCode: Int,
        reasonPhrase: String?,
        headers: Map<String, String>,
        body: String,
    ): String =
        "HTTP $statusCode ${reasonPhrase.orEmpty()}; request=$requestUri; headers=$headers; body=${sanitizeBody(body)}"

    private fun sanitizeBody(body: String): String {
        if (body.isBlank()) {
            return ""
        }

        val withoutApiKey = if (apiKey.isBlank()) {
            body
        } else {
            body.replace(apiKey, "<redacted>")
        }
        return if (withoutApiKey.length <= RESPONSE_BODY_LOG_LIMIT) {
            withoutApiKey
        } else {
            withoutApiKey.take(RESPONSE_BODY_LOG_LIMIT) + "...<truncated>"
        }
    }

    private companion object {
        const val RESPONSE_BODY_LOG_LIMIT = 500
        const val RETRY_AFTER_HEADER = "retry-after"
        const val REQUESTS_REMAINING_HEADER = "x-ratelimit-requests-remaining"
    }
}

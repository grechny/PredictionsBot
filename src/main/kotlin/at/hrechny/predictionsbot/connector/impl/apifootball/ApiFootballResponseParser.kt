package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.impl.apifootball.model.ApiFootballResponse
import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton

@Singleton
class ApiFootballResponseParser(
    @param:Value("\${connectors.api-football.apiKey}")
    private val apiKey: String,
) {
    fun <T, G : ApiFootballResponse<T>> validate(
        connectorName: String,
        requestDescription: String,
        statusCode: Int,
        reasonPhrase: String?,
        headers: Map<String, String>,
        response: G?,
    ): G {
        val normalizedHeaders = headers.mapKeys { (name, _) -> name.lowercase() }
        if (statusCode !in 200..299) {
            throw ApiConnectorException(
                connectorName,
                reasonFor(statusCode, normalizedHeaders),
                responseDetails(requestDescription, statusCode, reasonPhrase, normalizedHeaders),
            )
        }

        if (response == null) {
            throw ApiConnectorException(
                connectorName,
                Reason.INVALID_RESPONSE,
                "API-Football response is null for $requestDescription",
            )
        }
        if (response.errors.isNotEmpty()) {
            throw ApiConnectorException(
                connectorName,
                Reason.INVALID_RESPONSE,
                "API-Football response contains errors for $requestDescription: ${response.errors}",
            )
        }
        if (response.response == null) {
            throw ApiConnectorException(
                connectorName,
                Reason.INVALID_RESPONSE,
                "API-Football response has null response field for $requestDescription",
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
        requestDescription: String,
        statusCode: Int,
        reasonPhrase: String?,
        headers: Map<String, String>,
    ): String =
        "HTTP $statusCode ${reasonPhrase.orEmpty()}; request=$requestDescription; headers=$headers"

    fun sanitize(value: String?): String? {
        if (value.isNullOrBlank()) {
            return value
        }

        val withoutApiKey = if (apiKey.isBlank()) {
            value
        } else {
            value.replace(apiKey, "<redacted>")
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

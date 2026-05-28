package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.impl.apifootball.model.ApiFootballResponse
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Fixture
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixturesResponse
import at.hrechny.predictionsbot.connector.impl.apifootball.model.RoundsResponse
import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
open class ApiFootballClient(
    private val apiFootballHttpClient: ApiFootballHttpClient,
    private val apiFootballResponseParser: ApiFootballResponseParser,
    @param:Value("\${connectors.api-football.fixtureBatchSize:20}")
    private val fixtureBatchSize: Int,
) {
    private val objectMapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(JavaTimeModule())

    fun getRounds(leagueId: Long, seasonYear: String): List<String> {
        return sendRequest(
            "rounds for league=$leagueId season=$seasonYear",
            RoundsResponse::class.java,
        ) { apiFootballHttpClient.getRounds(leagueId, seasonYear) }.response!!
    }

    fun getFixtures(leagueId: Long, seasonYear: String): List<Fixture> {
        return sendRequest(
            "fixtures for league=$leagueId season=$seasonYear",
            FixturesResponse::class.java,
        ) { apiFootballHttpClient.getSeasonFixtures(leagueId, seasonYear) }.response!!
    }

    @Cacheable(value = [ApiFootballConnector.NAME])
    open fun getFixtures(fixtureIds: List<Long>): List<Fixture> {
        if (fixtureIds.isEmpty()) {
            return emptyList()
        }

        val fixtures = mutableListOf<Fixture>()
        fixtureIds.chunked(fixtureBatchSize.coerceAtLeast(1)).forEachIndexed { batchIndex, batch ->
            val fixtureIdsString = batch.map(Long::toString).joinToString("-")
            try {
                fixtures.addAll(
                    sendRequest(
                        "fixtures by ids batch ${batchIndex + 1}",
                        FixturesResponse::class.java,
                    ) { apiFootballHttpClient.getFixturesByIds(fixtureIdsString) }.response!!,
                )
            } catch (exception: ApiConnectorException) {
                if (fixtures.isNotEmpty() && exception.reason in PARTIAL_BATCH_STOP_REASONS) {
                    log.warn(
                        "Stopped API-Football fixture batch refresh after batch {} because {}. Returning {} fixtures from completed batches",
                        batchIndex,
                        exception.reason,
                        fixtures.size,
                    )
                    return fixtures
                }
                if (exception.reason in PARTIAL_BATCH_STOP_REASONS) {
                    log.error("Failed to fetch fixtures", exception)
                    return emptyList()
                }
                throw exception
            }
        }
        return fixtures
    }

    @Synchronized
    private fun <T, G : ApiFootballResponse<T>> sendRequest(
        requestDescription: String,
        responseType: Class<G>,
        httpRequest: () -> HttpResponse<String>,
    ): G {
        val httpResponse = try {
            httpRequest()
        } catch (exception: HttpClientResponseException) {
            exception.response
        } catch (exception: Exception) {
            log.error("Request to API-Football failed", exception)
            val connectorException = ApiConnectorException(
                ApiFootballConnector.NAME,
                Reason.REQUEST_ERROR,
                apiFootballResponseParser.sanitize(exception.message),
                exception,
            )
            throw connectorException
        }
        return parseResponse(requestDescription, responseType, httpResponse)
    }

    private fun <T, G : ApiFootballResponse<T>> parseResponse(
        requestDescription: String,
        responseType: Class<G>,
        httpResponse: HttpResponse<*>,
    ): G {
        val headers = safeRateLimitHeaders(httpResponse)
        val responseBody = if (httpResponse.status.code in 200..299) {
            parseBody(requestDescription, responseType, httpResponse.body.orElse(null) as? String)
        } else {
            null
        }
        return apiFootballResponseParser.validate(
            ApiFootballConnector.NAME,
            requestDescription,
            httpResponse.status.code,
            httpResponse.status.reason,
            headers,
            responseBody,
        )
    }

    private fun <G> parseBody(requestDescription: String, responseType: Class<G>, responseBody: String?): G? {
        if (responseBody == null) {
            return null
        }
        return try {
            objectMapper.readValue(responseBody, responseType)
        } catch (exception: Exception) {
            throw ApiConnectorException(
                ApiFootballConnector.NAME,
                Reason.INVALID_RESPONSE,
                "Failed to parse API-Football response for $requestDescription: " +
                    apiFootballResponseParser.sanitize(exception.message),
                exception,
            )
        }
    }

    private fun safeRateLimitHeaders(httpResponse: HttpResponse<*>): Map<String, String> =
        httpResponse.headers.names()
            .map { name -> name.lowercase() to (httpResponse.headers.get(name) ?: "") }
            .filter { (name, _) -> SAFE_RESPONSE_HEADERS.contains(name) || name.startsWith("x-ratelimit-") }
            .toMap()

    private companion object {
        const val RETRY_AFTER_HEADER = "retry-after"
        const val REQUESTS_REMAINING_HEADER = "x-ratelimit-requests-remaining"
        val SAFE_RESPONSE_HEADERS = setOf(
            RETRY_AFTER_HEADER,
            "x-ratelimit-requests-limit",
            REQUESTS_REMAINING_HEADER,
            "x-ratelimit-requests-reset",
            "x-ratelimit-requests-used",
            "x-ratelimit-subscription-limit",
            "x-ratelimit-subscription-remaining",
        )
        val PARTIAL_BATCH_STOP_REASONS = setOf(
            Reason.QUOTA_EXCEEDED,
            Reason.TOO_OFTEN_REQUESTS,
            Reason.REQUEST_ERROR,
        )
        val log = LoggerFactory.getLogger(ApiFootballClient::class.java)
    }
}

package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.ApiConnectorRequestAuditService
import at.hrechny.predictionsbot.connector.impl.apifootball.model.ApiFootballResponse
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Fixture
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixturesResponse
import at.hrechny.predictionsbot.connector.impl.apifootball.model.RoundsResponse
import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.exceptions.HttpClientResponseException
import jakarta.inject.Singleton
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.slf4j.LoggerFactory

@Singleton
open class ApiFootballClient(
    private val apiFootballHttpClient: ApiFootballHttpClient,
    private val apiFootballResponseParser: ApiFootballResponseParser,
    private val apiConnectorRequestAuditService: ApiConnectorRequestAuditService,
    private val apiFootballQuotaGuard: ApiFootballQuotaGuard,
    @param:Value("\${connectors.api-football.apiKey}")
    private val apiKey: String,
) {
    open fun getRounds(competitionId: Long, seasonYear: String): List<String> {
        val requestUri = buildRequestUri("/fixtures/rounds", mapOf("league" to competitionId, "season" to seasonYear))
        return sendRequest(requestUri, RoundsResponse::class.java) {
            apiFootballHttpClient.getRounds(apiKey, RAPID_API_HOST, competitionId, seasonYear)
        }.response!!
    }

    open fun getFixtures(competitionId: Long, seasonYear: String): List<Fixture> {
        val requestUri = buildRequestUri("/fixtures", mapOf("league" to competitionId, "season" to seasonYear))
        return sendRequest(requestUri, FixturesResponse::class.java) {
            apiFootballHttpClient.getSeasonFixtures(apiKey, RAPID_API_HOST, competitionId, seasonYear)
        }.response!!
    }

    @Cacheable(value = ["api-football"])
    open fun getFixtures(fixtureIds: List<Long>): List<Fixture> {
        if (fixtureIds.isEmpty()) {
            return emptyList()
        }

        val fixtureIdsString = fixtureIds.take(20).map(Long::toString).joinToString("-")
        val requestUri = buildRequestUri("/fixtures", mapOf("ids" to fixtureIdsString))

        return try {
            sendRequest(requestUri, FixturesResponse::class.java) {
                apiFootballHttpClient.getFixturesByIds(apiKey, RAPID_API_HOST, fixtureIdsString)
            }.response!!
        } catch (exception: ApiConnectorException) {
            log.error("Failed to fetch fixtures", exception)
            emptyList()
        }
    }

    @Synchronized
    private fun <T, G : ApiFootballResponse<T>> sendRequest(
        requestUri: String,
        clazz: Class<G>,
        httpRequest: () -> HttpResponse<String>,
    ): G {
        try {
            apiFootballQuotaGuard.checkRequestAllowed(requestUri)
        } catch (exception: ApiConnectorException) {
            recordFailedRequest(requestUri, exception.message, apiFootballQuotaGuard.snapshot())
            throw exception
        }

        return try {
            val httpResponse = httpRequest()
            apiFootballQuotaGuard.markRequestAttempted()
            parseAndAudit(requestUri, httpResponse, clazz)
        } catch (exception: HttpClientResponseException) {
            apiFootballQuotaGuard.markRequestAttempted()
            parseAndAudit(requestUri, exception.response, clazz)
        } catch (exception: ApiConnectorException) {
            throw exception
        } catch (exception: Exception) {
            apiFootballQuotaGuard.markRequestAttempted()
            log.error("Request to API-Football failed", exception)
            val connectorException = ApiConnectorException(CONNECTOR_CODE, Reason.REQUEST_ERROR, exception.message, exception)
            recordFailedRequest(requestUri, connectorException.message, apiFootballQuotaGuard.snapshot())
            throw connectorException
        }
    }

    private fun <T, G : ApiFootballResponse<T>> parseAndAudit(
        requestUri: String,
        httpResponse: HttpResponse<*>,
        clazz: Class<G>,
    ): G {
        val headers = safeRateLimitHeaders(httpResponse)
        apiFootballQuotaGuard.updateFromHeaders(headers)
        val quotaSnapshot = apiFootballQuotaGuard.snapshot(headers)
        return try {
            val response = apiFootballResponseParser.parse(
                CONNECTOR_CODE,
                requestUri,
                httpResponse.status.code,
                httpResponse.status.reason,
                headers,
                httpResponse.getBody(String::class.java).orElse(""),
                clazz,
            )
            apiConnectorRequestAuditService.recordRequest(
                CONNECTOR_CODE,
                requestUri,
                true,
                null,
                quotaSnapshot,
            )
            response
        } catch (exception: ApiConnectorException) {
            recordFailedRequest(requestUri, exception.message, quotaSnapshot)
            throw exception
        }
    }

    private fun recordFailedRequest(requestUri: String, failureReason: String?, quotaSnapshot: String?) {
        apiConnectorRequestAuditService.recordRequest(
            CONNECTOR_CODE,
            requestUri,
            false,
            failureReason,
            quotaSnapshot,
        )
    }

    private fun safeRateLimitHeaders(httpResponse: HttpResponse<*>): Map<String, String> =
        httpResponse.headers.names()
            .map { name -> name.lowercase() to (httpResponse.headers.get(name) ?: "") }
            .filter { (name, _) -> SAFE_RESPONSE_HEADERS.contains(name) || name.startsWith("x-ratelimit-") }
            .toMap()

    private fun buildRequestUri(path: String, queryParams: Map<String, Any>): String {
        val query = queryParams.entries.map { entry -> "${encode(entry.key)}=${encode(entry.value.toString())}" }
        return "$path?${query.joinToString("&")}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val CONNECTOR_CODE = "api-football"
        const val RAPID_API_HOST = "api-football-v1.p.rapidapi.com"
        const val RETRY_AFTER_HEADER = "retry-after"
        const val REQUESTS_REMAINING_HEADER = "x-ratelimit-requests-remaining"
        const val REQUESTS_RESET_HEADER = "x-ratelimit-requests-reset"
        val SAFE_RESPONSE_HEADERS = setOf(
            RETRY_AFTER_HEADER,
            "x-ratelimit-requests-limit",
            REQUESTS_REMAINING_HEADER,
            REQUESTS_RESET_HEADER,
            "x-ratelimit-requests-used",
            "x-ratelimit-subscription-limit",
            "x-ratelimit-subscription-remaining",
        )
        val log = LoggerFactory.getLogger(ApiFootballClient::class.java)
    }
}

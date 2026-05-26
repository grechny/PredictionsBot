package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.impl.apifootball.model.ApiFootballResponse
import at.hrechny.predictionsbot.connector.impl.apifootball.model.Fixture
import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import at.hrechny.predictionsbot.service.connector.ApiConnectorRequestAuditService
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
    @param:Value("\${connectors.api-football.rapidApiHost:api-football-v1.p.rapidapi.com}")
    private val rapidApiHost: String,
    @param:Value("\${connectors.api-football.fixtureBatchSize:20}")
    private val fixtureBatchSize: Int,
) {
    open fun getRounds(leagueId: Long, seasonYear: String): List<String> {
        val requestUri = buildRequestUri("/fixtures/rounds", mapOf("league" to leagueId, "season" to seasonYear))
        return sendRequest(requestUri) {
            apiFootballHttpClient.getRounds(apiKey, rapidApiHost, leagueId, seasonYear)
        }.response!!
    }

    open fun getFixtures(leagueId: Long, seasonYear: String): List<Fixture> {
        val requestUri = buildRequestUri("/fixtures", mapOf("league" to leagueId, "season" to seasonYear))
        return sendRequest(requestUri) {
            apiFootballHttpClient.getSeasonFixtures(apiKey, rapidApiHost, leagueId, seasonYear)
        }.response!!
    }

    @Cacheable(value = ["api-football"])
    open fun getFixtures(fixtureIds: List<Long>): List<Fixture> {
        if (fixtureIds.isEmpty()) {
            return emptyList()
        }

        val fixtures = mutableListOf<Fixture>()
        fixtureIds.chunked(fixtureBatchSize.coerceAtLeast(1)).forEachIndexed { batchIndex, batch ->
            val fixtureIdsString = batch.map(Long::toString).joinToString("-")
            val requestUri = buildRequestUri("/fixtures", mapOf("ids" to fixtureIdsString))
            try {
                fixtures.addAll(
                    sendRequest(requestUri) {
                        apiFootballHttpClient.getFixturesByIds(apiKey, rapidApiHost, fixtureIdsString)
                    }.response!!,
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
        requestUri: String,
        httpRequest: () -> HttpResponse<G>,
    ): G {
        try {
            apiFootballQuotaGuard.checkRequestAllowed(requestUri)
        } catch (exception: ApiConnectorException) {
            recordFailedRequest(requestUri, exception.message)
            throw exception
        }
        apiFootballQuotaGuard.markRequestAttempted()

        val httpResponse = try {
            httpRequest()
        } catch (exception: HttpClientResponseException) {
            exception.response
        } catch (exception: Exception) {
            log.error("Request to API-Football failed", exception)
            val connectorException = ApiConnectorException(
                CONNECTOR_NAME,
                Reason.REQUEST_ERROR,
                apiFootballResponseParser.sanitize(exception.message),
                exception,
            )
            recordFailedRequest(requestUri, connectorException.message)
            throw connectorException
        }
        return parseAndAudit(requestUri, httpResponse)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T, G : ApiFootballResponse<T>> parseAndAudit(
        requestUri: String,
        httpResponse: HttpResponse<*>,
    ): G {
        val headers = safeRateLimitHeaders(httpResponse)
        apiFootballQuotaGuard.updateFromHeaders(headers)
        return try {
            val responseBody = if (httpResponse.status.code in 200..299) {
                httpResponse.body.orElse(null) as G?
            } else {
                null
            }
            val response = apiFootballResponseParser.validate(
                CONNECTOR_NAME,
                requestUri,
                httpResponse.status.code,
                httpResponse.status.reason,
                headers,
                responseBody,
            )
            apiConnectorRequestAuditService.recordRequest(
                CONNECTOR_NAME,
                requestUri,
                true,
                null,
            )
            response
        } catch (exception: ApiConnectorException) {
            recordFailedRequest(requestUri, exception.message)
            throw exception
        }
    }

    private fun recordFailedRequest(requestUri: String, failureReason: String?) {
        apiConnectorRequestAuditService.recordRequest(
            CONNECTOR_NAME,
            requestUri,
            false,
            failureReason,
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
        const val CONNECTOR_NAME = "api-football"
        const val RETRY_AFTER_HEADER = "retry-after"
        const val REQUESTS_REMAINING_HEADER = "x-ratelimit-requests-remaining"
        const val REQUESTS_RESET_HEADER = "x-ratelimit-requests-reset"
        val PARTIAL_BATCH_STOP_REASONS = setOf(
            Reason.QUOTA_EXCEEDED,
            Reason.TOO_OFTEN_REQUESTS,
            Reason.REQUEST_ERROR,
        )
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

package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.ApiConnector
import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import at.hrechny.predictionsbot.service.connector.ApiConnectorAuditService
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.client.exceptions.HttpClientResponseException
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory

@ClientFilter(ClientFilter.MATCH_ALL_PATTERN)
class ApiFootballHttpClientFilter(
    private val apiConnectorAuditService: ApiConnectorAuditService,
    private val clock: Clock,
    @param:Value("\${connectors.api-football.apiKey}")
    private val apiKey: String,
    @Value("\${connectors.api-football.url}")
    apiFootballUrl: String,
    @param:Value("\${connectors.api-football.maxAttempts}")
    private val maxAttempts: Int,
    @param:Value("\${connectors.api-football.dayStarts}")
    private val dayStarts: String,
) {
    private val apiFootballHost = parseHost(apiFootballUrl)
    private var billingStart: Instant = currentBillingStart(Instant.now(clock))
    private var requestCount: Int =
        apiConnectorAuditService.countRequestsSince(ApiFootballConnector.NAME, billingStart)
    private var retryBlockedUntil: Instant? = null

    @RequestFilter
    fun beforeRequest(request: MutableHttpRequest<*>) {
        if (!isApiFootballRequest(request)) {
            return
        }

        checkRequestAllowed(request.uri.toString())
        markRequestAttempted()
        request.header("X-RapidAPI-Key", apiKey)
        request.header("X-RapidAPI-Host", apiFootballHost)
    }

    @ResponseFilter
    fun afterResponse(request: HttpRequest<*>, response: HttpResponse<*>) {
        if (isApiFootballRequest(request)) {
            updateFromHeaders(safeRateLimitHeaders(response))
        }
    }

    @ResponseFilter
    fun afterFailure(request: HttpRequest<*>, exception: Throwable) {
        if (!isApiFootballRequest(request)) {
            return
        }

        val response = (exception as? HttpClientResponseException)?.response ?: return
        updateFromHeaders(safeRateLimitHeaders(response))
    }

    private fun isApiFootballRequest(request: HttpRequest<*>): Boolean =
        request.getAttribute(ApiConnector.CONNECTOR_NAME_ATTRIBUTE, String::class.java)
            .orElse(null) == ApiFootballConnector.NAME

    private fun parseHost(apiFootballUrl: String): String =
        URI.create(apiFootballUrl).host
            ?: throw ApiConnectorException(
                ApiFootballConnector.NAME,
                Reason.REQUEST_ERROR,
                "connectors.api-football.url must be an absolute URL with host",
            )

    @Synchronized
    private fun checkRequestAllowed(requestUri: String) {
        val now = Instant.now(clock)
        refreshBillingWindow(now)
        checkRetryState(now)

        if (maxAttempts > 0 && requestCount >= maxAttempts) {
            throw ApiConnectorException(
                ApiFootballConnector.NAME,
                Reason.QUOTA_EXCEEDED,
                "API-Football daily request quota is exhausted before $requestUri: ${quotaState()}",
            )
        }
    }

    @Synchronized
    private fun markRequestAttempted() {
        refreshBillingWindow(Instant.now(clock))
        requestCount += 1
    }

    @Synchronized
    private fun updateFromHeaders(headers: Map<String, String>) {
        val now = Instant.now(clock)
        refreshBillingWindow(now)
        val normalizedHeaders = headers.mapKeys { (name, _) -> name.lowercase() }

        parseRetryAfter(normalizedHeaders[RETRY_AFTER_HEADER], now)?.let { retryUntil ->
            retryBlockedUntil = retryUntil
            log.warn("API-Football requested retry after {}", retryUntil)
        }

        val remainingRequests = normalizedHeaders[REQUESTS_REMAINING_HEADER]?.toIntOrNull() ?: return
        updateCountFromHeaders(remainingRequests, normalizedHeaders)
        if (remainingRequests <= 0 && maxAttempts > 0) {
            requestCount = maxAttempts
            log.warn("API-Football request quota is exhausted")
        }
    }

    private fun refreshBillingWindow(now: Instant) {
        val currentBillingStart = currentBillingStart(now)
        if (currentBillingStart.isAfter(billingStart)) {
            billingStart = currentBillingStart
            requestCount = apiConnectorAuditService.countRequestsSince(ApiFootballConnector.NAME, billingStart)
        }
    }

    private fun checkRetryState(now: Instant) {
        val blockedUntil = retryBlockedUntil ?: return
        if (now.isBefore(blockedUntil)) {
            throw ApiConnectorException(
                ApiFootballConnector.NAME,
                Reason.TOO_OFTEN_REQUESTS,
                "API-Football retry-after is active until $blockedUntil",
            )
        }
        retryBlockedUntil = null
    }

    private fun currentBillingStart(now: Instant): Instant {
        val billingStartTime = LocalTime.parse(dayStarts)
        var billingStartDate = LocalDate.ofInstant(now, ZoneOffset.UTC)
        if (LocalTime.ofInstant(now, ZoneOffset.UTC).isBefore(billingStartTime)) {
            billingStartDate = billingStartDate.minusDays(1)
        }
        return LocalDateTime.of(billingStartDate, billingStartTime).toInstant(ZoneOffset.UTC)
    }

    private fun parseRetryAfter(value: String?, now: Instant): Instant? {
        if (value.isNullOrBlank()) {
            return null
        }

        value.toLongOrNull()?.let { seconds ->
            return if (seconds <= 0) now else now.plusSeconds(seconds)
        }
        return runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .getOrNull()
    }

    private fun updateCountFromHeaders(remainingRequests: Int, headers: Map<String, String>) {
        val requestsUsed = headers[REQUESTS_USED_HEADER]?.toIntOrNull()
            ?: headers[REQUESTS_LIMIT_HEADER]?.toIntOrNull()?.let { limit -> limit - remainingRequests }
        if (requestsUsed != null && requestsUsed >= 0) {
            requestCount = maxOf(requestCount, requestsUsed)
        }
    }

    private fun quotaState(): String =
        "connector=${ApiFootballConnector.NAME};dailyCount=$requestCount;dailyLimit=$maxAttempts;billingStart=$billingStart"

    private fun safeRateLimitHeaders(httpResponse: HttpResponse<*>): Map<String, String> =
        httpResponse.headers.names()
            .map { name -> name.lowercase() to (httpResponse.headers.get(name) ?: "") }
            .filter { (name, _) -> SAFE_RESPONSE_HEADERS.contains(name) || name.startsWith("x-ratelimit-") }
            .toMap()

    private companion object {
        const val RETRY_AFTER_HEADER = "retry-after"
        const val REQUESTS_LIMIT_HEADER = "x-ratelimit-requests-limit"
        const val REQUESTS_REMAINING_HEADER = "x-ratelimit-requests-remaining"
        const val REQUESTS_USED_HEADER = "x-ratelimit-requests-used"
        val SAFE_RESPONSE_HEADERS = setOf(
            RETRY_AFTER_HEADER,
            REQUESTS_LIMIT_HEADER,
            REQUESTS_REMAINING_HEADER,
            "x-ratelimit-requests-reset",
            REQUESTS_USED_HEADER,
            "x-ratelimit-subscription-limit",
            "x-ratelimit-subscription-remaining",
        )
        val log = LoggerFactory.getLogger(ApiFootballHttpClientFilter::class.java)
    }
}

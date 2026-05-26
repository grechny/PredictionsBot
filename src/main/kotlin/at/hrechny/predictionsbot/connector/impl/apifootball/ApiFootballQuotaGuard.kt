package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import at.hrechny.predictionsbot.service.connector.ApiConnectorRequestAuditService
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.slf4j.LoggerFactory

@Singleton
open class ApiFootballQuotaGuard(
    private val apiConnectorRequestAuditService: ApiConnectorRequestAuditService,
    private val clock: Clock,
    @param:Value("\${connectors.api-football.maxAttempts}")
    private val maxAttempts: Int,
    @param:Value("\${connectors.api-football.dayStarts}")
    private val dayStarts: String,
) {
    private var billingStart: Instant = currentBillingStart(Instant.now(clock))
    private var requestCount: Int =
        apiConnectorRequestAuditService.countRequestsSince(CONNECTOR_NAME, billingStart)
    private var retryBlockedUntil: Instant? = null

    @Synchronized
    open fun checkRequestAllowed(requestUri: String) {
        val now = Instant.now(clock)
        refreshBillingWindow(now)
        checkRetryState(now)

        if (maxAttempts > 0 && requestCount >= maxAttempts) {
            throw ApiConnectorException(
                CONNECTOR_NAME,
                Reason.QUOTA_EXCEEDED,
                "API-Football daily request quota is exhausted before $requestUri: ${quotaState()}",
            )
        }
    }

    @Synchronized
    open fun markRequestAttempted() {
        refreshBillingWindow(Instant.now(clock))
        requestCount += 1
    }

    @Synchronized
    open fun updateFromHeaders(headers: Map<String, String>) {
        val now = Instant.now(clock)
        refreshBillingWindow(now)
        val normalizedHeaders = headers.mapKeys { (name, _) -> name.lowercase() }

        parseRetryAfter(normalizedHeaders[RETRY_AFTER_HEADER], now)?.let { retryUntil ->
            retryBlockedUntil = retryUntil
            log.warn("API-Football requested retry after {}", retryUntil)
        }

        val remainingRequests = normalizedHeaders[REQUESTS_REMAINING_HEADER]?.toIntOrNull()
        if (remainingRequests == null) {
            logMalformedHeader(normalizedHeaders, REQUESTS_REMAINING_HEADER)
            return
        }

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
            requestCount = apiConnectorRequestAuditService.countRequestsSince(CONNECTOR_NAME, billingStart)
        }
    }

    private fun checkRetryState(now: Instant) {
        val blockedUntil = retryBlockedUntil ?: return
        if (now.isBefore(blockedUntil)) {
            throw ApiConnectorException(
                CONNECTOR_NAME,
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
        return parseHttpDate(value)
    }

    private fun parseHttpDate(value: String): Instant? =
        runCatching { java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }
            .getOrNull()

    private fun updateCountFromHeaders(remainingRequests: Int, headers: Map<String, String>) {
        val requestsUsed = headers[REQUESTS_USED_HEADER]?.toIntOrNull()
            ?: headers[REQUESTS_LIMIT_HEADER]?.toIntOrNull()?.let { limit -> limit - remainingRequests }
        if (requestsUsed != null && requestsUsed >= 0) {
            requestCount = maxOf(requestCount, requestsUsed)
        }
    }

    private fun quotaState(): String =
        "connector=$CONNECTOR_NAME;dailyCount=$requestCount;dailyLimit=$maxAttempts;billingStart=$billingStart"

    private fun logMalformedHeader(headers: Map<String, String>, headerName: String) {
        if (headers.containsKey(headerName)) {
            log.warn("API-Football returned malformed quota header {}={}", headerName, headers[headerName])
        }
    }

    private companion object {
        const val CONNECTOR_NAME = "api-football"
        const val RETRY_AFTER_HEADER = "retry-after"
        const val REQUESTS_LIMIT_HEADER = "x-ratelimit-requests-limit"
        const val REQUESTS_REMAINING_HEADER = "x-ratelimit-requests-remaining"
        const val REQUESTS_USED_HEADER = "x-ratelimit-requests-used"
        val log = LoggerFactory.getLogger(ApiFootballQuotaGuard::class.java)
    }
}

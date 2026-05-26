package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.ApiConnectorRequestAuditService
import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
        apiConnectorRequestAuditService.countRequestsSince(CONNECTOR_CODE, billingStart)
    private var quotaBlockedUntil: Instant? = null
    private var retryBlockedUntil: Instant? = null

    @Synchronized
    open fun checkRequestAllowed(requestUri: String) {
        val now = Instant.now(clock)
        refreshBillingWindow(now)
        checkRetryState(now)
        checkQuotaState(now)

        if (maxAttempts > 0 && requestCount >= maxAttempts) {
            throw ApiConnectorException(
                CONNECTOR_CODE,
                Reason.QUOTA_EXCEEDED,
                "API-Football daily request quota is exhausted before $requestUri: ${snapshot()}",
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

        if (remainingRequests <= 0) {
            quotaBlockedUntil = parseReset(normalizedHeaders[REQUESTS_RESET_HEADER], now) ?: nextBillingStart(now)
            log.warn("API-Football request quota is exhausted until {}", quotaBlockedUntil)
        }
    }

    @Synchronized
    open fun snapshot(headers: Map<String, String> = emptyMap()): String {
        val parts = mutableListOf(
            "connector=$CONNECTOR_CODE",
            "dailyCount=$requestCount",
            "dailyLimit=$maxAttempts",
            "billingStart=$billingStart",
        )
        retryBlockedUntil?.let { parts.add("retryBlockedUntil=$it") }
        quotaBlockedUntil?.let { parts.add("quotaBlockedUntil=$it") }
        if (headers.isNotEmpty()) {
            parts.add(
                headers.entries
                    .sortedBy { (name, _) -> name }
                    .joinToString(",", prefix = "headers=") { (name, value) -> "$name=$value" },
            )
        }
        return parts.joinToString(";")
    }

    private fun refreshBillingWindow(now: Instant) {
        val currentBillingStart = currentBillingStart(now)
        if (currentBillingStart.isAfter(billingStart)) {
            billingStart = currentBillingStart
            requestCount = apiConnectorRequestAuditService.countRequestsSince(CONNECTOR_CODE, billingStart)
            quotaBlockedUntil = null
        }
    }

    private fun checkRetryState(now: Instant) {
        val blockedUntil = retryBlockedUntil ?: return
        if (now.isBefore(blockedUntil)) {
            throw ApiConnectorException(
                CONNECTOR_CODE,
                Reason.TOO_OFTEN_REQUESTS,
                "API-Football retry-after is active until $blockedUntil",
            )
        }
        retryBlockedUntil = null
    }

    private fun checkQuotaState(now: Instant) {
        val blockedUntil = quotaBlockedUntil ?: return
        if (now.isBefore(blockedUntil)) {
            throw ApiConnectorException(
                CONNECTOR_CODE,
                Reason.QUOTA_EXCEEDED,
                "API-Football request quota is exhausted until $blockedUntil",
            )
        }
        quotaBlockedUntil = null
    }

    private fun currentBillingStart(now: Instant): Instant {
        val billingStartTime = LocalTime.parse(dayStarts)
        var billingStartDate = LocalDate.ofInstant(now, ZoneOffset.UTC)
        if (LocalTime.ofInstant(now, ZoneOffset.UTC).isBefore(billingStartTime)) {
            billingStartDate = billingStartDate.minusDays(1)
        }
        return LocalDateTime.of(billingStartDate, billingStartTime).toInstant(ZoneOffset.UTC)
    }

    private fun nextBillingStart(now: Instant): Instant =
        currentBillingStart(now).plusSeconds(SECONDS_PER_DAY)

    private fun parseRetryAfter(value: String?, now: Instant): Instant? {
        if (value.isNullOrBlank()) {
            return null
        }

        value.toLongOrNull()?.let { seconds ->
            return if (seconds <= 0) now else now.plusSeconds(seconds)
        }
        return parseHttpDate(value)
    }

    private fun parseReset(value: String?, now: Instant): Instant? {
        if (value.isNullOrBlank()) {
            return null
        }

        value.toLongOrNull()?.let { seconds ->
            return if (seconds > EPOCH_SECONDS_THRESHOLD) {
                Instant.ofEpochSecond(seconds)
            } else {
                now.plusSeconds(seconds.coerceAtLeast(0))
            }
        }
        return runCatching { Instant.parse(value) }.getOrNull() ?: parseHttpDate(value)
    }

    private fun parseHttpDate(value: String): Instant? =
        runCatching { ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant() }.getOrNull()

    private fun logMalformedHeader(headers: Map<String, String>, headerName: String) {
        if (headers.containsKey(headerName)) {
            log.warn("API-Football returned malformed quota header {}={}", headerName, headers[headerName])
        }
    }

    private companion object {
        const val CONNECTOR_CODE = "api-football"
        const val EPOCH_SECONDS_THRESHOLD = 1_000_000_000L
        const val SECONDS_PER_DAY = 86_400L
        const val RETRY_AFTER_HEADER = "retry-after"
        const val REQUESTS_REMAINING_HEADER = "x-ratelimit-requests-remaining"
        const val REQUESTS_RESET_HEADER = "x-ratelimit-requests-reset"
        val log = LoggerFactory.getLogger(ApiFootballQuotaGuard::class.java)
    }
}

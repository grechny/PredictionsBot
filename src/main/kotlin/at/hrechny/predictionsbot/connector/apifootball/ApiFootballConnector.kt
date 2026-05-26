package at.hrechny.predictionsbot.connector.apifootball

import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException.Reason
import at.hrechny.predictionsbot.connector.apifootball.model.ApiFootballResponse
import at.hrechny.predictionsbot.connector.apifootball.model.Fixture
import at.hrechny.predictionsbot.connector.apifootball.model.FixturesResponse
import at.hrechny.predictionsbot.connector.apifootball.model.RoundsResponse
import at.hrechny.predictionsbot.database.entity.AuditEntity
import at.hrechny.predictionsbot.database.model.ApiProvider
import at.hrechny.predictionsbot.database.repository.AuditRepository
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Value
import jakarta.inject.Singleton
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpResponse
import org.apache.http.HttpHost
import org.apache.http.client.fluent.Executor
import org.apache.http.client.fluent.Request
import org.apache.http.util.EntityUtils
import org.slf4j.LoggerFactory

@Singleton
open class ApiFootballConnector(
    private val auditRepository: AuditRepository,
    @param:Value("\${connectors.api-football.url}")
    private val baseUrl: String,
    @param:Value("\${connectors.api-football.apiKey}")
    private val apiKey: String,
    @param:Value("\${connectors.api-football.maxAttempts}")
    private val maxAttempts: Int,
    @param:Value("\${connectors.api-football.dayStarts}")
    private val dayStarts: String,
    @param:Value("\${connectors.proxy.host:}")
    private val proxyHost: String,
    @param:Value("\${connectors.proxy.port:0}")
    private val proxyPort: Int,
    @param:Value("\${connectors.proxy.username:}")
    private val proxyUsername: String,
    @param:Value("\${connectors.proxy.password:}")
    private val proxyPassword: String,
) {
    open fun getRounds(competitionId: Long, seasonYear: String): List<String> {
        val uri = buildUri("/fixtures/rounds", mapOf("league" to competitionId, "season" to seasonYear))
        return sendRequest(uri, RoundsResponse::class.java).response!!
    }

    open fun getFixtures(competitionId: Long, seasonYear: String): List<Fixture> {
        val uri = buildUri("/fixtures", mapOf("league" to competitionId, "season" to seasonYear))
        return sendRequest(uri, FixturesResponse::class.java).response!!
    }

    @Cacheable(value = ["api-football"])
    open fun getFixtures(fixtureIds: List<Long>): List<Fixture> {
        val fixtureIdsString = fixtureIds.take(20).map(Long::toString)
        val uri = buildUri("/fixtures", mapOf("ids" to fixtureIdsString.joinToString("-")))

        return try {
            sendRequest(uri, FixturesResponse::class.java).response!!
        } catch (exception: ApiFootballConnectorException) {
            log.error("Failed to fetch fixtures", exception)
            emptyList()
        }
    }

    @Synchronized
    private fun <T, G : ApiFootballResponse<T>> sendRequest(uri: URI, clazz: Class<G>): G {
        checkMaxAttempts()

        val auditEntity = AuditEntity().apply {
            apiKey = this@ApiFootballConnector.apiKey
            apiProvider = ApiProvider.API_FOOTBALL
            requestUri = uri.toString()
            requestDate = Instant.now()
        }

        try {
            val request = Request.Get(uri.toString())
                .addHeader("X-RapidAPI-Key", apiKey)
                .addHeader("X-RapidAPI-Host", "api-football-v1.p.rapidapi.com")
            val executor = Executor.newInstance()

            if (StringUtils.isNotBlank(proxyHost)) {
                val proxy = HttpHost(proxyHost, proxyPort)
                request.viaProxy(proxy)

                if (StringUtils.isNotBlank(proxyUsername)) {
                    executor.auth(proxy, proxyUsername, proxyPassword)
                }
            }

            val httpResponse = executor
                .execute(request)
                .returnResponse()
            val responseString = readResponse(uri, httpResponse)

            val objectMapper = ObjectMapper()
            objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            objectMapper.registerModule(JavaTimeModule())

            val response = objectMapper.readValue(responseString, clazz)
            if (response == null) {
                auditEntity.success = false
                log.error("API-Football response is null. Request: {}", uri)
                throw ApiFootballConnectorException(Reason.INVALID_RESPONSE)
            } else if (CollectionUtils.isNotEmpty(response.errors)) {
                auditEntity.success = false
                log.error("API-Football response contains errors: {}", response.errors)
                throw ApiFootballConnectorException(Reason.INVALID_RESPONSE)
            } else if (response.response == null) {
                auditEntity.success = false
                log.error("API-Football response has null response field: {}", response)
                throw ApiFootballConnectorException(Reason.INVALID_RESPONSE)
            } else {
                auditEntity.success = true
            }
            return response
        } catch (exception: ApiFootballConnectorException) {
            auditEntity.success = false
            throw exception
        } catch (exception: Exception) {
            log.error("Request to API-Football failed", exception)
            auditEntity.success = false
            throw ApiFootballConnectorException(Reason.REQUEST_ERROR, exception.message, exception)
        } finally {
            auditRepository.save(auditEntity)
        }
    }

    private fun readResponse(uri: URI, httpResponse: HttpResponse): String {
        val responseString = if (httpResponse.entity != null) {
            EntityUtils.toString(httpResponse.entity, StandardCharsets.UTF_8)
        } else {
            ""
        }
        val statusCode = httpResponse.statusLine.statusCode
        if (statusCode in 200..299) {
            return responseString
        }

        val safeHeaders = safeRateLimitHeaders(httpResponse)
        val bodyExcerpt = sanitizeBody(responseString)
        val details = "HTTP $statusCode ${httpResponse.statusLine.reasonPhrase}; headers=$safeHeaders; body=$bodyExcerpt"
        log.error("API-Football returned non-success response for {}: {}", uri, details)
        throw ApiFootballConnectorException(Reason.REQUEST_ERROR, details)
    }

    private fun safeRateLimitHeaders(httpResponse: HttpResponse): Map<String, String> =
        httpResponse.allHeaders
            .map { header -> header.name.lowercase() to header.value }
            .filter { (name, _) -> SAFE_RESPONSE_HEADERS.contains(name) || name.startsWith("x-ratelimit-") }
            .toMap()

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

    private fun checkMaxAttempts() {
        if (maxAttempts <= 0) {
            return
        }

        var billingStartDate = LocalDate.now(ZoneOffset.UTC)
        val billingStartTime = LocalTime.parse(dayStarts)
        if (LocalTime.now(ZoneOffset.UTC).isBefore(billingStartTime)) {
            billingStartDate = billingStartDate.minusDays(1)
        }

        val billingStartDateTime = LocalDateTime.of(billingStartDate, billingStartTime).toInstant(ZoneOffset.UTC)
        val count = auditRepository.countAllByApiProviderAndApiKeyAndRequestDateAfter(
            ApiProvider.API_FOOTBALL,
            apiKey,
            billingStartDateTime,
        )
        if (count >= maxAttempts) {
            throw ApiFootballConnectorException(Reason.QUOTA_EXCEEDED)
        }
    }

    private fun buildUri(path: String, queryParams: Map<String, Any>): URI {
        val query = queryParams.entries.map { entry -> "${encode(entry.key)}=${encode(entry.value.toString())}" }
        return URI.create("$baseUrl$path?${query.joinToString("&")}")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val RESPONSE_BODY_LOG_LIMIT = 500
        val SAFE_RESPONSE_HEADERS = setOf(
            "retry-after",
            "x-ratelimit-requests-limit",
            "x-ratelimit-requests-remaining",
            "x-ratelimit-requests-reset",
            "x-ratelimit-requests-used",
            "x-ratelimit-subscription-limit",
            "x-ratelimit-subscription-remaining",
        )
        val log = LoggerFactory.getLogger(ApiFootballConnector::class.java)
    }
}

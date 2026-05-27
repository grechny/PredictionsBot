package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.ApiConnector
import at.hrechny.predictionsbot.exception.ApiConnectorException
import at.hrechny.predictionsbot.exception.ApiConnectorException.Reason
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.client.exceptions.HttpClientResponseException
import java.net.URI

@ClientFilter(ClientFilter.MATCH_ALL_PATTERN)
open class ApiFootballHttpClientFilter(
    private val apiFootballQuotaGuard: ApiFootballQuotaGuard,
    @param:Value("\${connectors.api-football.apiKey}")
    private val apiKey: String,
    @Value("\${connectors.api-football.url}")
    apiFootballUrl: String,
) {
    private val apiFootballHost = parseHost(apiFootballUrl)

    @RequestFilter
    open fun beforeRequest(request: MutableHttpRequest<*>) {
        if (!isApiFootballRequest(request)) {
            return
        }

        apiFootballQuotaGuard.checkRequestAllowed(request.uri.toString())
        apiFootballQuotaGuard.markRequestAttempted()
        request.header("X-RapidAPI-Key", apiKey)
        request.header("X-RapidAPI-Host", apiFootballHost)
    }

    @ResponseFilter
    open fun afterResponse(request: HttpRequest<*>, response: HttpResponse<*>) {
        if (isApiFootballRequest(request)) {
            apiFootballQuotaGuard.updateFromHeaders(ApiFootballResponseHeaders.safeRateLimitHeaders(response))
        }
    }

    @ResponseFilter
    open fun afterFailure(request: HttpRequest<*>, exception: Throwable) {
        if (!isApiFootballRequest(request)) {
            return
        }

        val response = (exception as? HttpClientResponseException)?.response ?: return
        apiFootballQuotaGuard.updateFromHeaders(ApiFootballResponseHeaders.safeRateLimitHeaders(response))
    }

    private fun isApiFootballRequest(request: HttpRequest<*>): Boolean =
        request.getAttribute(ApiConnector.CONNECTOR_NAME_ATTRIBUTE, String::class.java)
            .orElse(null) == ApiFootballConnector.connectorName()

    private fun parseHost(apiFootballUrl: String): String =
        URI.create(apiFootballUrl).host
            ?: throw ApiConnectorException(
                ApiFootballConnector.connectorName(),
                Reason.REQUEST_ERROR,
                "connectors.api-football.url must be an absolute URL with host",
            )
}

package at.hrechny.predictionsbot.connector

import at.hrechny.predictionsbot.service.connector.ApiConnectorAuditService
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.ClientFilter
import io.micronaut.http.annotation.ResponseFilter
import io.micronaut.http.client.exceptions.HttpClientResponseException

@ClientFilter(ClientFilter.MATCH_ALL_PATTERN)
open class ConnectorHttpClientAuditFilter(
    private val apiConnectorAuditService: ApiConnectorAuditService,
) {
    @ResponseFilter
    open fun recordResponse(request: HttpRequest<*>, response: HttpResponse<*>) {
        val connectorName = connectorName(request) ?: return
        apiConnectorAuditService.recordRequest(
            connectorName,
            request.uri.toString(),
            response.status.code in 200..299,
            failureReason(response),
        )
    }

    @ResponseFilter
    open fun recordFailure(request: HttpRequest<*>, exception: Throwable) {
        val connectorName = connectorName(request) ?: return
        val response = (exception as? HttpClientResponseException)?.response
        apiConnectorAuditService.recordRequest(
            connectorName,
            request.uri.toString(),
            false,
            response?.let(::failureReason) ?: exception.javaClass.simpleName,
        )
    }

    private fun connectorName(request: HttpRequest<*>): String? =
        request.getAttribute(ApiConnectorHttpAttributes.CONNECTOR_NAME, String::class.java).orElse(null)

    private fun failureReason(response: HttpResponse<*>): String? =
        if (response.status.code in 200..299) {
            null
        } else {
            "HTTP ${response.status.code} ${response.status.reason}"
        }
}

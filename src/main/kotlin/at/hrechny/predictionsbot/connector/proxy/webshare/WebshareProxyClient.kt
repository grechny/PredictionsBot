package at.hrechny.predictionsbot.connector.proxy.webshare

import at.hrechny.predictionsbot.config.JsonBodyHandler
import at.hrechny.predictionsbot.config.WebshareProxyConfiguration
import at.hrechny.predictionsbot.connector.proxy.webshare.model.WebshareProxyListResponse
import at.hrechny.predictionsbot.connector.proxy.webshare.model.WebshareProxyResponseDto
import at.hrechny.predictionsbot.exception.ConnectorProxyException
import jakarta.inject.Singleton
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.nio.charset.StandardCharsets
import java.time.Duration

interface WebshareProxyClient {
    fun listDirectProxies(): List<WebshareProxyResponseDto>
}

@Singleton
class DefaultWebshareProxyClient(
    private val configuration: WebshareProxyConfiguration,
) : WebshareProxyClient {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun listDirectProxies(): List<WebshareProxyResponseDto> {
        if (configuration.apiKey.isBlank()) {
            throw ConnectorProxyException("proxy.webshare.apiKey is required when webshare.io proxy is enabled")
        }
        if (configuration.apiUrl.isBlank()) {
            throw ConnectorProxyException("proxy.webshare.apiUrl is required when webshare.io proxy is enabled")
        }

        val request = HttpRequest.newBuilder(resolveListUri(configuration.apiUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Token ${configuration.apiKey}")
            .GET()
            .build()
        val response = try {
            httpClient.send(request, JsonBodyHandler(WebshareProxyListResponse::class.java))
        } catch (exception: Exception) {
            throw ConnectorProxyException("Failed to request Webshare proxy list", exception)
        }

        if (response.statusCode() !in 200..299) {
            throw ConnectorProxyException("Webshare proxy list request failed with HTTP ${response.statusCode()}")
        }
        return response.body()?.results ?: emptyList()
    }

    private fun resolveListUri(apiUrl: String): URI {
        val normalizedApiUrl = apiUrl.trim().trimEnd('/')
        val baseUrl = if (normalizedApiUrl.endsWith("/api/v2/proxy/list")) {
            normalizedApiUrl
        } else {
            "$normalizedApiUrl/api/v2/proxy/list"
        }
        return URI.create(
            "$baseUrl/?mode=${encode("direct")}&page=1&page_size=100",
        )
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)
}

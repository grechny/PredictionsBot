package at.hrechny.predictionsbot.connector.proxy.webshare

import at.hrechny.predictionsbot.config.WebshareProxyConfiguration
import at.hrechny.predictionsbot.connector.proxy.webshare.model.WebshareProxyListResponse
import at.hrechny.predictionsbot.connector.proxy.webshare.model.WebshareProxyResponseDto
import at.hrechny.predictionsbot.exception.ConnectorProxyException
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import jakarta.inject.Singleton
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
    private val objectMapper = ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .registerModule(JavaTimeModule())

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
            httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
        } catch (exception: Exception) {
            throw ConnectorProxyException("Failed to request Webshare proxy list", exception)
        }

        if (response.statusCode() !in 200..299) {
            throw ConnectorProxyException("Webshare proxy list request failed with HTTP ${response.statusCode()}")
        }
        return parseResponse(response.body()).results
    }

    private fun parseResponse(responseBody: String): WebshareProxyListResponse =
        try {
            objectMapper.readValue(responseBody, WebshareProxyListResponse::class.java)
        } catch (exception: IOException) {
            throw ConnectorProxyException("Failed to parse Webshare proxy list response", exception)
        }

    private fun resolveListUri(apiUrl: String): URI {
        val uri = URI.create(apiUrl.trim())
        val queryParameters = parseQueryParameters(uri.rawQuery).toMutableMap()
        queryParameters.putIfAbsent("mode", "direct")
        queryParameters.putIfAbsent("page", "1")
        queryParameters.putIfAbsent("page_size", "100")
        return URI(
            uri.scheme,
            uri.userInfo,
            uri.host,
            uri.port,
            resolveListPath(uri.path),
            buildQuery(queryParameters),
            uri.fragment,
        )
    }

    private fun resolveListPath(path: String?): String {
        val normalizedPath = path.orEmpty().trimEnd('/')
        val listPath = if (normalizedPath.endsWith(PROXY_LIST_PATH)) {
            normalizedPath
        } else {
            normalizedPath + PROXY_LIST_PATH
        }
        return "$listPath/"
    }

    private fun parseQueryParameters(rawQuery: String?): LinkedHashMap<String, String> {
        val queryParameters = linkedMapOf<String, String>()
        if (rawQuery.isNullOrBlank()) {
            return queryParameters
        }

        rawQuery.split("&")
            .filter(String::isNotBlank)
            .forEach { parameter ->
                val parts = parameter.split("=", limit = 2)
                val name = decode(parts[0])
                val value = if (parts.size == 2) decode(parts[1]) else ""
                queryParameters[name] = value
            }
        return queryParameters
    }

    private fun buildQuery(queryParameters: Map<String, String>): String =
        queryParameters.entries.joinToString("&") { (name, value) ->
            "${encode(name)}=${encode(value)}"
        }

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val PROXY_LIST_PATH = "/api/v2/proxy/list"
    }
}

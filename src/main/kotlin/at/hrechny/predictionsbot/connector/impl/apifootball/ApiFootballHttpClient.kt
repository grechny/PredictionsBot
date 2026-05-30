package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.ApiConnector
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixturesResponse
import at.hrechny.predictionsbot.connector.impl.apifootball.model.RoundsResponse
import at.hrechny.predictionsbot.connector.proxy.ConnectorProxy
import at.hrechny.predictionsbot.connector.proxy.ConnectorProxyManager
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.LoadBalancer
import io.micronaut.http.uri.UriBuilder
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

@Singleton
open class ApiFootballHttpClient(
    @Value("\${connectors.api-football.url}")
    apiFootballUrl: String,
    private val connectorProxyManager: ConnectorProxyManager,
    private val httpClientRegistry: HttpClientRegistry<HttpClient>,
    private val beanContext: BeanContext,
) {
    private val apiFootballBaseUri = URI.create(apiFootballUrl)
    private var activeClient: HttpClient? = null
    private var activeProxySignature: String? = null

    open fun getRounds(
        leagueId: Long,
        season: String,
    ): HttpResponse<RoundsResponse> =
        execute(
            buildRequest(
                "/fixtures/rounds",
                mapOf("league" to leagueId, "season" to season),
            ),
            RoundsResponse::class.java,
        )

    open fun getSeasonFixtures(
        leagueId: Long,
        season: String,
    ): HttpResponse<FixturesResponse> =
        execute(
            buildRequest(
                "/fixtures",
                mapOf("league" to leagueId, "season" to season),
            ),
            FixturesResponse::class.java,
        )

    open fun getFixturesByIds(
        ids: String,
    ): HttpResponse<FixturesResponse> =
        execute(
            buildRequest(
                "/fixtures",
                mapOf("ids" to ids),
            ),
            FixturesResponse::class.java,
        )

    fun buildRequest(path: String, queryParameters: Map<String, Any>): MutableHttpRequest<Any> {
        var uriBuilder = UriBuilder.of(path)
        queryParameters.forEach { (name, value) ->
            uriBuilder = uriBuilder.queryParam(name, value)
        }
        val request = HttpRequest.GET<Any>(uriBuilder.build())
        request.setAttribute(ApiConnector.CONNECTOR_NAME_ATTRIBUTE, ApiFootballConnector.NAME)
        return request
    }

    fun createHttpClientConfiguration(proxy: ConnectorProxy?): DefaultHttpClientConfiguration {
        val configuration = DefaultHttpClientConfiguration()
        if (proxy != null) {
            configuration.setProxyType(Proxy.Type.HTTP)
            configuration.setProxyAddress(InetSocketAddress.createUnresolved(proxy.host, proxy.port))
            configuration.setProxyUsername(proxy.username)
            configuration.setProxyPassword(proxy.password)
        }
        return configuration
    }

    @Synchronized
    private fun currentHttpClient(): HttpClient {
        val proxy = connectorProxyManager.currentProxy(ApiFootballConnector.NAME)
        val proxySignature = proxy?.let { "${it.id}:${it.host}:${it.port}" } ?: DIRECT_PROXY_SIGNATURE
        if (activeClient == null || activeProxySignature != proxySignature) {
            activeClient?.close()
            activeClient = httpClientRegistry.resolveClient(
                null,
                LoadBalancer.fixed(apiFootballBaseUri),
                createHttpClientConfiguration(proxy),
                beanContext,
            )
            activeProxySignature = proxySignature
        }
        return activeClient!!
    }

    private fun <T : Any> execute(request: MutableHttpRequest<Any>, responseType: Class<T>): HttpResponse<T> =
        currentHttpClient().toBlocking().exchange(request, responseType)

    @PreDestroy
    fun close() {
        activeClient?.close()
        activeClient = null
        activeProxySignature = null
    }

    private companion object {
        const val DIRECT_PROXY_SIGNATURE = "direct"
    }
}

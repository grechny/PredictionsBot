package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.proxy.ConnectorProxy
import at.hrechny.predictionsbot.connector.proxy.ConnectorProxyManager
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Value
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.HttpClientRegistry
import io.micronaut.http.client.LoadBalancer
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

@Singleton
open class ApiFootballHttpClientFactory(
    @Value("\${connectors.api-football.url}")
    apiFootballUrl: String,
    private val connectorProxyManager: ConnectorProxyManager,
    private val httpClientRegistry: HttpClientRegistry<HttpClient>,
    private val beanContext: BeanContext,
) {
    private val apiFootballBaseUri = URI.create(apiFootballUrl)
    private var activeClient: HttpClient? = null
    private var activeProxySignature: String? = null

    @Synchronized
    open fun currentClient(): HttpClient {
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

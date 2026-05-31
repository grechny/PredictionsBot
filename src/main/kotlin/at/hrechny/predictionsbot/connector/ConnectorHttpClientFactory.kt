package at.hrechny.predictionsbot.connector

import at.hrechny.predictionsbot.connector.proxy.ConnectorProxy
import at.hrechny.predictionsbot.connector.proxy.ConnectorProxyManager
import io.micronaut.context.BeanContext
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
open class ConnectorHttpClientFactory(
    private val connectorProxyManager: ConnectorProxyManager,
    private val httpClientRegistry: HttpClientRegistry<HttpClient>,
    private val beanContext: BeanContext,
) {
    private val activeClients = mutableMapOf<String, ActiveConnectorHttpClient>()

    @Synchronized
    open fun currentClient(connectorName: String, baseUri: URI): HttpClient {
        val proxy = connectorProxyManager.currentProxy(connectorName)
        val clientSignature = clientSignature(baseUri, proxy)
        val activeClient = activeClients[connectorName]
        if (activeClient == null || activeClient.signature != clientSignature) {
            activeClient?.client?.close()
            val client = httpClientRegistry.resolveClient(
                null,
                LoadBalancer.fixed(baseUri),
                createHttpClientConfiguration(proxy),
                beanContext,
            )
            activeClients[connectorName] = ActiveConnectorHttpClient(client, clientSignature)
            return client
        }
        return activeClient.client
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
        activeClients.values.forEach { activeClient -> activeClient.client.close() }
        activeClients.clear()
    }

    private fun clientSignature(baseUri: URI, proxy: ConnectorProxy?): String {
        val proxySignature = proxy?.let { "${it.id}:${it.host}:${it.port}" } ?: DIRECT_PROXY_SIGNATURE
        return "$baseUri|$proxySignature"
    }

    private data class ActiveConnectorHttpClient(
        val client: HttpClient,
        val signature: String,
    )

    private companion object {
        const val DIRECT_PROXY_SIGNATURE = "direct"
    }
}

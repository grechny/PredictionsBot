package at.hrechny.predictionsbot.config

import io.micronaut.context.annotation.Value
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.client.HttpClientConfiguration
import jakarta.inject.Singleton
import java.net.InetSocketAddress
import java.net.Proxy
import org.slf4j.LoggerFactory

@Singleton
class ConnectorHttpClientProxyConfiguration(
    @param:Value("\${connectors.proxy.host:}")
    private val proxyHost: String,
    @param:Value("\${connectors.proxy.port:0}")
    private val proxyPort: Int,
    @param:Value("\${connectors.proxy.username:}")
    private val proxyUsername: String,
    @param:Value("\${connectors.proxy.password:}")
    private val proxyPassword: String,
) : BeanCreatedEventListener<HttpClientConfiguration> {
    override fun onCreated(event: BeanCreatedEvent<HttpClientConfiguration>): HttpClientConfiguration =
        configure(event.bean)

    fun <T : HttpClientConfiguration> configure(configuration: T): T {
        if (proxyHost.isBlank()) {
            return configuration
        }
        if (proxyPort <= 0) {
            log.warn("Connector HTTP proxy host is configured but proxy port is not valid")
            return configuration
        }

        configuration.setProxyType(Proxy.Type.HTTP)
        configuration.setProxyAddress(InetSocketAddress.createUnresolved(proxyHost, proxyPort))
        if (proxyUsername.isNotBlank()) {
            configuration.setProxyUsername(proxyUsername)
            configuration.setProxyPassword(proxyPassword)
        }
        return configuration
    }

    private companion object {
        val log = LoggerFactory.getLogger(ConnectorHttpClientProxyConfiguration::class.java)
    }
}

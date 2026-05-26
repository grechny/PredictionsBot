package at.hrechny.predictionsbot.connector.impl.apifootball

import io.micronaut.context.annotation.Value
import io.micronaut.context.event.BeanCreatedEvent
import io.micronaut.context.event.BeanCreatedEventListener
import io.micronaut.http.client.HttpClientConfiguration
import io.micronaut.http.client.ServiceHttpClientConfiguration
import jakarta.inject.Singleton
import java.net.InetSocketAddress
import java.net.Proxy
import org.slf4j.LoggerFactory

@Singleton
class ApiFootballHttpClientProxyConfiguration(
    @param:Value("\${connectors.proxy.host:}")
    private val proxyHost: String,
    @param:Value("\${connectors.proxy.port:0}")
    private val proxyPort: Int,
    @param:Value("\${connectors.proxy.username:}")
    private val proxyUsername: String,
    @param:Value("\${connectors.proxy.password:}")
    private val proxyPassword: String,
) : BeanCreatedEventListener<ServiceHttpClientConfiguration> {
    override fun onCreated(event: BeanCreatedEvent<ServiceHttpClientConfiguration>): ServiceHttpClientConfiguration =
        configure(event.bean.serviceId, event.bean)

    fun <T : HttpClientConfiguration> configure(serviceId: String, configuration: T): T {
        if (serviceId != API_FOOTBALL_SERVICE_ID || proxyHost.isBlank()) {
            return configuration
        }
        if (proxyPort <= 0) {
            log.warn("API-Football proxy host is configured but proxy port is not valid")
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
        const val API_FOOTBALL_SERVICE_ID = "api-football"
        val log = LoggerFactory.getLogger(ApiFootballHttpClientProxyConfiguration::class.java)
    }
}

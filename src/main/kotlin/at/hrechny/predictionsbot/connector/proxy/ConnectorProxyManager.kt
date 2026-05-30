package at.hrechny.predictionsbot.connector.proxy

import at.hrechny.predictionsbot.config.ApiFootballConnectorConfiguration
import at.hrechny.predictionsbot.config.ConnectorProxyMode
import at.hrechny.predictionsbot.connector.impl.apifootball.ApiFootballConnector
import at.hrechny.predictionsbot.connector.proxy.webshare.WebshareProxyService
import at.hrechny.predictionsbot.exception.ConnectorProxyException
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
open class ConnectorProxyManager(
    private val apiFootballConnectorConfiguration: ApiFootballConnectorConfiguration,
    private val webshareProxyService: WebshareProxyService,
) {
    private var apiFootballProxy: ConnectorProxy? = null

    @Synchronized
    open fun usesWebshare(connectorName: String): Boolean =
        proxyMode(connectorName) == ConnectorProxyMode.WEBSHARE_IO

    @Synchronized
    open fun initialize(connectorName: String) {
        when (proxyMode(connectorName)) {
            ConnectorProxyMode.NONE -> {
                apiFootballProxy = null
                log.info("Connector {} proxy mode is none", connectorName)
            }
            ConnectorProxyMode.WEBSHARE_IO -> {
                apiFootballProxy = webshareProxyService.selectProxy()
                log.info("Connector {} initialized with Webshare proxy", connectorName)
            }
        }
    }

    @Synchronized
    open fun currentProxy(connectorName: String): ConnectorProxy? {
        return when (proxyMode(connectorName)) {
            ConnectorProxyMode.NONE -> null
            ConnectorProxyMode.WEBSHARE_IO -> apiFootballProxy ?: webshareProxyService.selectProxy()
                .also { proxy -> apiFootballProxy = proxy }
        }
    }

    @Synchronized
    open fun rotate(
        connectorName: String,
        failedProxy: ConnectorProxy?,
        cause: Throwable,
    ): ConnectorProxy {
        if (proxyMode(connectorName) != ConnectorProxyMode.WEBSHARE_IO) {
            throw ConnectorProxyException("Connector $connectorName does not use Webshare proxy")
        }

        val proxyToExclude = failedProxy ?: apiFootballProxy
        val selectedProxy = webshareProxyService.selectProxy(proxyToExclude)
        apiFootballProxy = selectedProxy
        log.warn(
            "Rotated Webshare proxy for connector {} after {}",
            connectorName,
            cause.javaClass.simpleName,
        )
        return selectedProxy
    }

    private fun proxyMode(connectorName: String): ConnectorProxyMode =
        when (connectorName) {
            ApiFootballConnector.NAME -> apiFootballConnectorConfiguration.proxy
            else -> throw ConnectorProxyException("Unknown connector $connectorName")
        }

    private companion object {
        val log = LoggerFactory.getLogger(ConnectorProxyManager::class.java)
    }
}

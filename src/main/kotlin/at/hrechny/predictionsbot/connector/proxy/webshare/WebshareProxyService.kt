package at.hrechny.predictionsbot.connector.proxy.webshare

import at.hrechny.predictionsbot.connector.proxy.ConnectorProxy
import at.hrechny.predictionsbot.connector.proxy.webshare.model.WebshareProxyResponseDto
import at.hrechny.predictionsbot.exception.ConnectorProxyException
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
class WebshareProxyService(
    private val webshareProxyClient: WebshareProxyClient,
) {
    @JvmOverloads
    fun selectProxy(excludedProxy: ConnectorProxy? = null): ConnectorProxy {
        val validProxies = webshareProxyClient.listDirectProxies()
            .asSequence()
            .filter { proxy -> proxy.isValidProxy() }
            .filterNot { proxy -> proxy.matches(excludedProxy) }
            .toList()

        if (validProxies.isEmpty()) {
            throw ConnectorProxyException("No valid Webshare proxies are available")
        }

        val selected = validProxies
            .filter { proxy -> proxy.countryCode?.uppercase() in EUROPEAN_COUNTRY_CODES }
            .minByOrNull { proxy -> EUROPEAN_COUNTRY_CODES.indexOf(proxy.countryCode?.uppercase()) }
            ?: validProxies.first()
        val connectorProxy = selected.toConnectorProxy()
        log.info(
            "Selected Webshare proxy country={} id={}",
            connectorProxy.countryCode ?: "unknown",
            connectorProxy.id,
        )
        return connectorProxy
    }

    private fun WebshareProxyResponseDto.isValidProxy(): Boolean =
        valid == true &&
            !proxyAddress.isNullOrBlank() &&
            port != null &&
            port!! > 0 &&
            !username.isNullOrBlank() &&
            !password.isNullOrBlank()

    private fun WebshareProxyResponseDto.matches(excludedProxy: ConnectorProxy?): Boolean {
        if (excludedProxy == null) {
            return false
        }
        val proxyId = id ?: "${proxyAddress}:${port}"
        return proxyId == excludedProxy.id || (proxyAddress == excludedProxy.host && port == excludedProxy.port)
    }

    private fun WebshareProxyResponseDto.toConnectorProxy(): ConnectorProxy =
        ConnectorProxy(
            id = id ?: "${proxyAddress}:${port}",
            host = proxyAddress!!,
            port = port!!,
            username = username!!,
            password = password!!,
            countryCode = countryCode,
        )

    private companion object {
        val EUROPEAN_COUNTRY_CODES = listOf(
            "AT",
            "BE",
            "BG",
            "HR",
            "CY",
            "CZ",
            "DK",
            "EE",
            "FI",
            "FR",
            "DE",
            "GR",
            "HU",
            "IE",
            "IT",
            "LV",
            "LT",
            "LU",
            "MT",
            "NL",
            "PL",
            "PT",
            "RO",
            "SK",
            "SI",
            "ES",
            "SE",
            "GB",
            "NO",
            "CH",
        )
        val log = LoggerFactory.getLogger(WebshareProxyService::class.java)
    }
}

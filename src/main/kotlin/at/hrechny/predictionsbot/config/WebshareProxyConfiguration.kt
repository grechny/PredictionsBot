package at.hrechny.predictionsbot.config

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("proxy.webshare")
class WebshareProxyConfiguration {
    var apiKey: String = ""
    var apiUrl: String = ""
}

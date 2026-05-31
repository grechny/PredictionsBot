package at.hrechny.predictionsbot.config

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("connectors.api-football")
class ApiFootballConnectorConfiguration {
    var url: String = ""
    var apiKey: String = ""
    var maxAttempts: Int = 0
    var dayStarts: String = "00:00"
    var minInterval: Int = 60
    var fixtureBatchSize: Int = 20
    var proxy: ConnectorProxyMode = ConnectorProxyMode.NONE
}

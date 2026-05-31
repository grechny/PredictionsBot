package at.hrechny.predictionsbot.connector.proxy

data class ConnectorProxy(
    val id: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val countryCode: String?,
)

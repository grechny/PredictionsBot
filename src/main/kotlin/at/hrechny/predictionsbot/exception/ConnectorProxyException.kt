package at.hrechny.predictionsbot.exception

class ConnectorProxyException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

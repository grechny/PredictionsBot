package at.hrechny.predictionsbot.exception

class ConnectorProxyException @JvmOverloads constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

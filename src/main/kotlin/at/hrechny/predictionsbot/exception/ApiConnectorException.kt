package at.hrechny.predictionsbot.exception

class ApiConnectorException(
    val connectorName: String,
    val reason: Reason,
    details: String? = null,
    cause: Throwable? = null,
) : RuntimeException(listOfNotNull("$connectorName: $reason", details).joinToString(": "), cause) {
    enum class Reason {
        REQUEST_ERROR,
        INVALID_RESPONSE,
        TOO_OFTEN_REQUESTS,
        QUOTA_EXCEEDED,
        MISSING_DATA,
    }
}

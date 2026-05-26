package at.hrechny.predictionsbot.exception

class ApiConnectorException(
    val connectorCode: String,
    val reason: Reason,
    details: String? = null,
    cause: Throwable? = null,
) : RuntimeException(listOfNotNull("$connectorCode: $reason", details).joinToString(": "), cause) {
    enum class Reason {
        REQUEST_ERROR,
        INVALID_RESPONSE,
        TOO_OFTEN_REQUESTS,
        QUOTA_EXCEEDED,
        MISSING_DATA,
    }
}

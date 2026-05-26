package at.hrechny.predictionsbot.connector.apifootball.exception

class ApiFootballConnectorException(
    val reason: Reason,
    details: String? = null,
    cause: Throwable? = null,
) : RuntimeException(listOfNotNull(reason.toString(), details).joinToString(": "), cause) {
    enum class Reason {
        TOO_OFTEN_REQUESTS,
        QUOTA_EXCEEDED,
        REQUEST_ERROR,
        INVALID_RESPONSE,
    }
}

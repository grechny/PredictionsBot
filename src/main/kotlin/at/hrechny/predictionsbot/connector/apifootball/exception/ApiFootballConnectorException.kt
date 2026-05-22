package at.hrechny.predictionsbot.connector.apifootball.exception

class ApiFootballConnectorException(reason: Reason) : RuntimeException(reason.toString()) {
    enum class Reason {
        TOO_OFTEN_REQUESTS,
        QUOTA_EXCEEDED,
        REQUEST_ERROR,
        INVALID_RESPONSE,
    }
}

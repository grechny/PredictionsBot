package at.hrechny.predictionsbot.connector.football

import at.hrechny.predictionsbot.connector.football.model.FootballProviderId

class FootballDataProviderException(
    val providerId: FootballProviderId,
    val reason: Reason,
    details: String? = null,
    cause: Throwable? = null,
) : RuntimeException(listOfNotNull("${providerId.value}: $reason", details).joinToString(": "), cause) {
    enum class Reason {
        REQUEST_ERROR,
        INVALID_RESPONSE,
        TOO_OFTEN_REQUESTS,
        QUOTA_EXCEEDED,
        MISSING_DATA,
    }
}

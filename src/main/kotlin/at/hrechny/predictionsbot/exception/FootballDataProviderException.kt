package at.hrechny.predictionsbot.exception

import at.hrechny.predictionsbot.model.football.FootballDataProviderId

class FootballDataProviderException(
    val providerId: FootballDataProviderId,
    val reason: Reason,
    details: String? = null,
    cause: Throwable? = null,
) : RuntimeException(listOfNotNull("${providerId.value}: $reason", details).joinToString(": "), cause) {
    constructor(
        providerCode: String,
        reason: Reason,
        details: String? = null,
        cause: Throwable? = null,
    ) : this(FootballDataProviderId(providerCode), reason, details, cause)

    enum class Reason {
        REQUEST_ERROR,
        INVALID_RESPONSE,
        TOO_OFTEN_REQUESTS,
        QUOTA_EXCEEDED,
        MISSING_DATA,
    }
}

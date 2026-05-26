package at.hrechny.predictionsbot.connector

import at.hrechny.predictionsbot.model.FootballDataProviderId

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

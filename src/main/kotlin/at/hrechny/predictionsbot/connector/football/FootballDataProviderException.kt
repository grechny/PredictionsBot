package at.hrechny.predictionsbot.connector.football

import at.hrechny.predictionsbot.model.ExternalApiProviderId

class FootballDataProviderException(
    val providerId: ExternalApiProviderId,
    val reason: Reason,
    details: String? = null,
    cause: Throwable? = null,
) : RuntimeException(listOfNotNull("${providerId.value}: $reason", details).joinToString(": "), cause) {
    constructor(
        providerCode: String,
        reason: Reason,
        details: String? = null,
        cause: Throwable? = null,
    ) : this(ExternalApiProviderId(providerCode), reason, details, cause)

    enum class Reason {
        REQUEST_ERROR,
        INVALID_RESPONSE,
        TOO_OFTEN_REQUESTS,
        QUOTA_EXCEEDED,
        MISSING_DATA,
    }
}

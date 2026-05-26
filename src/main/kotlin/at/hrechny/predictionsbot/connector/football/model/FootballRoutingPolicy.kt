package at.hrechny.predictionsbot.connector.football.model

import at.hrechny.predictionsbot.model.ExternalApiProviderId

data class FootballRoutingPolicy(
    val fieldPriority: Map<FootballDataType, List<ExternalApiProviderId>> = emptyMap(),
    val fallbackOrder: List<ExternalApiProviderId> = emptyList(),
    val quotaWindows: Map<ExternalApiProviderId, List<FootballQuotaWindowDefinition>> = emptyMap(),
    val health: Map<ExternalApiProviderId, FootballProviderHealthSnapshot> = emptyMap(),
    val mergePolicy: FootballMergePolicy = FootballMergePolicy(),
)

data class FootballProviderHealthSnapshot(
    val status: FootballProviderHealthStatus,
    val derivedFromRecentAudit: Boolean = true,
    val reason: String? = null,
)

data class FootballMergePolicy(
    val strategy: FootballMergeStrategy = FootballMergeStrategy.FALLBACK_THEN_FIELD_PRIORITY,
    val fieldOwners: Map<FootballDataType, ExternalApiProviderId> = emptyMap(),
)

enum class FootballProviderHealthStatus {
    HEALTHY,
    DEGRADED,
    UNAVAILABLE,
    QUOTA_EXHAUSTED,
    UNKNOWN,
}

enum class FootballMergeStrategy {
    PRIMARY_ONLY,
    FALLBACK_THEN_FIELD_PRIORITY,
    FIELD_OWNER,
}

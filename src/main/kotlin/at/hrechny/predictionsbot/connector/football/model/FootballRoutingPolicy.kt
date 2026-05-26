package at.hrechny.predictionsbot.connector.football.model

data class FootballRoutingPolicy(
    val fieldPriority: Map<FootballDataType, List<FootballProviderId>> = emptyMap(),
    val fallbackOrder: List<FootballProviderId> = emptyList(),
    val quotaWindows: Map<FootballProviderId, List<FootballQuotaWindowDefinition>> = emptyMap(),
    val health: Map<FootballProviderId, FootballProviderHealthSnapshot> = emptyMap(),
    val mergePolicy: FootballMergePolicy = FootballMergePolicy(),
)

data class FootballProviderHealthSnapshot(
    val status: FootballProviderHealthStatus,
    val derivedFromRecentAudit: Boolean = true,
    val reason: String? = null,
)

data class FootballMergePolicy(
    val strategy: FootballMergeStrategy = FootballMergeStrategy.FALLBACK_THEN_FIELD_PRIORITY,
    val fieldOwners: Map<FootballDataType, FootballProviderId> = emptyMap(),
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

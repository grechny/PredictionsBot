package at.hrechny.predictionsbot.model.football

data class FootballRoutingPolicy(
    val fieldPriority: Map<FootballDataType, List<FootballDataProviderId>> = emptyMap(),
    val fallbackOrder: List<FootballDataProviderId> = emptyList(),
    val quotaWindows: Map<FootballDataProviderId, List<FootballQuotaWindowDefinition>> = emptyMap(),
    val health: Map<FootballDataProviderId, FootballProviderHealthSnapshot> = emptyMap(),
    val mergePolicy: FootballMergePolicy = FootballMergePolicy(),
)

data class FootballProviderHealthSnapshot(
    val status: FootballProviderHealthStatus,
    val derivedFromRecentAudit: Boolean = true,
    val reason: String? = null,
)

data class FootballMergePolicy(
    val strategy: FootballMergeStrategy = FootballMergeStrategy.FALLBACK_THEN_FIELD_PRIORITY,
    val fieldOwners: Map<FootballDataType, FootballDataProviderId> = emptyMap(),
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

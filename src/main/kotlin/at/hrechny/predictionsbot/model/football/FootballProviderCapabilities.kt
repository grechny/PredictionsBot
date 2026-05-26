package at.hrechny.predictionsbot.model.football

data class FootballProviderCapabilities(
    val providerId: FootballDataProviderId,
    val dataTypes: Set<FootballDataType>,
    val competitions: Set<String> = emptySet(),
    val freshness: FootballFreshness = FootballFreshness.UNKNOWN,
    val quotaWindows: List<FootballQuotaWindowDefinition> = emptyList(),
    val supportsLiveScores: Boolean = false,
)

data class FootballQuotaWindowDefinition(
    val window: FootballQuotaWindow,
    val maxRequests: Int,
)

enum class FootballDataType {
    ROUNDS,
    SEASON_FIXTURES,
    LIVE_FIXTURES,
    SCORES,
    STANDINGS,
    TEAMS,
    PROVIDER_METADATA,
}

enum class FootballFreshness {
    LIVE,
    NEAR_REAL_TIME,
    DELAYED,
    HISTORICAL,
    UNKNOWN,
}

enum class FootballQuotaWindow {
    MINUTE,
    HOUR,
    DAY,
    MONTH,
}

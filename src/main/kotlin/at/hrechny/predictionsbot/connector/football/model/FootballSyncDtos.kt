package at.hrechny.predictionsbot.connector.football.model

import java.time.Instant

data class FootballCompetitionSeasonRef(
    val providerId: FootballProviderId,
    val competitionExternalId: String,
    val seasonYear: String,
)

data class FootballRoundSyncDto(
    val externalId: String,
    val name: String,
    val orderNumber: Int? = null,
    val type: FootballRoundType? = null,
)

data class FootballTeamSyncDto(
    val externalId: String,
    val name: String?,
    val logoUrl: String? = null,
)

data class FootballFixtureSyncDto(
    val externalId: String,
    val roundExternalId: String,
    val startTime: Instant?,
    val status: FootballFixtureStatus,
    val homeTeam: FootballTeamSyncDto,
    val awayTeam: FootballTeamSyncDto,
    val score: FootballScoreSyncDto,
)

data class FootballScoreSyncDto(
    val home: Int? = null,
    val away: Int? = null,
)

data class FootballStandingSyncDto(
    val competitionExternalId: String,
    val seasonYear: String,
    val team: FootballTeamSyncDto,
    val position: Int? = null,
    val points: Int? = null,
    val played: Int? = null,
    val won: Int? = null,
    val drawn: Int? = null,
    val lost: Int? = null,
    val goalsFor: Int? = null,
    val goalsAgainst: Int? = null,
)

enum class FootballFixtureStatus {
    PLANNED,
    STARTED,
    NOT_DEFINED,
    FINISHED,
}

enum class FootballRoundType {
    QUALIFYING,
    SEASON,
    GROUP_STAGE,
    ROUND_OF_32,
    ROUND_OF_32_RETURN,
    ROUND_OF_16,
    ROUND_OF_16_RETURN,
    QUARTER_FINAL,
    QUARTER_FINAL_RETURN,
    SEMI_FINAL,
    SEMI_FINAL_RETURN,
    THIRD_PLACE_FINAL,
    FINAL,
}

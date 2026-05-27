package at.hrechny.predictionsbot.connector.model

import java.time.Instant

data class FixtureSyncDto(
    val externalId: String,
    val roundExternalId: String,
    val startTime: Instant?,
    val status: FixtureSyncStatus,
    val homeTeam: TeamSyncDto,
    val awayTeam: TeamSyncDto,
    val score: ScoreSyncDto,
)

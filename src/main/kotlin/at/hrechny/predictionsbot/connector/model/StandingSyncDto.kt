package at.hrechny.predictionsbot.connector.model

data class StandingSyncDto(
    val competitionExternalId: String,
    val seasonYear: String,
    val team: TeamSyncDto,
    val position: Int? = null,
    val points: Int? = null,
    val played: Int? = null,
    val won: Int? = null,
    val drawn: Int? = null,
    val lost: Int? = null,
    val goalsFor: Int? = null,
    val goalsAgainst: Int? = null,
)

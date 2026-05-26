package at.hrechny.predictionsbot.connector.model

data class TeamSyncDto(
    val externalId: String,
    val name: String?,
    val logoUrl: String? = null,
)

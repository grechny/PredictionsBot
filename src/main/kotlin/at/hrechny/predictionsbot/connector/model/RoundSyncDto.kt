package at.hrechny.predictionsbot.connector.model

data class RoundSyncDto(
    val externalId: String,
    val name: String,
    val orderNumber: Int? = null,
    val type: RoundSyncType? = null,
)

package at.hrechny.predictionsbot.connector.model

data class RoundSyncDto(
    val name: String,
    val orderNumber: Int? = null,
    val types: List<RoundSyncType>,
)

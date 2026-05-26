package at.hrechny.predictionsbot.connector.football.model

@JvmInline
value class FootballProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider id must not be blank" }
    }

    companion object {
        val API_FOOTBALL = FootballProviderId("api-football")
    }
}

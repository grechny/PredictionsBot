package at.hrechny.predictionsbot.connector.football.model

data class FootballProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Provider id must not be blank" }
    }

    companion object {
        val API_FOOTBALL = FootballProviderId("api-football")

        @JvmStatic
        fun apiFootballCode(): String = API_FOOTBALL.value
    }
}

package at.hrechny.predictionsbot.model

data class FootballDataProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "Football data provider id must not be blank" }
    }

    companion object {
        val API_FOOTBALL = FootballDataProviderId("api-football")
    }
}

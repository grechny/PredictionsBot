package at.hrechny.predictionsbot.model

data class ExternalApiProviderId(val value: String) {
    init {
        require(value.isNotBlank()) { "External API provider id must not be blank" }
    }

    companion object {
        val API_FOOTBALL = ExternalApiProviderId("api-football")
    }
}

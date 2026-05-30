package at.hrechny.predictionsbot.connector.impl.apifootball.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
class Status {
    @field:JsonProperty("long")
    var statusLong: String? = null

    @field:JsonProperty("short")
    var status: FixtureStatusEnum? = null

    var elapsed: Int? = null
}

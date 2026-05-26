package at.hrechny.predictionsbot.connector.impl.apifootball.model

import com.fasterxml.jackson.annotation.JsonProperty

class Status {
    @field:JsonProperty("long")
    var statusLong: String? = null

    @field:JsonProperty("short")
    var status: FixtureStatusEnum? = null

    var elapsed: Int? = null
}

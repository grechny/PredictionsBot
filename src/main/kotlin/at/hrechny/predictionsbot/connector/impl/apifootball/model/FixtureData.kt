package at.hrechny.predictionsbot.connector.impl.apifootball.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.OffsetDateTime

@JsonIgnoreProperties(ignoreUnknown = true)
class FixtureData {
    var id: Long? = null
    var date: OffsetDateTime? = null
    var status: Status? = null
}

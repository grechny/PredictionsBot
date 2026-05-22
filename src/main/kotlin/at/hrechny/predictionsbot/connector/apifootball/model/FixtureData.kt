package at.hrechny.predictionsbot.connector.apifootball.model

import java.time.OffsetDateTime

class FixtureData {
    var id: Long? = null
    var date: OffsetDateTime? = null
    var status: Status? = null
}

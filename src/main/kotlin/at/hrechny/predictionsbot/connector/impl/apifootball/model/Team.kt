package at.hrechny.predictionsbot.connector.impl.apifootball.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class Team {
    var id: Long? = null
    var name: String? = null
    var logo: String? = null
    var winner: Boolean? = null
}

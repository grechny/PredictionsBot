package at.hrechny.predictionsbot.connector.impl.apifootball.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class TeamsData {
    var home: Team? = null
    var away: Team? = null
}

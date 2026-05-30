package at.hrechny.predictionsbot.connector.impl.apifootball.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
class ScoreDetail {
    var halftime: Score? = null
    var fulltime: Score? = null
    var extratime: Score? = null
    var penalty: Score? = null
}

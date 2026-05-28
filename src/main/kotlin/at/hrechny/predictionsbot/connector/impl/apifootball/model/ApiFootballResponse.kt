package at.hrechny.predictionsbot.connector.impl.apifootball.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
open class ApiFootballResponse<T> {
    var errors: List<String> = emptyList()
    var response: List<T>? = null
}

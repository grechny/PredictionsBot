package at.hrechny.predictionsbot.connector.impl.apifootball.model

open class ApiFootballResponse<T> {
    var errors: List<String> = emptyList()
    var response: List<T>? = null
}

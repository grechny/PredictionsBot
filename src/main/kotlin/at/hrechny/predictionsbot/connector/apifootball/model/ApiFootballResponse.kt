package at.hrechny.predictionsbot.connector.apifootball.model

open class ApiFootballResponse<T> {
    var errors: List<String> = emptyList()
    var response: List<T>? = null
}

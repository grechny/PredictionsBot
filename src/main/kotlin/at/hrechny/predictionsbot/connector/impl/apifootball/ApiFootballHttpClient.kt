package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.ApiConnector
import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixturesResponse
import at.hrechny.predictionsbot.connector.impl.apifootball.model.RoundsResponse
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.uri.UriBuilder
import jakarta.inject.Singleton

@Singleton
open class ApiFootballHttpClient(
    private val apiFootballHttpClientFactory: ApiFootballHttpClientFactory,
) {
    open fun getRounds(
        leagueId: Long,
        season: String,
    ): HttpResponse<RoundsResponse> =
        execute(
            buildRequest(
                "/fixtures/rounds",
                mapOf("league" to leagueId, "season" to season),
            ),
            RoundsResponse::class.java,
        )

    open fun getSeasonFixtures(
        leagueId: Long,
        season: String,
    ): HttpResponse<FixturesResponse> =
        execute(
            buildRequest(
                "/fixtures",
                mapOf("league" to leagueId, "season" to season),
            ),
            FixturesResponse::class.java,
        )

    open fun getFixturesByIds(
        ids: String,
    ): HttpResponse<FixturesResponse> =
        execute(
            buildRequest(
                "/fixtures",
                mapOf("ids" to ids),
            ),
            FixturesResponse::class.java,
        )

    fun buildRequest(path: String, queryParameters: Map<String, Any>): MutableHttpRequest<Any> {
        var uriBuilder = UriBuilder.of(path)
        queryParameters.forEach { (name, value) ->
            uriBuilder = uriBuilder.queryParam(name, value)
        }
        val request = HttpRequest.GET<Any>(uriBuilder.build())
        request.setAttribute(ApiConnector.CONNECTOR_NAME_ATTRIBUTE, ApiFootballConnector.NAME)
        return request
    }

    private fun <T : Any> execute(request: MutableHttpRequest<Any>, responseType: Class<T>): HttpResponse<T> =
        apiFootballHttpClientFactory.currentClient().toBlocking().exchange(request, responseType)
}

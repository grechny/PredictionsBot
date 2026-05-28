package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.ApiConnector
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestAttribute
import io.micronaut.http.client.annotation.Client

@Client(value = "\${connectors.api-football.url}", path = "/v3")
@RequestAttribute(name = ApiConnector.CONNECTOR_NAME_ATTRIBUTE, value = ApiFootballConnector.NAME)
interface ApiFootballHttpClient {
    @Get("/fixtures/rounds{?league,season}")
    fun getRounds(
        @QueryValue("league") leagueId: Long,
        @QueryValue season: String,
    ): HttpResponse<String>

    @Get("/fixtures{?league,season}")
    fun getSeasonFixtures(
        @QueryValue("league") leagueId: Long,
        @QueryValue season: String,
    ): HttpResponse<String>

    @Get("/fixtures{?ids}")
    fun getFixturesByIds(
        @QueryValue ids: String,
    ): HttpResponse<String>
}

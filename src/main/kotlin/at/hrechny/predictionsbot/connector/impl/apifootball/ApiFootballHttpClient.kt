package at.hrechny.predictionsbot.connector.impl.apifootball

import at.hrechny.predictionsbot.connector.impl.apifootball.model.FixturesResponse
import at.hrechny.predictionsbot.connector.impl.apifootball.model.RoundsResponse
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client

@Client(value = "\${connectors.api-football.url}", path = "/v3")
interface ApiFootballHttpClient {
    @Get("/fixtures/rounds{?league,season}")
    fun getRounds(
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") host: String,
        @QueryValue("league") leagueId: Long,
        @QueryValue season: String,
    ): HttpResponse<RoundsResponse>

    @Get("/fixtures{?league,season}")
    fun getSeasonFixtures(
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") host: String,
        @QueryValue("league") leagueId: Long,
        @QueryValue season: String,
    ): HttpResponse<FixturesResponse>

    @Get("/fixtures{?ids}")
    fun getFixturesByIds(
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") host: String,
        @QueryValue ids: String,
    ): HttpResponse<FixturesResponse>
}

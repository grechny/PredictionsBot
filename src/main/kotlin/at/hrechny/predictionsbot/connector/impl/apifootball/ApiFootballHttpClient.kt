package at.hrechny.predictionsbot.connector.impl.apifootball

import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.client.annotation.Client

@Client("api-football")
interface ApiFootballHttpClient {
    @Get("/fixtures/rounds{?league,season}")
    fun getRounds(
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") host: String,
        @QueryValue league: Long,
        @QueryValue season: String,
    ): HttpResponse<String>

    @Get("/fixtures{?league,season}")
    fun getSeasonFixtures(
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") host: String,
        @QueryValue league: Long,
        @QueryValue season: String,
    ): HttpResponse<String>

    @Get("/fixtures{?ids}")
    fun getFixturesByIds(
        @Header("X-RapidAPI-Key") apiKey: String,
        @Header("X-RapidAPI-Host") host: String,
        @QueryValue ids: String,
    ): HttpResponse<String>
}

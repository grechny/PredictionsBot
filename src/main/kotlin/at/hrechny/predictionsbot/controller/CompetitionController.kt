package at.hrechny.predictionsbot.controller

import at.hrechny.predictionsbot.exception.RequestValidationException
import at.hrechny.predictionsbot.model.Competition
import at.hrechny.predictionsbot.model.Season
import at.hrechny.predictionsbot.service.predictor.CompetitionService
import at.hrechny.predictionsbot.service.telegram.TelegramService
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import jakarta.validation.Valid
import java.util.UUID

@Controller
open class CompetitionController(
    private val competitionService: CompetitionService,
    private val telegramService: TelegramService,
) {
    @Post(
        value = "/\${secrets.adminKey:}/competitions",
        consumes = [MediaType.APPLICATION_JSON],
        produces = [MediaType.APPLICATION_JSON],
    )
    open fun addCompetition(@Valid @Body competition: Competition): HttpResponse<Map<String, UUID>> {
        if (competition.id != null) {
            throw RequestValidationException("Setting of competition id is not allowed")
        }

        val id = competitionService.addCompetition(competition)
        telegramService.sendCompetition(id)
        return HttpResponse.ok(mapOf("id" to id))
    }

    @Get(value = "/\${secrets.adminKey:}/competitions", produces = [MediaType.APPLICATION_JSON])
    open fun getCompetitions(): HttpResponse<List<Competition>> = HttpResponse.ok(competitionService.getCompetitions())

    @Post(
        value = "/\${secrets.adminKey:}/competitions/{competitionId}/seasons",
        consumes = [MediaType.APPLICATION_JSON],
        produces = [MediaType.APPLICATION_JSON],
    )
    open fun addSeason(
        @PathVariable("competitionId") competitionId: UUID,
        @Valid @Body season: Season,
    ): HttpResponse<Map<String, UUID>> {
        if (season.id != null) {
            throw RequestValidationException("Setting of season id is not allowed")
        }

        val id = competitionService.addSeason(competitionId, season)
        telegramService.pushUpdate(competitionId)
        return HttpResponse.ok(mapOf("id" to id))
    }

    @Get(value = "/\${secrets.adminKey:}/competitions/{competitionId}/seasons", produces = [MediaType.APPLICATION_JSON])
    open fun getSeasons(@PathVariable("competitionId") competitionId: UUID): HttpResponse<List<Season>> =
        HttpResponse.ok(competitionService.getSeasons(competitionId))

    @Put(value = "/\${secrets.adminKey:}/competitions/{competitionId}/seasons/{seasonId}", consumes = [MediaType.APPLICATION_JSON])
    open fun updateSeason(
        @PathVariable("competitionId") competitionId: UUID,
        @PathVariable("seasonId") seasonId: UUID,
        @Valid @Body season: Season,
    ): HttpResponse<Void> {
        if (season.id != null && season.id != seasonId) {
            throw RequestValidationException("Season ID cannot be updated")
        }

        season.id = seasonId
        competitionService.updateSeason(competitionId, season)
        telegramService.pushUpdate(competitionId)
        return HttpResponse.ok()
    }

    @Post(value = "/\${secrets.adminKey:}/fixtures")
    open fun refreshFixtures(): HttpResponse<Void> {
        competitionService.getActiveSeasons().forEach(competitionService::refreshFixtures)
        return HttpResponse.ok()
    }

    @Post(value = "/\${secrets.adminKey:}/fixtures/{competitionId}")
    open fun refreshFixtures(@PathVariable("competitionId") competitionId: UUID): HttpResponse<Void> {
        val season = competitionService.getCurrentSeason(competitionId)
        competitionService.refreshFixtures(season)
        return HttpResponse.ok()
    }
}

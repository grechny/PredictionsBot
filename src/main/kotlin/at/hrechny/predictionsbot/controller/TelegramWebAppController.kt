package at.hrechny.predictionsbot.controller

import at.hrechny.predictionsbot.config.MessageResolver
import at.hrechny.predictionsbot.database.entity.MatchEntity
import at.hrechny.predictionsbot.database.entity.RoundEntity
import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.database.model.MatchStatus
import at.hrechny.predictionsbot.exception.InputValidationException
import at.hrechny.predictionsbot.exception.LimitExceededException
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport
import at.hrechny.predictionsbot.model.LeagueRequest
import at.hrechny.predictionsbot.model.LeagueResponse
import at.hrechny.predictionsbot.model.Result
import at.hrechny.predictionsbot.service.predictor.CompetitionService
import at.hrechny.predictionsbot.service.predictor.LeagueService
import at.hrechny.predictionsbot.service.predictor.PredictionService
import at.hrechny.predictionsbot.service.predictor.UserService
import at.hrechny.predictionsbot.util.HashUtils
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Put
import io.micronaut.http.annotation.QueryValue
import io.micronaut.views.ModelAndView
import org.apache.commons.collections4.CollectionUtils
import java.util.Comparator
import java.util.Locale
import java.util.UUID

@Controller
@EnableErrorReport
open class TelegramWebAppController(
    private val competitionService: CompetitionService,
    private val predictionService: PredictionService,
    private val leagueService: LeagueService,
    private val userService: UserService,
    private val hashUtils: HashUtils,
    private val messageResolver: MessageResolver,
) {
    @Value("\${application.url}")
    private lateinit var applicationUrl: String

    @Get(value = "/webapp/{hash}/users/{userId}/predictions", produces = [MediaType.TEXT_HTML])
    open fun getPredictions(
        @PathVariable("hash") hash: String,
        @PathVariable("userId") userId: Long,
        @QueryValue("competitionId") competitionId: UUID,
        @Nullable @QueryValue("round") roundNumber: Int?,
    ): ModelAndView<Map<String, Any>> {
        val user = userService.getUser(userId)
        val round = if (roundNumber == null || roundNumber == 0) {
            competitionService.getUpcomingRound(competitionId)
        } else {
            competitionService.getRound(competitionId, roundNumber)
        }

        if (round == null || CollectionUtils.isEmpty(round.matches)) {
            val model = webModel(user)
            model["competitionName"] = competitionService.getCompetition(competitionId).name!!
            return ModelAndView("no-upcoming-matches", model)
        }

        val season = round.season!!
        val rounds = season.rounds
            .filter { roundEntity -> roundEntity.matches.isNotEmpty() }
            .filter { roundEntity -> roundEntity.orderNumber != 0 }
            .distinctBy(RoundEntity::orderNumber)
            .sortedBy(RoundEntity::orderNumber)
        val fixtures = season.rounds
            .filter { roundEntity -> roundEntity.orderNumber == round.orderNumber }
            .flatMap { roundEntity -> roundEntity.matches }
            .sortedWith(compareBy(Comparator.nullsLast(Comparator.naturalOrder())) { match -> match.startTime })

        val model = webModel(user)
        model["user"] = user
        model["fixtures"] = fixtures
        model["rounds"] = rounds
        model["baseUrl"] = buildBaseUrl("predictions", userId, competitionId, null)
        return ModelAndView("predictions", model)
    }

    @Get(value = "/webapp/{hash}/users/{userId}/results", produces = [MediaType.TEXT_HTML])
    open fun getResults(
        @PathVariable("hash") hash: String,
        @PathVariable("userId") userId: Long,
        @QueryValue("competitionId") competitionId: UUID,
        @Nullable @QueryValue("seasonId") seasonId: UUID?,
        @Nullable @QueryValue("round") roundNumber: Int?,
    ): ModelAndView<Map<String, Any>> {
        val user = userService.getUser(userId)
        val season = if (seasonId != null) {
            competitionService.getSeason(seasonId)
        } else {
            competitionService.getCurrentSeason(competitionId)
        }
        competitionService.refreshActiveFixtures(season.id!!)

        val results: List<Result>
        val matches: List<MatchEntity>
        if (roundNumber != null && roundNumber != 0) {
            matches = season.rounds
                .filter { roundEntity -> roundNumber == roundEntity.orderNumber }
                .flatMap { roundEntity -> roundEntity.matches }
                .filter { match -> match.status in setOf(MatchStatus.STARTED, MatchStatus.FINISHED) }
                .filter { match -> CollectionUtils.isNotEmpty(match.predictions) }
                .sortedWith(compareBy(Comparator.nullsLast(Comparator.naturalOrder())) { match -> match.startTime })
            results = predictionService.getResults(matches)
        } else {
            results = predictionService.getResults(season.id!!)
            matches = season.rounds
                .flatMap { roundEntity -> roundEntity.matches }
                .filter { match -> match.status in setOf(MatchStatus.STARTED, MatchStatus.FINISHED) }
                .filter { match -> CollectionUtils.isNotEmpty(match.predictions) }
                .sortedWith(compareBy(Comparator.nullsLast(Comparator.reverseOrder())) { match -> match.startTime })
                .take(10)
        }

        val matchResults = matches.associate { match ->
            match.id.toString() to predictionService.getResults(listOf(match))
        }
        val rounds = season.rounds
            .flatMap { roundEntity -> roundEntity.matches }
            .filter { match -> match.status in setOf(MatchStatus.STARTED, MatchStatus.FINISHED) }
            .filter { match -> CollectionUtils.isNotEmpty(match.predictions) }
            .map { match -> match.round!! }
            .distinctBy(RoundEntity::orderNumber)
            .sortedBy(RoundEntity::orderNumber)
            .distinct()

        if (CollectionUtils.isEmpty(rounds)) {
            val model = webModel(user)
            model["competitionName"] = competitionService.getCompetition(competitionId).name!!
            return ModelAndView("no-results", model)
        }

        val model = webModel(user)
        model["user"] = user
        model["results"] = results
        model["rounds"] = rounds
        if (roundNumber != null) {
            model["activeRound"] = roundNumber
        }
        model["matches"] = matches
        model["matchResults"] = matchResults
        model["competitionName"] = competitionService.getCompetition(competitionId).name!!
        model["baseUrl"] = buildBaseUrl("results", userId, competitionId, seasonId)

        return ModelAndView("results", model)
    }

    @Get(value = "/webapp/{hash}/users/{userId}/leagues", produces = [MediaType.TEXT_HTML])
    open fun getLeagues(
        @PathVariable("hash") hash: String,
        @PathVariable("userId") userId: Long,
    ): ModelAndView<Map<String, Any>> {
        val user = userService.getUser(userId)
        val model = webModel(user)
        model["user"] = user
        return ModelAndView("leagues", model)
    }

    @Post(value = "/webapp/{hash}/users/{userId}/leagues", produces = [MediaType.APPLICATION_JSON])
    open fun createLeague(
        @PathVariable("hash") hash: String,
        @PathVariable("userId") userId: Long,
        @Body leagueRequest: LeagueRequest,
    ): HttpResponse<LeagueResponse> =
        try {
            HttpResponse.ok(leagueService.create(userId, leagueRequest))
        } catch (inputValidationException: InputValidationException) {
            HttpResponse.badRequest()
        } catch (limitExceededException: LimitExceededException) {
            HttpResponse.status(HttpStatus.CONFLICT)
        }

    @Put(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = [MediaType.APPLICATION_JSON])
    open fun updateLeague(
        @PathVariable("hash") hash: String,
        @PathVariable("userId") userId: Long,
        @PathVariable("leagueId") leagueId: UUID,
        @Body leagueRequest: LeagueRequest,
    ): HttpResponse<LeagueResponse> =
        try {
            val response = leagueService.update(userId, leagueId, leagueRequest)
            if (response != null) HttpResponse.ok(response) else HttpResponse.ok()
        } catch (inputValidationException: InputValidationException) {
            HttpResponse.badRequest()
        }

    @Post(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = [MediaType.APPLICATION_JSON])
    open fun joinLeague(
        @PathVariable("hash") hash: String,
        @PathVariable("userId") userId: Long,
        @PathVariable("leagueId") leagueId: UUID,
    ): HttpResponse<LeagueResponse> =
        try {
            val response = leagueService.join(userId, leagueId)
            if (response != null) HttpResponse.ok(response) else HttpResponse.ok()
        } catch (inputValidationException: InputValidationException) {
            HttpResponse.badRequest()
        }

    @Delete(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = [MediaType.APPLICATION_JSON])
    open fun deleteLeague(
        @PathVariable("hash") hash: String,
        @PathVariable("userId") userId: Long,
        @PathVariable("leagueId") leagueId: UUID,
    ): HttpResponse<LeagueResponse> =
        try {
            val response = leagueService.delete(userId, leagueId)
            if (response != null) HttpResponse.ok(response) else HttpResponse.ok()
        } catch (inputValidationException: InputValidationException) {
            HttpResponse.badRequest()
        }

    private fun buildBaseUrl(key: String, userId: Long, competitionId: UUID, seasonId: UUID?): String {
        var url = "$applicationUrl/webapp/${hashUtils.getHash(userId.toString())}/users/$userId/$key?competitionId=$competitionId"
        if (seasonId != null) {
            url += "&seasonId=$seasonId"
        }
        return "$url&round="
    }

    private fun webModel(user: UserEntity): MutableMap<String, Any> =
        hashMapOf("i18n" to messageResolver.forLocale(getLocale(user)))

    private fun getLocale(user: UserEntity): Locale? = user.language ?: user.initialLanguage
}

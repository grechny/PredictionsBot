package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.database.entity.MatchEntity
import at.hrechny.predictionsbot.database.entity.PredictionEntity
import at.hrechny.predictionsbot.database.entity.RoundEntity
import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.database.model.MatchStatus
import at.hrechny.predictionsbot.database.repository.MatchRepository
import at.hrechny.predictionsbot.exception.NotFoundException
import at.hrechny.predictionsbot.exception.RequestValidationException
import at.hrechny.predictionsbot.mapper.UserMapper
import at.hrechny.predictionsbot.controller.model.prediction.PredictionRequestDto
import at.hrechny.predictionsbot.controller.model.prediction.ResultResponseDto
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID
import org.slf4j.LoggerFactory

@Singleton
@Transactional
open class PredictionService(
    private val userMapper: UserMapper?,
    private val matchRepository: MatchRepository?,
    private val competitionService: CompetitionService?,
    private val userService: UserService?,
    private val predictionResultsCalculator: PredictionResultsCalculator,
) {
    open fun savePredictions(userId: Long, predictions: List<PredictionRequestDto>?) {
        log.info("Saving predictions for the user {}", userId)
        val user = userService!!.getUser(userId)

        if (predictions.isNullOrEmpty()) {
            log.warn("No predictions found to save")
            return
        }

        val predictionEntities = ArrayList<PredictionEntity>()
        predictions.forEach { prediction ->
            val matchEntity = matchRepository!!.findById(prediction.matchId!!)
                .orElseThrow { NotFoundException("Match with id ${prediction.matchId} not found") }

            if (matchEntity.startTime != null && Instant.now().isAfter(matchEntity.startTime)) {
                log.warn("Not possible to save prediction for the match {} - match already started", matchEntity.id)
                return@forEach
            }

            val predictionEntity = matchEntity.getPrediction(userId).orElseGet { createPredictionEntity(user, matchEntity) }
            predictionEntity.predictionHome = prediction.predictionHome
            predictionEntity.predictionAway = prediction.predictionAway
            predictionEntity.doubleUp = prediction.isDoubleUp()
            predictionEntity.updatedAt = Instant.now()

            predictionEntities.add(predictionEntity)
            matchRepository.save(matchEntity)
        }

        if (predictionEntities.isEmpty()) {
            log.warn("No predictions saved")
        } else {
            validatePredictions(userId, predictionEntities)
            log.info("All predictions for the user {} have been successfully saved", userId)
        }
    }

    open fun getResults(seasonId: UUID): List<ResultResponseDto> {
        val season = competitionService!!.getSeason(seasonId)
        val matches = season.rounds.flatMap { roundEntity -> roundEntity.matches }
        return getResults(matches)
    }

    open fun getResults(matches: List<MatchEntity>): List<ResultResponseDto> =
        predictionResultsCalculator.calculate(toResultInputs(matches))
            .map(::toResult)

    private fun toResultInputs(matches: List<MatchEntity>): List<PredictionResultInput> =
        matches
            .filter { match -> match.status == MatchStatus.FINISHED || match.status == MatchStatus.STARTED }
            .flatMap { match ->
                match.predictions.map { prediction ->
                    PredictionResultInput(
                        user = prediction.user!!,
                        matchStatus = match.status!!,
                        homeTeamScore = match.homeTeamScore!!,
                        awayTeamScore = match.awayTeamScore!!,
                        predictionHome = prediction.predictionHome,
                        predictionAway = prediction.predictionAway,
                        doubleUp = prediction.isDoubleUp(),
                    )
                }
            }

    private fun toResult(predictionUserResult: PredictionUserResult): ResultResponseDto =
        ResultResponseDto().apply {
            user = userMapper!!.entityToModel(predictionUserResult.user)
            predictions = predictionUserResult.predictions
            guessed = predictionUserResult.guessed
            sum = predictionUserResult.sum

            if (predictionUserResult.predictionsLive != null) {
                predictionsLive = predictionUserResult.predictionsLive
                guessedLive = predictionUserResult.guessedLive
                liveSum = predictionUserResult.liveSum
            }
        }

    private fun createPredictionEntity(user: UserEntity, matchEntity: MatchEntity): PredictionEntity {
        val predictionEntity = PredictionEntity().apply {
            this.user = user
            match = matchEntity
        }
        matchEntity.predictions.add(predictionEntity)
        return predictionEntity
    }

    private fun validatePredictions(userId: Long, predictions: Iterable<PredictionEntity>) {
        val predictionEntities = predictions.toList()
        val seasons = predictionEntities.map { prediction -> prediction.match!!.round!!.season!! }.distinct()
        if (seasons.size > 1) {
            throw RequestValidationException("Updating of predictions of different competitions/seasons at once is not supported")
        }

        val season = seasons[0]
        if (!season.isActive()) {
            throw RequestValidationException("Season is not active")
        }

        val rounds = predictionEntities.map { prediction -> prediction.match!!.round!! }.distinct()
        if (!isSameRound(rounds)) {
            throw RequestValidationException("Updating of predictions of different rounds at once is not supported")
        }

        val roundOrderNumber = rounds[0].orderNumber
        val matches = rounds[0].season!!.rounds
            .filter { round -> round.orderNumber == roundOrderNumber }
            .flatMap { round -> round.matches }
        matches.forEach { match ->
            val predictionsOfMatch = match.predictions.filter { prediction -> prediction.user!!.id == userId }
            if (predictionsOfMatch.size > 1) {
                throw RequestValidationException("User can not make more than one prediction for the match")
            }
        }

        val doubleUpOfTheRound = matches
            .flatMap(MatchEntity::predictions)
            .filter { prediction -> prediction.user!!.id == userId }
            .filter(PredictionEntity::isDoubleUp)
        if (doubleUpOfTheRound.size != 1) {
            throw RequestValidationException("User has no/more than one double up for for the round")
        }
    }

    private fun isSameRound(rounds: List<RoundEntity>): Boolean {
        if (rounds.size > 1) {
            val seasonCount = rounds.distinctBy { round -> round.season!!.id }.size
            val roundOrderNumberCount = rounds.distinctBy(RoundEntity::orderNumber).size
            return seasonCount == 1 && roundOrderNumberCount == 1
        }
        return true
    }

    private companion object {
        val log = LoggerFactory.getLogger(PredictionService::class.java)
    }
}

package at.hrechny.predictionsbot.service.predictor

import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.database.model.MatchStatus
import jakarta.inject.Singleton
import org.springframework.stereotype.Component

data class PredictionResultInput(
    val user: UserEntity,
    val matchStatus: MatchStatus,
    val homeTeamScore: Int,
    val awayTeamScore: Int,
    val predictionHome: Int,
    val predictionAway: Int,
    val doubleUp: Boolean,
)

data class PredictionUserResult(
    val user: UserEntity,
    val predictions: Int,
    val guessed: Int,
    val sum: Int,
    val predictionsLive: Int? = null,
    val guessedLive: Int? = null,
    val liveSum: Int? = null,
) {
    val totalSum: Int
        get() = sum + (liveSum ?: 0)
}

@Component
@Singleton
class PredictionResultsCalculator {

    fun calculate(predictions: List<PredictionResultInput>): List<PredictionUserResult> {
        val finishedPredictions = groupByUser(predictions, MatchStatus.FINISHED)
        val livePredictions = groupByUser(predictions, MatchStatus.STARTED)

        return (finishedPredictions.keys + livePredictions.keys)
            .map { user ->
                val finished = finishedPredictions[user]
                val live = livePredictions[user]

                PredictionUserResult(
                    user = user,
                    predictions = finished?.size ?: 0,
                    guessed = calculateGuessed(finished),
                    sum = calculatePoints(finished),
                    predictionsLive = live?.size,
                    guessedLive = live?.let(::calculateGuessed),
                    liveSum = live?.let(::calculatePoints),
                )
            }
            .sortedByDescending { result -> result.totalSum }
    }

    private fun groupByUser(
        predictions: List<PredictionResultInput>,
        matchStatus: MatchStatus,
    ): Map<UserEntity, List<PredictionResultInput>> {
        val byUser = HashMap<UserEntity, MutableList<PredictionResultInput>>()
        predictions
            .filter { prediction -> prediction.matchStatus == matchStatus }
            .forEach { prediction -> byUser.getOrPut(prediction.user) { mutableListOf() }.add(prediction) }

        return byUser
    }

    private fun calculateGuessed(predictions: List<PredictionResultInput>?): Int {
        if (predictions.isNullOrEmpty()) {
            return 0
        }

        return predictions.count { prediction -> isOutcomeHit(prediction) }
    }

    private fun calculatePoints(predictions: List<PredictionResultInput>?): Int {
        if (predictions.isNullOrEmpty()) {
            return 0
        }

        return predictions.sumOf { prediction ->
            val points = when {
                isExactHit(prediction) -> 5
                isDifferenceHit(prediction) -> 3
                isWinnerHit(prediction) -> 2
                else -> 0
            }

            if (prediction.doubleUp) {
                points * 2
            } else {
                points
            }
        }
    }

    private fun isOutcomeHit(prediction: PredictionResultInput): Boolean =
        isDifferenceHit(prediction) || isWinnerHit(prediction)

    private fun isExactHit(prediction: PredictionResultInput): Boolean =
        prediction.homeTeamScore == prediction.predictionHome && prediction.awayTeamScore == prediction.predictionAway

    private fun isDifferenceHit(prediction: PredictionResultInput): Boolean =
        prediction.homeTeamScore - prediction.awayTeamScore == prediction.predictionHome - prediction.predictionAway

    private fun isWinnerHit(prediction: PredictionResultInput): Boolean =
        (prediction.homeTeamScore > prediction.awayTeamScore && prediction.predictionHome > prediction.predictionAway) ||
            (prediction.homeTeamScore < prediction.awayTeamScore && prediction.predictionHome < prediction.predictionAway)
}

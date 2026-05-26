package at.hrechny.predictionsbot.controller.model.prediction

import at.hrechny.predictionsbot.controller.model.user.UserResponseDto
import io.micronaut.core.annotation.Introspected

@Introspected
class ResultResponseDto {
    var user: UserResponseDto? = null
    var predictions: Int? = null
    var predictionsLive: Int? = null
    var guessed: Int? = null
    var guessedLive: Int? = null
    var sum: Int? = null
    var liveSum: Int? = null

    val totalPredictions: Int?
        get() = if (predictionsLive != null) predictions!! + predictionsLive!! else predictions

    val totalGuessed: Int?
        get() = if (guessedLive != null) guessed!! + guessedLive!! else guessed

    val totalSum: Int?
        get() = if (liveSum != null) sum!! + liveSum!! else sum
}

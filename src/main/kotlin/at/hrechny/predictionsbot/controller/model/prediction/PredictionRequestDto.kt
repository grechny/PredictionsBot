package at.hrechny.predictionsbot.controller.model.prediction

import io.micronaut.core.annotation.Introspected
import java.util.UUID

@Introspected
class PredictionRequestDto {
    var matchId: UUID? = null
    var predictionHome: Int = 0
    var predictionAway: Int = 0
    var doubleUp: Boolean = false

    fun isDoubleUp(): Boolean = doubleUp
}

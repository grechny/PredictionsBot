package at.hrechny.predictionsbot.model

import io.micronaut.core.annotation.Introspected
import java.util.UUID

@Introspected
class Prediction {
    var matchId: UUID? = null
    var predictionHome: Int = 0
    var predictionAway: Int = 0
    var doubleUp: Boolean = false

    fun isDoubleUp(): Boolean = doubleUp
}

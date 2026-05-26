package at.hrechny.predictionsbot.controller

import at.hrechny.predictionsbot.controller.model.prediction.PredictionRequestDto
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport
import at.hrechny.predictionsbot.service.predictor.PredictionService
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.Post
import jakarta.validation.Valid

@Controller
@EnableErrorReport
open class PredictionController(
    private val predictionService: PredictionService,
) {
    @Post(value = "/\${secrets.adminKey:}/users/{userId}/predictions", consumes = [MediaType.APPLICATION_JSON])
    open fun addPredictions(
        @PathVariable("userId") userId: Long,
        @Valid @Body predictions: List<PredictionRequestDto>,
    ): HttpResponse<Void> {
        predictionService.savePredictions(userId, predictions)
        return HttpResponse.ok()
    }
}

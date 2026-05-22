package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
import at.hrechny.predictionsbot.model.Prediction;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import jakarta.validation.Valid;
import java.util.List;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;

@Controller
@EnableErrorReport
public class PredictionController {

  private final PredictionService predictionService;

  public PredictionController(PredictionService predictionService) {
    this.predictionService = predictionService;
  }

  @Post(value = "/${secrets.adminKey}/users/{userId}/predictions", consumes = MediaType.APPLICATION_JSON)
  public HttpResponse<Void> addPredictions(@PathVariable("userId") Long userId, @Valid @Body List<Prediction> predictions) {
    predictionService.savePredictions(userId, predictions);
    return HttpResponse.ok();
  }

}

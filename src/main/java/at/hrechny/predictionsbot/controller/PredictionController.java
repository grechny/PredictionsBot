package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.model.Prediction;
import at.hrechny.predictionsbot.service.PredictionService;
import java.util.List;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PredictionController {

  private final PredictionService predictionService;

  @PostMapping(value = "/${secrets.adminKey}/users/{userId}/predictions", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> addPredictions(@PathVariable("userId") Long userId, @Valid @RequestBody List<Prediction> predictions) {
    predictionService.savePredictions(userId, predictions);
    return ResponseEntity.ok().build();
  }

}

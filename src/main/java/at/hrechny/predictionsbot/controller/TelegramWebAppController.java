package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.model.Prediction;
import at.hrechny.predictionsbot.service.PredictionService;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/${secrets.telegramKey}/")
@RequiredArgsConstructor
public class TelegramWebAppController {

  private final LocaleResolver localeResolver;
  private final PredictionService predictionService;

  private final HttpServletRequest request;

  @GetMapping(value = "/users/{userId}/predictions", produces = MediaType.TEXT_HTML_VALUE)
  public ModelAndView getPredictions(
      @PathVariable("userId") Long userId,
      @RequestParam("leagueId") UUID leagueId,
      @RequestParam(value = "round", required = false) Integer round) {

    var user = predictionService.getUser(userId);
    localeResolver.setLocale(request, null, user.getLanguage());

    var fixtures = predictionService.getFixtures(leagueId, round);
    if (fixtures.isEmpty()) {
      return new ModelAndView("no-upcoming-matches");
    }

    var modelAndView = new ModelAndView("predictions");
    modelAndView.addObject("user", user);
    modelAndView.addObject("fixtures", fixtures);
    return modelAndView;
  }

  @PostMapping(value = "/users/{userId}/predictions", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> addPredictions(@PathVariable("userId") Long userId, @Valid @RequestBody List<Prediction> predictions) {
    predictionService.savePredictions(userId, predictions);
    return ResponseEntity.ok().build();
  }

}

package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.model.Result;
import at.hrechny.predictionsbot.service.CompetitionService;
import at.hrechny.predictionsbot.service.PredictionService;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequestMapping("/${secrets.telegramKey}/")
@RequiredArgsConstructor
public class TelegramWebAppController {

  private final LocaleResolver localeResolver;
  private final CompetitionService competitionService;
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

  @GetMapping(value = "/users/{userId}/results", produces = MediaType.TEXT_HTML_VALUE)
  public ModelAndView getResults(
      @PathVariable("userId") Long userId,
      @RequestParam("leagueId") UUID leagueId,
      @RequestParam(value = "round", required = false) Integer round) {

    var user = predictionService.getUser(userId);
    localeResolver.setLocale(request, null, user.getLanguage());

    var results = predictionService.getResults(leagueId);
    var competition = competitionService.getCompetition(leagueId);

    var modelAndView = new ModelAndView("results");
    modelAndView.addObject("competitionName", competition.getName());
    modelAndView.addObject("user", user);
    modelAndView.addObject("results", results);
    return modelAndView;
  }

}

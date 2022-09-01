package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.model.Result;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.util.HashUtils;
import at.hrechny.predictionsbot.util.ObjectUtils;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.ModelAndView;

@Controller
@RequiredArgsConstructor
public class TelegramWebAppController {

  @Value("${application.url}")
  private String applicationUrl;

  private final LocaleResolver localeResolver;
  private final CompetitionService competitionService;
  private final PredictionService predictionService;
  private final UserService userService;
  private final HashUtils hashUtils;
  private final HttpServletRequest request;

  @GetMapping(value = "/{hash}/users/{userId}/predictions", produces = MediaType.TEXT_HTML_VALUE)
  public ModelAndView getPredictions(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @RequestParam("leagueId") UUID leagueId,
      @RequestParam(value = "round", required = false) Integer round) {

    if (!hashUtils.getHash(userId.toString()).equals(hash)) {
      throw new NotFoundException("User not found");
    }

    var user = userService.getUser(userId);
    if (user.getLanguage() != null) {
      localeResolver.setLocale(request, null, user.getLanguage());
    }

    if (round == null || round.equals(0)) {
      round = competitionService.getUpcomingRound(leagueId);
    }

    var fixtures = competitionService.getFixtures(leagueId, round);
    if (fixtures.isEmpty()) {
      var modelAndView = new ModelAndView("no-upcoming-matches");
      modelAndView.addObject("competitionName", competitionService.getCompetition(leagueId).getName());
      return modelAndView;
    }

    var rounds = fixtures.get(0).getSeason().getApiFootballRounds().stream()
        .filter(ObjectUtils.distinctByKey(RoundEntity::getOrderNumber))
        .sorted(Comparator.comparingInt(RoundEntity::getOrderNumber))
        .toList();

    var modelAndView = new ModelAndView("predictions");
    modelAndView.addObject("user", user);
    modelAndView.addObject("fixtures", fixtures);
    modelAndView.addObject("rounds", rounds);
    modelAndView.addObject("baseUrl", buildBaseUrl("predictions", userId, leagueId));
    return modelAndView;
  }

  @GetMapping(value = "/{hash}/users/{userId}/results", produces = MediaType.TEXT_HTML_VALUE)
  public ModelAndView getResults(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @RequestParam("leagueId") UUID leagueId,
      @RequestParam(value = "round", required = false) Integer round) {

    if (!hashUtils.getHash(userId.toString()).equals(hash)) {
      throw new NotFoundException("User not found");
    }

    var user = userService.getUser(userId);
    if (user.getLanguage() != null) {
      localeResolver.setLocale(request, null, user.getLanguage());
    }

    List<Result> results;
    if (round != null && !round.equals(0)) {
      results = predictionService.getResults(leagueId, round);
    } else {
      results = predictionService.getResults(leagueId);
    }

    var season = competitionService.getCurrentSeason(leagueId);
    var rounds = season.getMatches().stream()
        .filter(match -> Arrays.asList(MatchStatus.STARTED, MatchStatus.FINISHED).contains(match.getStatus()))
        .filter(match -> CollectionUtils.isNotEmpty(match.getPredictions()))
        .map(MatchEntity::getRound)
        .collect(Collectors.toSet());

    if (CollectionUtils.isEmpty(rounds)) {
      var modelAndView = new ModelAndView("no-results");
      modelAndView.addObject("competitionName", competitionService.getCompetition(leagueId).getName());
      return modelAndView;
    }

    var roundEntities = season.getApiFootballRounds().stream()
        .filter(ObjectUtils.distinctByKey(RoundEntity::getOrderNumber))
        .filter(roundEntity -> rounds.contains(roundEntity.getOrderNumber()))
        .sorted(Comparator.comparingInt(RoundEntity::getOrderNumber))
        .toList();

    var modelAndView = new ModelAndView("results");
    modelAndView.addObject("user", user);
    modelAndView.addObject("results", results);
    modelAndView.addObject("rounds", roundEntities);
    modelAndView.addObject("activeRound", round);
    modelAndView.addObject("competitionName", competitionService.getCompetition(leagueId).getName());
    modelAndView.addObject("baseUrl", buildBaseUrl("results", userId, leagueId));
    return modelAndView;
  }

  private String buildBaseUrl(String key, Long userId, UUID competitionId) {
    return applicationUrl + "/" + hashUtils.getHash(userId.toString()) + "/users/" + userId + "/" + key + "?leagueId=" + competitionId + "&round=";
  }
}

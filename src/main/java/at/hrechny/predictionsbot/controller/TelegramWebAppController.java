package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
import at.hrechny.predictionsbot.model.Result;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.util.HashUtils;
import at.hrechny.predictionsbot.util.ObjectUtils;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
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
@EnableErrorReport
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
      @RequestParam(value = "round", required = false) Integer roundNumber) {

    if (!hashUtils.getHash(userId.toString()).equals(hash)) {
      throw new NotFoundException("User not found");
    }

    var user = userService.getUser(userId);
    if (user.getLanguage() != null) {
      localeResolver.setLocale(request, null, user.getLanguage());
    }

    RoundEntity round;
    if (roundNumber == null || roundNumber.equals(0)) {
      round = competitionService.getUpcomingRound(leagueId);
    } else {
      round = competitionService.getRound(leagueId, roundNumber);
    }

    if (round == null || CollectionUtils.isEmpty(round.getMatches())) {
      var modelAndView = new ModelAndView("no-upcoming-matches");
      modelAndView.addObject("competitionName", competitionService.getCompetition(leagueId).getName());
      return modelAndView;
    }

    var rounds = round.getSeason().getRounds().stream()
        .filter(roundEntity -> roundEntity.getOrderNumber() != 0)
        .filter(ObjectUtils.distinctByKey(RoundEntity::getOrderNumber))
        .sorted(Comparator.comparingInt(RoundEntity::getOrderNumber))
        .toList();
    var fixtures = round.getMatches().stream()
        .sorted(Comparator.comparing(MatchEntity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();

    var modelAndView = new ModelAndView("predictions");
    modelAndView.addObject("user", user);
    modelAndView.addObject("fixtures", fixtures);
    modelAndView.addObject("rounds", rounds);
    modelAndView.addObject("baseUrl", buildBaseUrl("predictions", userId, leagueId, null));
    return modelAndView;
  }

  @GetMapping(value = "/{hash}/users/{userId}/results", produces = MediaType.TEXT_HTML_VALUE)
  public ModelAndView getResults(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @RequestParam("leagueId") UUID leagueId,
      @RequestParam(value = "seasonId", required = false) UUID seasonId,
      @RequestParam(value = "round", required = false) Integer roundNumber) {

    if (!hashUtils.getHash(userId.toString()).equals(hash)) {
      throw new NotFoundException("User not found");
    }

    var user = userService.getUser(userId);
    if (user.getLanguage() != null) {
      localeResolver.setLocale(request, null, user.getLanguage());
    }

    var season = seasonId != null ? competitionService.getSeason(seasonId) : competitionService.getCurrentSeason(leagueId);
    competitionService.refreshActiveFixtures(season.getId());

    List<Result> results;
    List<MatchEntity> matches;
    if (roundNumber != null && !roundNumber.equals(0)) {
      var round = season.getRounds().stream()
          .filter(roundEntity -> roundNumber.equals(roundEntity.getOrderNumber()))
          .findFirst()
          .orElseThrow(() -> new NotFoundException("Round with order number " + roundNumber + "could not be found for the season " + season.getId()));
      results = predictionService.getResults(round.getMatches());
      matches = round.getMatches().stream()
          .filter(match -> Arrays.asList(MatchStatus.STARTED, MatchStatus.FINISHED).contains(match.getStatus()))
          .filter(match -> CollectionUtils.isNotEmpty(match.getPredictions()))
          .sorted(Comparator.comparing(MatchEntity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
          .toList();
    } else {
      results = predictionService.getResults(season.getId());
      matches = season.getRounds().stream()
          .flatMap(roundEntity -> roundEntity.getMatches().stream())
          .filter(match -> Arrays.asList(MatchStatus.STARTED, MatchStatus.FINISHED).contains(match.getStatus()))
          .filter(match -> CollectionUtils.isNotEmpty(match.getPredictions()))
          .sorted(Comparator.comparing(MatchEntity::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())))
          .limit(10)
          .toList();
    }

    var matchResults = matches.stream()
        .collect(Collectors.toMap(match -> match.getId().toString(), match -> predictionService.getResults(List.of(match))));
    var rounds = season.getRounds().stream()
        .flatMap(roundEntity -> roundEntity.getMatches().stream())
        .filter(match -> Arrays.asList(MatchStatus.STARTED, MatchStatus.FINISHED).contains(match.getStatus()))
        .filter(match -> CollectionUtils.isNotEmpty(match.getPredictions()))
        .map(MatchEntity::getRound)
        .sorted(Comparator.comparingInt(RoundEntity::getOrderNumber))
        .distinct()
        .toList();

    if (CollectionUtils.isEmpty(rounds)) {
      var modelAndView = new ModelAndView("no-results");
      modelAndView.addObject("competitionName", competitionService.getCompetition(leagueId).getName());
      return modelAndView;
    }

    var modelAndView = new ModelAndView("results");
    modelAndView.addObject("user", user);
    modelAndView.addObject("results", results);
    modelAndView.addObject("rounds", rounds);
    modelAndView.addObject("activeRound", roundNumber);
    modelAndView.addObject("matches", matches);
    modelAndView.addObject("matchResults", matchResults);
    modelAndView.addObject("competitionName", competitionService.getCompetition(leagueId).getName());
    modelAndView.addObject("baseUrl", buildBaseUrl("results", userId, leagueId, seasonId));

    return modelAndView;
  }

  private String buildBaseUrl(String key, Long userId, UUID competitionId, UUID seasonId) {
    var url = applicationUrl + "/" + hashUtils.getHash(userId.toString()) + "/users/" + userId + "/" + key + "?leagueId=" + competitionId;
    if (seasonId != null) {
      url += "&seasonId=" + seasonId;
    }
    return url + "&round=";
  }
}

package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.exception.InputValidationException;
import at.hrechny.predictionsbot.exception.LimitExceededException;
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
import at.hrechny.predictionsbot.model.LeagueRequest;
import at.hrechny.predictionsbot.model.LeagueResponse;
import at.hrechny.predictionsbot.model.Result;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.LeagueService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final LeagueService leagueService;
  private final UserService userService;
  private final HashUtils hashUtils;
  private final HttpServletRequest request;

  @GetMapping(value = "/webapp/{hash}/users/{userId}/predictions", produces = MediaType.TEXT_HTML_VALUE)
  public ModelAndView getPredictions(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @RequestParam("competitionId") UUID competitionId,
      @RequestParam(value = "round", required = false) Integer roundNumber) {

    var user = userService.getUser(userId);
    if (user.getLanguage() != null) {
      localeResolver.setLocale(request, null, user.getLanguage());
    }

    RoundEntity round;
    if (roundNumber == null || roundNumber.equals(0)) {
      round = competitionService.getUpcomingRound(competitionId);
    } else {
      round = competitionService.getRound(competitionId, roundNumber);
    }

    if (round == null || CollectionUtils.isEmpty(round.getMatches())) {
      var modelAndView = new ModelAndView("no-upcoming-matches");
      modelAndView.addObject("competitionName", competitionService.getCompetition(competitionId).getName());
      return modelAndView;
    }

    var rounds = round.getSeason().getRounds().stream()
        .filter(roundEntity -> !roundEntity.getMatches().isEmpty())
        .filter(roundEntity -> roundEntity.getOrderNumber() != 0)
        .filter(ObjectUtils.distinctByKey(RoundEntity::getOrderNumber))
        .sorted(Comparator.comparingInt(RoundEntity::getOrderNumber))
        .toList();
    var fixtures = round.getSeason().getRounds().stream()
        .filter(roundEntity -> roundEntity.getOrderNumber() == round.getOrderNumber())
        .flatMap(roundEntity -> roundEntity.getMatches().stream())
        .sorted(Comparator.comparing(MatchEntity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();

    var modelAndView = new ModelAndView("predictions");
    modelAndView.addObject("user", user);
    modelAndView.addObject("fixtures", fixtures);
    modelAndView.addObject("rounds", rounds);
    modelAndView.addObject("baseUrl", buildBaseUrl("predictions", userId, competitionId, null));
    return modelAndView;
  }

  @GetMapping(value = "/webapp/{hash}/users/{userId}/results", produces = MediaType.TEXT_HTML_VALUE)
  public ModelAndView getResults(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @RequestParam("competitionId") UUID competitionId,
      @RequestParam(value = "seasonId", required = false) UUID seasonId,
      @RequestParam(value = "round", required = false) Integer roundNumber) {

    var user = userService.getUser(userId);
    if (user.getLanguage() != null) {
      localeResolver.setLocale(request, null, user.getLanguage());
    }

    var season = seasonId != null ? competitionService.getSeason(seasonId) : competitionService.getCurrentSeason(competitionId);
    competitionService.refreshActiveFixtures(season.getId());

    List<Result> results;
    List<MatchEntity> matches;
    if (roundNumber != null && !roundNumber.equals(0)) {
      matches = season.getRounds().stream()
          .filter(roundEntity -> roundNumber.equals(roundEntity.getOrderNumber()))
          .flatMap(roundEntity -> roundEntity.getMatches().stream())
          .filter(match -> Arrays.asList(MatchStatus.STARTED, MatchStatus.FINISHED).contains(match.getStatus()))
          .filter(match -> CollectionUtils.isNotEmpty(match.getPredictions()))
          .sorted(Comparator.comparing(MatchEntity::getStartTime, Comparator.nullsLast(Comparator.naturalOrder())))
          .toList();
      results = predictionService.getResults(matches);
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
        .filter(ObjectUtils.distinctByKey(RoundEntity::getOrderNumber))
        .sorted(Comparator.comparingInt(RoundEntity::getOrderNumber))
        .distinct()
        .toList();

    if (CollectionUtils.isEmpty(rounds)) {
      var modelAndView = new ModelAndView("no-results");
      modelAndView.addObject("competitionName", competitionService.getCompetition(competitionId).getName());
      return modelAndView;
    }

    var modelAndView = new ModelAndView("results");
    modelAndView.addObject("user", user);
    modelAndView.addObject("results", results);
    modelAndView.addObject("rounds", rounds);
    modelAndView.addObject("activeRound", roundNumber);
    modelAndView.addObject("matches", matches);
    modelAndView.addObject("matchResults", matchResults);
    modelAndView.addObject("competitionName", competitionService.getCompetition(competitionId).getName());
    modelAndView.addObject("baseUrl", buildBaseUrl("results", userId, competitionId, seasonId));

    return modelAndView;
  }

  @GetMapping(value = "/webapp/{hash}/users/{userId}/leagues", produces = MediaType.TEXT_HTML_VALUE)
  public ModelAndView getLeagues(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId) {

    var user = userService.getUser(userId);
    if (user.getLanguage() != null) {
      localeResolver.setLocale(request, null, user.getLanguage());
    }

    var modelAndView = new ModelAndView("leagues");
    modelAndView.addObject("user", user);
    return modelAndView;
  }

  @PostMapping(value = "/webapp/{hash}/users/{userId}/leagues", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<LeagueResponse> createLeague(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @RequestBody LeagueRequest leagueRequest) {

    try {
      return ResponseEntity.ok(leagueService.create(userId, leagueRequest));
    } catch (InputValidationException inputValidationException) {
      return ResponseEntity.badRequest().body(null);
    } catch (LimitExceededException limitExceededException) {
      return new ResponseEntity<>(HttpStatus.CONFLICT);
    }
  }

  @PutMapping(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<LeagueResponse> updateLeague(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @PathVariable("leagueId") UUID leagueId,
      @RequestBody LeagueRequest leagueRequest) {

    try {
      return ResponseEntity.ok(leagueService.update(userId, leagueId, leagueRequest));
    } catch (InputValidationException inputValidationException) {
      return ResponseEntity.badRequest().body(null);
    }
  }

  @PostMapping(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<LeagueResponse> joinLeague(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @PathVariable("leagueId") UUID leagueId) {

    try {
      return ResponseEntity.ok(leagueService.join(userId, leagueId));
    } catch (InputValidationException inputValidationException) {
      return ResponseEntity.badRequest().body(null);
    }
  }

  @DeleteMapping(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<LeagueResponse> deleteLeague(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @PathVariable("leagueId") UUID leagueId) {

    try {
      return ResponseEntity.ok(leagueService.delete(userId, leagueId));
    } catch (InputValidationException inputValidationException) {
      return ResponseEntity.badRequest().body(null);
    }
  }

  private String buildBaseUrl(String key, Long userId, UUID competitionId, UUID seasonId) {
    var url = applicationUrl + "/webapp/" + hashUtils.getHash(userId.toString()) + "/users/" + userId + "/" + key + "?competitionId=" + competitionId;
    if (seasonId != null) {
      url += "&seasonId=" + seasonId;
    }
    return url + "&round=";
  }
}

package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.config.MessageResolver;
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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.collections4.CollectionUtils;
import io.micronaut.context.annotation.Value;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.views.ModelAndView;

@Controller
@EnableErrorReport
public class TelegramWebAppController {

  @Value("${application.url}")
  private String applicationUrl;

  private final CompetitionService competitionService;
  private final PredictionService predictionService;
  private final LeagueService leagueService;
  private final UserService userService;
  private final HashUtils hashUtils;
  private final MessageResolver messageResolver;

  public TelegramWebAppController(
      CompetitionService competitionService,
      PredictionService predictionService,
      LeagueService leagueService,
      UserService userService,
      HashUtils hashUtils,
      MessageResolver messageResolver) {

    this.competitionService = competitionService;
    this.predictionService = predictionService;
    this.leagueService = leagueService;
    this.userService = userService;
    this.hashUtils = hashUtils;
    this.messageResolver = messageResolver;
  }

  @Get(value = "/webapp/{hash}/users/{userId}/predictions", produces = MediaType.TEXT_HTML)
  public ModelAndView<Map<String, Object>> getPredictions(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @QueryValue("competitionId") UUID competitionId,
      @Nullable @QueryValue("round") Integer roundNumber) {

    var user = userService.getUser(userId);

    RoundEntity round;
    if (roundNumber == null || roundNumber.equals(0)) {
      round = competitionService.getUpcomingRound(competitionId);
    } else {
      round = competitionService.getRound(competitionId, roundNumber);
    }

    if (round == null || CollectionUtils.isEmpty(round.getMatches())) {
      var model = webModel(user);
      model.put("competitionName", competitionService.getCompetition(competitionId).getName());
      return new ModelAndView<>("no-upcoming-matches", model);
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

    var model = webModel(user);
    model.put("user", user);
    model.put("fixtures", fixtures);
    model.put("rounds", rounds);
    model.put("baseUrl", buildBaseUrl("predictions", userId, competitionId, null));
    return new ModelAndView<>("predictions", model);
  }

  @Get(value = "/webapp/{hash}/users/{userId}/results", produces = MediaType.TEXT_HTML)
  public ModelAndView<Map<String, Object>> getResults(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @QueryValue("competitionId") UUID competitionId,
      @Nullable @QueryValue("seasonId") UUID seasonId,
      @Nullable @QueryValue("round") Integer roundNumber) {

    var user = userService.getUser(userId);

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
      var model = webModel(user);
      model.put("competitionName", competitionService.getCompetition(competitionId).getName());
      return new ModelAndView<>("no-results", model);
    }

    var model = webModel(user);
    model.put("user", user);
    model.put("results", results);
    model.put("rounds", rounds);
    model.put("activeRound", roundNumber);
    model.put("matches", matches);
    model.put("matchResults", matchResults);
    model.put("competitionName", competitionService.getCompetition(competitionId).getName());
    model.put("baseUrl", buildBaseUrl("results", userId, competitionId, seasonId));

    return new ModelAndView<>("results", model);
  }

  @Get(value = "/webapp/{hash}/users/{userId}/leagues", produces = MediaType.TEXT_HTML)
  public ModelAndView<Map<String, Object>> getLeagues(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId) {

    var user = userService.getUser(userId);

    var model = webModel(user);
    model.put("user", user);
    return new ModelAndView<>("leagues", model);
  }

  @Post(value = "/webapp/{hash}/users/{userId}/leagues", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<LeagueResponse> createLeague(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @Body LeagueRequest leagueRequest) {

    try {
      return HttpResponse.ok(leagueService.create(userId, leagueRequest));
    } catch (InputValidationException inputValidationException) {
      return HttpResponse.badRequest();
    } catch (LimitExceededException limitExceededException) {
      return HttpResponse.status(HttpStatus.CONFLICT);
    }
  }

  @Put(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<LeagueResponse> updateLeague(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @PathVariable("leagueId") UUID leagueId,
      @Body LeagueRequest leagueRequest) {

    try {
      return HttpResponse.ok(leagueService.update(userId, leagueId, leagueRequest));
    } catch (InputValidationException inputValidationException) {
      return HttpResponse.badRequest();
    }
  }

  @Post(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<LeagueResponse> joinLeague(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @PathVariable("leagueId") UUID leagueId) {

    try {
      return HttpResponse.ok(leagueService.join(userId, leagueId));
    } catch (InputValidationException inputValidationException) {
      return HttpResponse.badRequest();
    }
  }

  @Delete(value = "/webapp/{hash}/users/{userId}/leagues/{leagueId}", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<LeagueResponse> deleteLeague(
      @PathVariable("hash") String hash,
      @PathVariable("userId") Long userId,
      @PathVariable("leagueId") UUID leagueId) {

    try {
      return HttpResponse.ok(leagueService.delete(userId, leagueId));
    } catch (InputValidationException inputValidationException) {
      return HttpResponse.badRequest();
    }
  }

  private String buildBaseUrl(String key, Long userId, UUID competitionId, UUID seasonId) {
    var url = applicationUrl + "/webapp/" + hashUtils.getHash(userId.toString()) + "/users/" + userId + "/" + key + "?competitionId=" + competitionId;
    if (seasonId != null) {
      url += "&seasonId=" + seasonId;
    }
    return url + "&round=";
  }

  private Map<String, Object> webModel(UserEntity user) {
    var model = new HashMap<String, Object>();
    model.put("i18n", messageResolver.forLocale(getLocale(user)));
    return model;
  }

  private Locale getLocale(UserEntity user) {
    return user.getLanguage() != null ? user.getLanguage() : user.getInitialLanguage();
  }
}

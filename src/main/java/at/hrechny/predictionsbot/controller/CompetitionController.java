package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.exception.RequestValidationException;
import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.model.Season;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Put;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;

@Controller
public class CompetitionController {

  private final CompetitionService competitionService;
  private final TelegramService telegramService;

  public CompetitionController(CompetitionService competitionService, TelegramService telegramService) {
    this.competitionService = competitionService;
    this.telegramService = telegramService;
  }

  @Post(value = "/${secrets.adminKey}/competitions", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
  public HttpResponse<Map<String, UUID>> addCompetition(@Valid @Body Competition competition) {
    if (competition.getId() != null) {
      throw new RequestValidationException("Setting of competition id is not allowed");
    }

    var id = competitionService.addCompetition(competition);
    telegramService.sendCompetition(id);
    return HttpResponse.ok(Map.of("id", id));
  }

  @Get(value = "/${secrets.adminKey}/competitions", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<List<Competition>> getCompetitions() {
    return HttpResponse.ok(competitionService.getCompetitions());
  }

  @Post(value = "/${secrets.adminKey}/competitions/{competitionId}/seasons", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
  public HttpResponse<Map<String, UUID>> addSeason(@PathVariable("competitionId") UUID competitionId, @Valid @Body Season season) {
    if (season.getId() != null) {
      throw new RequestValidationException("Setting of season id is not allowed");
    }

    var id = competitionService.addSeason(competitionId, season);
    telegramService.pushUpdate(competitionId);
    return HttpResponse.ok(Map.of("id", id));
  }

  @Get(value = "/${secrets.adminKey}/competitions/{competitionId}/seasons", produces = MediaType.APPLICATION_JSON)
  public HttpResponse<List<Season>> getSeasons(@PathVariable("competitionId") UUID competitionId) {
    return HttpResponse.ok(competitionService.getSeasons(competitionId));
  }

  @Put(value = "/${secrets.adminKey}/competitions/{competitionId}/seasons/{seasonId}", consumes = MediaType.APPLICATION_JSON)
  public HttpResponse<Void> updateSeason(
      @PathVariable("competitionId") UUID competitionId,
      @PathVariable("seasonId") UUID seasonId,
      @Valid @Body Season season) {

    if (season.getId() != null && !season.getId().equals(seasonId)) {
      throw new RequestValidationException("Season ID cannot be updated");
    }

    season.setId(seasonId);
    competitionService.updateSeason(competitionId, season);
    telegramService.pushUpdate(competitionId);
    return HttpResponse.ok();
  }

  @Post(value = "/${secrets.adminKey}/fixtures")
  public HttpResponse<Void> refreshFixtures() {
    competitionService.getActiveSeasons().forEach(competitionService::refreshFixtures);
    return HttpResponse.ok();
  }

  @Post(value = "/${secrets.adminKey}/fixtures/{competitionId}")
  public HttpResponse<Void> refreshFixtures(@PathVariable("competitionId") UUID competitionId) {
    var season = competitionService.getCurrentSeason(competitionId);
    competitionService.refreshFixtures(season);
    return HttpResponse.ok();
  }
}

package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.model.Season;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CompetitionController {

  private final CompetitionService competitionService;

  @PostMapping(value = "/${secrets.adminKey}/competitions", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, UUID>> addCompetition(@Valid @RequestBody Competition competition) {
    if (competition.getId() != null) {
      throw new IllegalArgumentException("Setting of competition id is not allowed");
    }

    var id = competitionService.addCompetition(competition);
    return ResponseEntity.ok(Map.of("id", id));
  }

  @GetMapping(value = "/${secrets.adminKey}/competitions", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Competition>> getCompetitions() {
    return ResponseEntity.ok(competitionService.getCompetitions());
  }

  @PostMapping(value = "/${secrets.adminKey}/competitions/{competitionId}/seasons", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Map<String, UUID>> addSeason(@PathVariable("competitionId") UUID competitionId, @Valid @RequestBody Season season) {
    if (season.getId() != null) {
      throw new IllegalArgumentException("Setting of season id is not allowed");
    }

    var id = competitionService.addSeason(competitionId, season);
    return ResponseEntity.ok(Map.of("id", id));
  }

  @GetMapping(value = "/${secrets.adminKey}/competitions/{competitionId}/seasons", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Season>> getSeasons(@PathVariable("competitionId") UUID competitionId) {
    return ResponseEntity.ok(competitionService.getSeasons(competitionId));
  }

  @PutMapping(value = "/${secrets.adminKey}/competitions/{competitionId}/seasons/{seasonId}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> updateSeason(
      @PathVariable("competitionId") UUID competitionId,
      @PathVariable("seasonId") UUID seasonId,
      @Valid @RequestBody Season season) {

    if (season.getId() != null && !season.getId().equals(seasonId)) {
      throw new IllegalArgumentException("Unable to update SEASON id");
    }

    season.setId(seasonId);
    competitionService.updateSeason(competitionId, season);
    return ResponseEntity.ok().build();
  }

  @PostMapping(value = "/${secrets.adminKey}/fixtures")
  public ResponseEntity<Void> refreshFixtures() {
    competitionService.refreshFixtures();
    return ResponseEntity.ok().build();
  }

}

package at.hrechny.predictionsbot.service.predictor;

import at.hrechny.predictionsbot.connector.apifootball.ApiFootballConnector;
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException;
import at.hrechny.predictionsbot.connector.apifootball.model.Fixture;
import at.hrechny.predictionsbot.connector.apifootball.model.Status;
import at.hrechny.predictionsbot.connector.apifootball.model.Team;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.TeamEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.model.RoundType;
import at.hrechny.predictionsbot.database.repository.CompetitionRepository;
import at.hrechny.predictionsbot.database.repository.MatchRepository;
import at.hrechny.predictionsbot.database.repository.SeasonRepository;
import at.hrechny.predictionsbot.database.repository.TeamRepository;
import at.hrechny.predictionsbot.exception.FixturesSynchronizationException;
import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.exception.RequestValidationException;
import at.hrechny.predictionsbot.mapper.CompetitionMapper;
import at.hrechny.predictionsbot.mapper.SeasonMapper;
import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.model.Season;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CompetitionService {

  private final SeasonMapper seasonMapper;
  private final SeasonRepository seasonRepository;
  private final CompetitionMapper competitionMapper;
  private final CompetitionRepository competitionRepository;
  private final TeamRepository teamRepository;
  private final MatchRepository matchRepository;
  private final ApiFootballConnector apiFootballConnector;

  public UUID addCompetition(Competition competition) {
    log.info("Adding the new competition: {}", competition);
    var entity = competitionRepository.save(competitionMapper.modelToEntity(competition));
    log.info("The competition has been successfully stored: {}", entity.getId());
    return entity.getId();
  }

  public Competition getCompetition(UUID competitionId) {
    CompetitionEntity entity = competitionRepository.findById(competitionId)
        .orElseThrow(() -> new NotFoundException("Competition with the ID " + competitionId + " not found"));
    return competitionMapper.entityToModel(entity);
  }

  public List<Competition> getCompetitions() {
    var entityList = competitionRepository.findAll();
    var competitionList = new ArrayList<Competition>();
    entityList.forEach(entity -> competitionList.add(competitionMapper.entityToModel(entity)));
    return competitionList;
  }

  public UUID addSeason(UUID competitionId, Season season) {
    log.info("Adding the new season for the competition {}: {}", competitionId, season);
    CompetitionEntity competitionEntity = competitionRepository.findById(competitionId)
        .orElseThrow(() -> new NotFoundException("Competition with the ID " + competitionId + " not found"));

    validateActiveSeasons(competitionId, season);

    var seasonEntity = seasonRepository.save(seasonMapper.modelToEntity(competitionEntity, season));
    log.info("The season has been successfully stored");

    return seasonEntity.getId();
  }

  public void updateSeason(UUID competitionId, Season season) {
    log.info("Updating the season {} for the competition {}", season.getId(), competitionId);
    var seasonEntity = seasonRepository.findById(season.getId()).orElse(null);
    if (seasonEntity == null || !seasonEntity.getCompetition().getId().equals(competitionId)) {
      throw new NotFoundException("Season with the ID " + season.getId() + " not found");
    }

    validateActiveSeasons(competitionId, season);

    seasonMapper.updateEntity(seasonEntity, season);
    seasonRepository.save(seasonEntity);
    log.info("The season {} has been successfully updated", seasonEntity.getId());
  }

  public SeasonEntity getSeason(UUID seasonId) {
    return seasonRepository.findById(seasonId).orElseThrow(() -> new NotFoundException("Season " + seasonId + " not found"));
  }

  public List<Season> getSeasons(UUID competitionId) {
    var entityList = seasonRepository.findAllByCompetitionId(competitionId);
    var seasonList = new ArrayList<Season>();
    entityList.forEach(entity -> seasonList.add(seasonMapper.entityToModel(entity)));
    return seasonList;
  }

  public SeasonEntity getCurrentSeason(UUID competitionId) {
    return seasonRepository.findFirstByCompetitionIdAndActiveIsTrue(competitionId)
        .orElseThrow(() -> new NotFoundException("No active season found for the competition " + competitionId));
  }

  public RoundEntity getUpcomingRound(UUID competitionId) {
    return matchRepository.findUpcoming(getCurrentSeason(competitionId)).map(MatchEntity::getRound).orElse(null);
  }

  public List<MatchEntity> getFixtures(Instant from, Instant to) {
    return matchRepository.findAllByStartTimeAfterAndStartTimeBeforeOrderByStartTimeAsc(from, to);
  }

  public RoundEntity getRound(UUID competitionId, Integer orderNumber) {
    var season = getCurrentSeason(competitionId);
    return season.getRounds().stream().filter(roundEntity -> orderNumber.equals(roundEntity.getOrderNumber())).findFirst().orElse(null);
  }

  public List<SeasonEntity> getActiveSeasons() {
    log.info("Starting to refresh fixtures data for the all active competitions");
    return seasonRepository.findAllByActiveIsTrue();
  }

  public void refreshActiveFixtures(UUID seasonId) {
    var seasonEntity = getSeason(seasonId);
    var activeMatches = matchRepository.findAllActive(seasonEntity);
    if (activeMatches.isEmpty()) {
      return;
    }

    var fixturesIds = new ArrayList<Long>();
    activeMatches.forEach(match -> fixturesIds.add(match.getApiFootballId()));
    try {
      var fixtures = apiFootballConnector.getFixtures(fixturesIds);
      refreshFixtures(fixtures, seasonEntity);
    } catch (ApiFootballConnectorException ex) {
      log.error("Failed to refresh fixtures: {}", ex.getMessage());
    }
  }

  public void refreshFixtures(SeasonEntity seasonEntity) {
    log.info("Start refreshing fixtures data for the season {}", seasonEntity.getId());
    try {
      var fixtures = apiFootballConnector.getFixtures(seasonEntity.getCompetition().getApiFootballId(), seasonEntity.getYear());
      refreshFixtures(fixtures, seasonEntity);
    } catch (ApiFootballConnectorException ex) {
      log.error("Failed to refresh fixtures for {}: {}", seasonEntity.getCompetition().getName(), ex.getMessage());
    }
  }

  private void refreshFixtures(List<Fixture> fixtures, SeasonEntity seasonEntity) {
    var rounds = seasonEntity.getRounds();
    var matches = rounds.stream().flatMap(round -> round.getMatches().stream()).toList();

    fixtures.forEach(fixture -> {
      var fixtureData = fixture.getFixture();
      var score = fixture.getScore().getFulltime().getHome() != null ? fixture.getScore().getFulltime() : fixture.getGoals();
      var roundList = getRound(rounds, fixture.getLeague().getRound());
      if (roundList.isEmpty()) {
        refreshRounds(seasonEntity);
        roundList = getRound(rounds, fixture.getLeague().getRound());
      }

      MatchEntity matchEntity;
      var existingMatchEntity = matches.stream().filter(match -> match.getApiFootballId().equals(fixtureData.getId())).findFirst();
      if (existingMatchEntity.isPresent()) {
        matchEntity = existingMatchEntity.get();
      } else {
        matchEntity = new MatchEntity();
        matchEntity.setApiFootballId(fixtureData.getId());
        matchEntity.setHomeTeam(getTeamEntity(fixture.getTeams().getHome()));
        matchEntity.setAwayTeam(getTeamEntity(fixture.getTeams().getAway()));
      }

      // update round if needed
      var round = getRound(roundList, matchEntity.getHomeTeam(), matchEntity.getAwayTeam());
      if (matchEntity.getRound() == null) {
        matchEntity.setRound(round);
        round.getMatches().add(matchEntity);
      } else if (!matchEntity.getRound().equals(round)) {
        matchEntity.getRound().getMatches().remove(matchEntity);
        matchEntity.setRound(round);
        round.getMatches().add(matchEntity);
      }

      // update match status and results
      matchEntity.setHomeTeamScore(score.getHome());
      matchEntity.setAwayTeamScore(score.getAway());
      matchEntity.setStatus(mapStatus(fixture.getFixture().getStatus()));
      if (fixtureData.getDate() != null && matchEntity.getStatus() != MatchStatus.NOT_DEFINED) {
        matchEntity.setStartTime(fixtureData.getDate().toInstant());
      } else {
        matchEntity.setStartTime(null);
      }
    });
    seasonRepository.save(seasonEntity);
    log.info("Fixtures have been successfully updated for the season {}", seasonEntity.getId());
  }

  private MatchStatus mapStatus(Status status) {
    if (status == null || status.getStatus() == null) {
      return MatchStatus.NOT_DEFINED;
    }

    return switch (status.getStatus()) {
      case NS -> MatchStatus.PLANNED;
      case _1H, HT, _2H, LIVE -> MatchStatus.STARTED;
      case ABD, CANC, INT, PST, SUSP, WO, TBD -> MatchStatus.NOT_DEFINED;
      case AET, P, PEN, ET, AWD, BT, FT -> MatchStatus.FINISHED;
    };
  }

  private TeamEntity getTeamEntity(Team team) {
    var teamEntityOptional = teamRepository.findFirstByApiFootballId(team.getId());
    if (teamEntityOptional.isPresent()) {
      var teamEntity = teamEntityOptional.get();
      // if no changes for the team detected, return the existing entity, otherwise update it
      if (Objects.equals(teamEntity.getName(), team.getName()) && Objects.equals(teamEntity.getLogoUrl(), team.getLogo())) {
        return teamEntity;
      }
      teamEntity.setName(team.getName());
      teamEntity.setLogoUrl(team.getLogo());
      teamEntity = teamRepository.save(teamEntity);
      log.info("Team {} has been updated: {}", teamEntity.getName(), teamEntity.getId());
      return teamEntity;
    } else {
        return createTeam(team);
    }
  }

  private TeamEntity createTeam(Team team) {
    var teamEntity = new TeamEntity();
    teamEntity.setName(team.getName());
    teamEntity.setApiFootballId(team.getId());
    teamEntity.setLogoUrl(team.getLogo());
    teamEntity = teamRepository.save(teamEntity);
    log.info("New team {} has been created: {}", teamEntity.getName(), teamEntity.getId());
    return teamEntity;
  }

  private void validateActiveSeasons(UUID competitionId, Season season) {
    if (season.isActive()) {
      int activeSeasons = seasonRepository.countAllByActiveIsTrueAndCompetitionId(competitionId);
      if (activeSeasons > 0) {
        throw new RequestValidationException("Not possible to add more than one active season");
      }
    }
  }

  private void refreshRounds(SeasonEntity seasonEntity) {
    var actualRounds = apiFootballConnector.getRounds(seasonEntity.getCompetition().getApiFootballId(), seasonEntity.getYear());
    var roundEntities = seasonEntity.getRounds();
    var lastRound = roundEntities.stream().max(Comparator.comparingInt(RoundEntity::getOrderNumber)).orElse(null);
    AtomicInteger nextOrderNumber = new AtomicInteger(lastRound != null ? lastRound.getOrderNumber() + 1 : 1);
    for (var round : actualRounds) {
      if (roundEntities.stream().noneMatch(roundEntity -> roundEntity.getApiFootballId().equals(round))) {
        RoundType.getByAlias(round).forEach(roundType -> {
          var roundEntity = new RoundEntity();
          roundEntity.setType(roundType);
          roundEntity.setOrderNumber(getOrderNumber(round, roundType, nextOrderNumber));
          roundEntity.setApiFootballId(round);
          roundEntity.setSeason(seasonEntity);
          roundEntities.add(roundEntity);
        });
      }
    }
  }

  private int getOrderNumber(String roundName, RoundType roundType, AtomicInteger nextOrderNumber) {
    var pattern = Pattern.compile("^(.+) - (\\d+)$").matcher(roundName);
    if (pattern.matches()) {
      return Integer.parseInt(pattern.group(2));
    }
    return roundType == RoundType.QUALIFYING ? 0 : nextOrderNumber.getAndIncrement();
  }

  private List<RoundEntity> getRound(List<RoundEntity> rounds, String apiFootballId) {
    return rounds.stream().filter(roundEntity -> roundEntity.getApiFootballId().equals(apiFootballId)).toList();
  }

  private RoundEntity getRound(List<RoundEntity> roundList, TeamEntity homeTeam, TeamEntity awayTeam) {
    if (roundList.isEmpty()) {
      throw new FixturesSynchronizationException("Round could not be find for the match between " + homeTeam.getName() + " and " + awayTeam.getName());
    }

    if (roundList.size() == 1) {
      return roundList.get(0);
    }

    if (roundList.size() == 2) {
      RoundEntity firstRound;
      RoundEntity returnRound;

      if (roundList.get(0).getType().name().endsWith("RETURN")) {
        firstRound = roundList.get(1);
        returnRound = roundList.get(0);
      } else {
        firstRound = roundList.get(0);
        returnRound = roundList.get(1);
      }

      var returnMatch = firstRound.getMatches().stream()
          .anyMatch(matchEntity -> matchEntity.getHomeTeam().equals(awayTeam) && matchEntity.getAwayTeam().equals(homeTeam));
      if (returnMatch) {
        return returnRound;
      } else {
        return firstRound;
      }
    }

    throw new FixturesSynchronizationException("Unexpected number of rounds found for the match between "
        + homeTeam.getName() + " and " + awayTeam.getName() + ": " + roundList);
  }
}

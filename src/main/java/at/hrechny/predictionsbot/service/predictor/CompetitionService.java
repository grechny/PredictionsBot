package at.hrechny.predictionsbot.service.predictor;

import at.hrechny.predictionsbot.connector.apifootball.ApiFootballConnector;
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException;
import at.hrechny.predictionsbot.connector.apifootball.model.Fixture;
import at.hrechny.predictionsbot.connector.apifootball.model.Status;
import at.hrechny.predictionsbot.connector.apifootball.model.Team;
import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.TeamEntity;
import at.hrechny.predictionsbot.database.model.MatchStatus;
import at.hrechny.predictionsbot.database.repository.MatchRepository;
import at.hrechny.predictionsbot.database.repository.TeamRepository;
import at.hrechny.predictionsbot.model.Competition;
import at.hrechny.predictionsbot.model.Season;
import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.repository.CompetitionRepository;
import at.hrechny.predictionsbot.database.repository.SeasonRepository;
import at.hrechny.predictionsbot.mapper.CompetitionMapper;
import at.hrechny.predictionsbot.mapper.SeasonMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
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
        .orElseThrow(() -> new IllegalArgumentException("Competition with the specified ID not found"));
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
        .orElseThrow(() -> new IllegalArgumentException("Competition with the specified ID not found"));

    validateActiveSeasons(competitionId, season);

    var seasonEntity = seasonMapper.modelToEntity(competitionEntity, season);
    var rounds = apiFootballConnector.getRounds(competitionEntity.getApiFootballId(), seasonEntity.getYear());
    seasonEntity.setApiFootballRounds(mapRounds(rounds));
    seasonEntity = seasonRepository.save(seasonEntity);
    log.info("The season has been successfully stored");

    return seasonEntity.getId();
  }

  public void updateSeason(UUID competitionId, Season season) {
    log.info("Updating the season {} for the competition {}", season.getId(), competitionId);
    var seasonEntity = seasonRepository.findById(season.getId()).orElse(null);
    if (seasonEntity == null || !seasonEntity.getCompetition().getId().equals(competitionId)) {
      throw new IllegalArgumentException("Season with the specified ID not found");
    }

    validateActiveSeasons(competitionId, season);

    seasonMapper.updateEntity(seasonEntity, season);
    seasonRepository.save(seasonEntity);
    log.info("The season {} has been successfully updated", seasonEntity.getId());
  }

  public List<Season> getSeasons(UUID competitionId) {
    var entityList = seasonRepository.findAllByCompetition_Id(competitionId);
    var seasonList = new ArrayList<Season>();
    entityList.forEach(entity -> seasonList.add(seasonMapper.entityToModel(entity)));
    return seasonList;
  }

  public SeasonEntity getCurrentSeason(UUID competitionId) {
    return seasonRepository.findFirstByCompetition_IdAndActiveIsTrue(competitionId)
        .orElseThrow(() -> new IllegalArgumentException("No active season found for the competition " + competitionId));
  }

  public Integer getUpcomingRound(UUID competitionId) {
    return matchRepository.findUpcoming(getCurrentSeason(competitionId)).map(MatchEntity::getRound).orElse(null);
  }

  public List<MatchEntity> getFixtures(Instant from, Instant to) {
    return matchRepository.findAllByStartTimeAfterAndStartTimeBeforeOrderByStartTimeAsc(from, to);
  }

  public List<MatchEntity> getFixtures(UUID competitionId, Integer round) {
    if (round == null || round.equals(0)) {
      return Collections.emptyList();
    }

    return matchRepository.findAllBySeasonAndRoundOrderByStartTimeAsc(getCurrentSeason(competitionId), round);
  }

  public void refreshFixtures() {
    log.info("Start refreshing fixtures data for the all active competitions");
    var activeSeasons = seasonRepository.findAllByActiveIsTrue();
    activeSeasons.forEach(this::refreshFixtures);
  }

  public void refreshActiveFixtures() {
    var activeMatches = matchRepository.findAllActive();
    if (activeMatches.isEmpty()) {
      return;
    }

    var fixturesIds = new ArrayList<Long>();
    activeMatches.forEach(match -> fixturesIds.add(match.getApiFootballId()));
    try {
      var fixtures = apiFootballConnector.getFixtures(fixturesIds);
      var leagueFixturesGroups = fixtures.stream().collect(Collectors.groupingBy(fixture -> fixture.getLeague().getId()));
      for (var leagueFixturesGroup : leagueFixturesGroups.entrySet()) {
        var leagueId = leagueFixturesGroup.getKey();
        var oneLeagueFixtures = leagueFixturesGroup.getValue();
        var seasonEntity = activeMatches.stream()
            .map(MatchEntity::getSeason)
            .filter(season -> leagueId.equals(season.getCompetition().getApiFootballId()))
            .findFirst().orElseThrow(() -> new RuntimeException("No league found for the match"));
        refreshFixtures(oneLeagueFixtures, seasonEntity);
      }
    } catch (ApiFootballConnectorException ex) {
      log.error("Failed to refresh fixtures: {}", ex.getMessage());
    }
  }

  private void refreshFixtures(SeasonEntity seasonEntity) {
    log.info("Start refreshing fixtures data for the season {}", seasonEntity.getId());
    try {
      var fixtures = apiFootballConnector.getFixtures(seasonEntity.getCompetition().getApiFootballId(), seasonEntity.getYear());
      refreshFixtures(fixtures, seasonEntity);
    } catch (ApiFootballConnectorException ex) {
      log.error("Failed to refresh fixtures: {}", ex.getMessage());
    }
  }

  private void refreshFixtures(List<Fixture> fixtures, SeasonEntity seasonEntity) {
    var rounds = seasonEntity.getApiFootballRounds().stream()
        .collect(Collectors.toMap(RoundEntity::getApiFootballRoundName, RoundEntity::getOrderNumber));

    fixtures.forEach(fixture -> {
      var fixtureData = fixture.getFixture();
      var score = fixture.getScore().getFulltime().getHome() != null ? fixture.getScore().getFulltime() : fixture.getGoals();

      MatchEntity matchEntity;
      var existingMatchEntity = matchRepository.findFirstByApiFootballId(fixtureData.getId());
      if (existingMatchEntity.isPresent()) {
        matchEntity = existingMatchEntity.get();
      } else {
        var round = rounds.get(fixture.getLeague().getRound());
        if (round == null) {
          refreshRounds(seasonEntity, rounds);
          round = rounds.get(fixture.getLeague().getRound());
        } else if (round == 0) {
          return;
        }

        matchEntity = new MatchEntity();
        matchEntity.setApiFootballId(fixtureData.getId());
        matchEntity.setSeason(seasonEntity);
        matchEntity.setRound(round);
        matchEntity.setHomeTeam(getTeamEntity(fixture.getTeams().getHome()));
        matchEntity.setAwayTeam(getTeamEntity(fixture.getTeams().getAway()));
        seasonEntity.getMatches().add(matchEntity);
      }

      matchEntity.setStartTime(fixtureData.getDate() != null ? fixtureData.getDate().toInstant() : null);
      matchEntity.setHomeTeamScore(score.getHome());
      matchEntity.setAwayTeamScore(score.getAway());
      matchEntity.setStatus(mapStatus(fixture.getFixture().getStatus()));
    });
    seasonRepository.save(seasonEntity);
    log.info("Fixtures have been successfully updated for the season {}", seasonEntity.getId());
  }

  private void refreshRounds(SeasonEntity seasonEntity, Map<String, Integer> rounds) {
    var actualRounds = apiFootballConnector.getRounds(seasonEntity.getCompetition().getApiFootballId(), seasonEntity.getYear());
    var roundEntities = seasonEntity.getApiFootballRounds();
    var maxOrderNumberRound = roundEntities.stream().max(Comparator.comparingInt(RoundEntity::getOrderNumber)).orElse(null);
    var nextOrderNumber = maxOrderNumberRound != null ? maxOrderNumberRound.getOrderNumber() + 1 : 0;
    AtomicInteger i = new AtomicInteger(nextOrderNumber);
    actualRounds.stream()
        .filter(round -> !rounds.containsKey(round))
        .forEach(round -> {
          var roundEntity = new RoundEntity();
          roundEntity.setOrderNumber(i.getAndIncrement());
          roundEntity.setApiFootballRoundName(round);
          roundEntities.add(roundEntity);
        });
    seasonRepository.save(seasonEntity);
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
    return teamEntityOptional.orElseGet(() -> createTeam(team));
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
      int activeSeasons = seasonRepository.countAllByActiveIsTrueAndCompetition_Id(competitionId);
      if (activeSeasons > 0) {
        throw new IllegalArgumentException("Not possible to add more than one active season");
      }
    }
  }

  private List<RoundEntity> mapRounds(List<String> rounds) {
    var roundEntities = new ArrayList<RoundEntity>();
    AtomicInteger i = new AtomicInteger(1);
    rounds.forEach(round -> {
      var roundEntity = new RoundEntity();
      roundEntity.setOrderNumber(i.getAndIncrement());
      roundEntity.setApiFootballRoundName(round);
      roundEntities.add(roundEntity);
    });
    return roundEntities;
  }

}

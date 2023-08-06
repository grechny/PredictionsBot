package at.hrechny.predictionsbot.service.predictor;

import at.hrechny.predictionsbot.database.entity.LeagueEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.repository.CompetitionRepository;
import at.hrechny.predictionsbot.database.repository.LeagueRepository;
import at.hrechny.predictionsbot.database.repository.TeamRepository;
import at.hrechny.predictionsbot.database.repository.UserRepository;
import at.hrechny.predictionsbot.exception.InputValidationException;
import at.hrechny.predictionsbot.exception.LimitExceededException;
import at.hrechny.predictionsbot.model.LeagueRequest;
import at.hrechny.predictionsbot.model.LeagueResponse;
import at.hrechny.predictionsbot.util.NameUtils;
import jakarta.transaction.Transactional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeagueService {

  private static final int MAX_LEAGUES_PER_USER = 10;

  private final LeagueRepository leagueRepository;
  private final TeamRepository teamRepository;
  private final UserRepository userRepository;
  private final CompetitionRepository competitionRepository;

  @Transactional
  public LeagueResponse create(Long userId, LeagueRequest leagueRequest) {
    log.info("Creating new league {} by user {}", leagueRequest.getName(), userId);

    validateName(leagueRequest.getName());
    var adminUser = getUser(userId);
    var competitions = competitionRepository.findAll().stream()
        .filter(competitionEntity -> leagueRequest.getCompetitions().contains(competitionEntity.getId()))
        .toList();

    var leagueEntity = new LeagueEntity();
    leagueEntity.setName(leagueRequest.getName());
    leagueEntity.setAdminUser(adminUser);
    leagueEntity.setUsers(Set.of(adminUser));
    leagueEntity.setCompetitions(competitions);
    leagueEntity = leagueRepository.save(leagueEntity);
    log.info("Created new league {}", leagueEntity.getId());
    return new LeagueResponse(leagueEntity.getId());
  }

  @Transactional
  public LeagueResponse update(Long userId, UUID leagueId, LeagueRequest leagueRequest) {
    var adminUser = getUser(userId);
    var league = leagueRepository.findById(leagueId).orElseThrow();

    if (!league.getAdminUser().equals(adminUser)) {
      throw new InputValidationException("User is not the admin of this league and cannot update it");
    }

    return null;
  }

  @Transactional
  public LeagueResponse join(Long userId, UUID leagueId) {
    return null;
  }

  @Transactional
  public LeagueResponse delete(Long userId, UUID leagueId) {
    return null;
  }

  @Transactional
  public LeagueEntity joinLeague(String leagueIdString, Long userId) {
    UUID leagueId;
    try {
      leagueId = UUID.fromString(leagueIdString);
    } catch (IllegalArgumentException e) {
      log.warn("Invalid league id {}", leagueIdString);
      throw new InputValidationException("Invalid league id");
    }

    var league = leagueRepository.findById(leagueId);
    if (league.isEmpty()) {
      throw new InputValidationException("Invalid league id");
    }

    var leagueEntity = league.get();
    if (leagueEntity.getUsers().stream().anyMatch(u -> u.getId().equals(userId))) {
      throw new InputValidationException("User is already a member of this league");
    }

    var user = userRepository.findById(userId).orElseThrow();
    if (user.getLeagues().size() > MAX_LEAGUES_PER_USER) {
      throw new LimitExceededException("User is already a member of another league");
    }

    leagueEntity.getUsers().add(user);
    leagueRepository.save(leagueEntity);
    log.info("User {} added to the league {}", userId, leagueId);
    return leagueEntity;
  }

  private void validateName(String leagueName) throws InputValidationException {
    log.info("Validating league name {}", leagueName);

    if (StringUtils.isBlank(leagueName)) {
      throw new InputValidationException("League name cannot be empty");
    }

    if (!NameUtils.isValid(leagueName)) {
      throw new InputValidationException("League name should be 3-20 characters long and contain only valid characters");
    }
  }

  private UserEntity getUser(Long userId) {
    var user = userRepository.findById(userId).orElseThrow();
    if (user.getLeagues().size() > MAX_LEAGUES_PER_USER) {
      throw new LimitExceededException("User is already a member of another league");
    }
    return user;
  }

}

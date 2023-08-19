package at.hrechny.predictionsbot.service.predictor;

import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.repository.UserRepository;
import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.exception.RequestValidationException;
import at.hrechny.predictionsbot.util.NameUtils;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  public void createUser(Long userId, String username, String language) {
    log.info("Creating new user {} with id {}", username, userId);
    var userEntity = userRepository.findById(userId).orElse(new UserEntity());
    userEntity.setId(userId);
    userEntity.setInitialLanguage(new Locale(language));
    userEntity.setUsername(userEntity.getUsername() == null ? NameUtils.formatName(username) : userEntity.getUsername());
    userEntity.setTimezone(userEntity.getTimezone() == null ? ZoneOffset.UTC : userEntity.getTimezone());
    userEntity.setActive(true);
    userRepository.save(userEntity);
    log.info("Created user {} with id {}", userEntity.getUsername(), userEntity.getId());
  }

  public void saveUser(UserEntity userEntity) {
    userRepository.save(userEntity);
    log.info("Added/updated user {} with id {}", userEntity.getUsername(), userEntity.getId());
  }

  public UserEntity getUser(Long userId) {
    return userRepository.findByIdAndActiveIsTrue(userId).orElseThrow(() -> new NotFoundException("User not found"));
  }

  public List<UserEntity> getUsers() {
    return userRepository.findAllByActiveIsTrue();
  }

  public List<UserEntity> getUsers(UUID competitionId) {
    return userRepository.findAllActiveByCompetitionsId(competitionId);
  }

  public void updateUsername(Long userId, String username) {
    log.info("Updating username to '{}' for the {}", username, userId);
    if (StringUtils.isBlank(username)) {
      throw new RequestValidationException("Username can not be null");
    }

    var userEntity = getUser(userId);
    userEntity.setUsername(username);
    saveUser(userEntity);
  }

  public void updateTimeZone(Long userId, String zoneId) {
    log.info("Updating time zone to '{}' for the {}", zoneId, userId);
    var userEntity = getUser(userId);
    userEntity.setTimezone(ZoneId.of(zoneId));
    saveUser(userEntity);
  }

  public void updateLanguage(Long userId, String language) {
    log.info("Updating language to '{}' for the {}", language, userId);
    var userEntity = getUser(userId);
    userEntity.setLanguage(language != null ? new Locale(language) : null);
    saveUser(userEntity);
  }

  @Transactional
  public void updateCompetitions(Long userId, UUID competitionId) {
    log.info("Updating competitions for the {}", userId);
    var userEntity = getUser(userId);
    var competitionOptional = userEntity.getCompetitions().stream().filter(competition -> competition.getId().equals(competitionId)).findFirst();
    if (competitionOptional.isPresent()) {
      userEntity.getCompetitions().remove(competitionOptional.get());
    } else {
      var competitionEntity = new CompetitionEntity();
      competitionEntity.setId(competitionId);
      userEntity.getCompetitions().add(competitionEntity);
    }
    saveUser(userEntity);
  }

  public void deactivate(Long userId) {
    log.info("Deactivating user {}", userId);
    var userEntity = getUser(userId);
    userEntity.setActive(false);
    saveUser(userEntity);
  }
}

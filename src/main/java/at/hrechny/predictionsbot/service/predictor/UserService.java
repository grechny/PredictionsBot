package at.hrechny.predictionsbot.service.predictor;

import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.repository.UserRepository;
import java.time.ZoneId;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;

  public void saveUser(UserEntity userEntity) {
    userRepository.save(userEntity);
    log.info("Added/updated user {} with id {}", userEntity.getUsername(), userEntity.getId());
  }

  public UserEntity getUser(Long userId) {
    return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
  }

  public void updateUsername(Long userId, String username) {
    log.info("Updating username to '{}' for the {}", username, userId);
    if (StringUtils.isBlank(username)) {
      throw new IllegalArgumentException("Username can not be null");
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
}

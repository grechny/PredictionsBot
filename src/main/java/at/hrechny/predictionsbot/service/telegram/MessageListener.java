package at.hrechny.predictionsbot.service.telegram;

import at.hrechny.predictionsbot.model.Prediction;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.util.TimeZoneUtils;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import java.util.List;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageListener implements UpdatesListener {

  private ObjectMapper objectMapper;

  private final TelegramService telegramService;
  private final PredictionService predictionService;
  private final UserService userService;

  @PostConstruct
  public void init() {
    initObjectMapper();
    telegramService.setUpListener(this);
  }

  @SneakyThrows
  public int process(List<Update> updates) {
    log.debug("Processing Bot updates: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updates));
    for (var updateMessage : updates) {
      try {
        if (updateMessage.callbackQuery() != null) {
          var callbackQuery = updateMessage.callbackQuery();
          if (callbackQuery.data().startsWith("/language ")) {
            updateLanguage(callbackQuery.from(), callbackQuery.data().substring(10));
          }
          continue;
        }

        var message = updateMessage.message() != null ? updateMessage.message() : updateMessage.editedMessage();
        if (message == null) {
          continue;
        }

        if (updateMessage.callbackQuery() != null) {
          log.info("Got callback query with data: {}", updateMessage.callbackQuery().data());
        } else if (message.location() != null) {
          updateLocation(message);
        } else if (message.webAppData() != null) {
          savePredictions(message);
        } else if (message.text() != null) {
          var user = message.from();

          switch (message.text()) {
            case "/start" -> telegramService.startBot(user);
            case "/predictions" -> telegramService.sendPredictions(user);
            case "/results" -> telegramService.sendResults(user);
            case "/timezone" -> telegramService.sendTimezoneMessage(user);
            case "/language" -> telegramService.sendLanguageMessage(user);
            case "/help" -> telegramService.sendHelp(user);
            case "/stop" -> stopBot(user);
            default -> {
              if (message.text().startsWith("/username ")) {
                updateUsername(user, message.text().substring(10));
              } else if (message.text().startsWith("/language ")) {
                updateLanguage(user, message.text().substring(10));
              } else {
                log.warn("Got the message which won't be processed: {}", message.text());
              }
            }
          }
        } else {
          log.warn("Got unexpected update: {}", updateMessage.updateId());
        }
      } catch (Exception ex) {
        log.error("Unable to process an update {}", updateMessage.updateId(), ex);
      }
    }

    return UpdatesListener.CONFIRMED_UPDATES_ALL;
  }

  private void initObjectMapper() {
    objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }

  @SneakyThrows
  private void savePredictions(Message message) {
    var predictions = objectMapper.readValue(message.webAppData().data(), new TypeReference<List<Prediction>>() {});
    predictionService.savePredictions(message.from().id(), predictions);
  }

  private void updateLocation(Message message) {
    var user = message.from();
    var location = message.location();
    var zoneId = TimeZoneUtils.getTimeZone(location.latitude(), location.longitude());
    if (zoneId != null) {
      userService.updateTimeZone(user.id(), zoneId);
    }

    telegramService.sendUpdateLocationConfirmation(user, zoneId);
  }

  private void updateUsername(User user, String username) {
    username = StringUtils.normalizeSpace(username);
    if (username.length() >= 3 && username.length() <= 20) {
      userService.updateUsername(user.id(), username);
    } else {
      log.warn("Username has not meet length criteria");
      username = null;
    }

    telegramService.sendUsernameConfirmation(user, username);
  }

  private void updateLanguage(User user, String language) {
    if (StringUtils.isBlank(language) || language.equalsIgnoreCase("system")) {
      language = null;
    }

    userService.updateLanguage(user.id(), language);
    telegramService.sendLanguageConfirmation(user);
  }

  private void stopBot(User user) {
    telegramService.stopBot(user);
    userService.deactivate(user.id());
  }

}

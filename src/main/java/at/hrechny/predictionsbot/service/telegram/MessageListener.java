package at.hrechny.predictionsbot.service.telegram;

import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
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
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
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
  @EnableErrorReport
  @SuppressWarnings("java:S135")
  public int process(List<Update> updates) {
    log.debug("Processing Bot updates: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updates));
    for (var updateMessage : updates) {
      try {
        if (updateMessage.callbackQuery() != null) {
          processCallbackQuery(updateMessage.callbackQuery());
          continue;
        }

        var message = updateMessage.message() != null ? updateMessage.message() : updateMessage.editedMessage();
        if (message == null) {
          continue;
        }

        if (message.location() != null) {
          updateLocation(message);
        } else if (message.webAppData() != null) {
          savePredictions(message);
        } else if (message.text() != null) {
          processMessageText(message);
        } else {
          log.warn("Got unexpected update: {}", updateMessage.updateId());
        }
      } catch (Exception ex) {
        log.error("Unable to process an update {}", updateMessage.updateId(), ex);
        telegramService.sendErrorReport(ex);
      }
    }

    return UpdatesListener.CONFIRMED_UPDATES_ALL;
  }

  private void processMessageText(Message message) {
    var user = message.from();

    try {
      switch (message.text()) {
        case "/start" -> telegramService.startBot(user);
        case "/predictions" -> telegramService.sendPredictions(user);
        case "/results" -> telegramService.sendResults(user);
        case "/leagues" -> telegramService.sendLeagues(user);
        case "/competitions" -> telegramService.sendCompetitions(user);
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
    } catch (NotFoundException notFoundException) {
      log.warn("Active user {} is not found. Trying to send a message", user.id());
      telegramService.sendActivateMessage(user);
    }
  }

  private void processCallbackQuery(CallbackQuery callbackQuery) {
    var messageId = callbackQuery.message().messageId();
    if (callbackQuery.data().startsWith("/language ")) {
      updateLanguage(callbackQuery.from(), callbackQuery.data().substring(10));
    } else if (callbackQuery.data().startsWith("/results")) {
      telegramService.sendResults(callbackQuery.from(), messageId);
    } else if (callbackQuery.data().startsWith("/seasons ")) {
      var competitionId = UUID.fromString(callbackQuery.data().substring(9));
      telegramService.sendResultsBySeasons(callbackQuery.from(), competitionId, messageId);
    } else if (callbackQuery.data().startsWith("/season ")) {
      var seasonId = UUID.fromString(callbackQuery.data().substring(8));
      telegramService.sendResults(callbackQuery.from(), seasonId, messageId);
    } else if (callbackQuery.data().startsWith("/competition ")) {
      var competitionId = UUID.fromString(callbackQuery.data().substring(13));
      userService.updateCompetitions(callbackQuery.from().id(), competitionId);
      telegramService.sendCompetition(callbackQuery.from(), messageId, competitionId);
    } else if (callbackQuery.data().startsWith("/competitions ")) {
      var competitionId = UUID.fromString(callbackQuery.data().substring(14));
      userService.updateCompetitions(callbackQuery.from().id(), competitionId);
      telegramService.sendCompetitions(callbackQuery.from(), messageId, competitionId);
    }
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

  private void initObjectMapper() {
    objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  }
}

package at.hrechny.predictionsbot.service.impl;

import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.model.Prediction;
import at.hrechny.predictionsbot.service.CompetitionService;
import at.hrechny.predictionsbot.service.PredictionService;
import at.hrechny.predictionsbot.service.TelegramService;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.WebAppInfo;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.SendMessage;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import us.dustinj.timezonemap.TimeZoneMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramServiceImpl implements TelegramService {

  @Value("${telegram.token}")
  private String botToken;

  @Value("${application.url}")
  private String applicationUrl;

  @Value("${secrets.telegramKey}")
  private String telegramKey;

  private TelegramBot telegramBot;
  private TimeZoneMap timezoneMap;
  private ObjectMapper objectMapper;

  private final MessageSource messageSource;
  private final PredictionService predictionService;
  private final CompetitionService competitionService;

  @PostConstruct
  public void init() {
    objectMapper = new ObjectMapper();
    objectMapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    objectMapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    timezoneMap = TimeZoneMap.forEverywhere();

    telegramBot = new TelegramBot(botToken);
    telegramBot.setUpdatesListener(this::setUpListener);
  }

  @SneakyThrows
  private int setUpListener(List<Update> updates) {
    log.debug("Processing Bot updates: {}", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(updates));
    for (var updateMessage : updates) {
      try {
        var message = updateMessage.message();
        if (updateMessage.callbackQuery() != null) {
          log.info("Got callback query with data: {}", updateMessage.callbackQuery().data());
        } else if (message != null && message.location() != null) {
          updateLocation(message);
        } else if (message != null && message.webAppData() != null) {
          savePredictions(message);
        } else if (message != null && message.text() != null) {
          var user = message.from();

          switch (message.text()) {
            case "/start" -> startBot(user);
            case "/predictions" -> sendPredictions(user.id());
//          case "/results" -> sendResults(userId);
//          case "/fixtures" -> sendFixtures(userId);
//          case "/standings" -> sendStandings(userId);
//          case "/help" -> sendHelp(userId);
            default -> log.info("Got the message: {}", message.text());
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

  private void sendMessage(SendMessage message) {
    var response = telegramBot.execute(message);
    log.info("Message {} has been successfully sent", response.message().messageId());
  }

  private void startBot(User user) {
    SendMessage message;
    var locale = new Locale(user.languageCode());
    if (predictionService.getUser(user.id()) != null) {
      message = buildChangeTimezoneMessage(user.id(), locale);
    } else {
      var username = user.username();
      if (StringUtils.isBlank(username)) {
        username = StringUtils.isNotBlank(user.firstName()) ? user.firstName() : user.lastName();
      }
      predictionService.saveUser(new UserEntity(user.id(), username, locale, ZoneOffset.UTC));
      message = buildGreetingMessage(user.id(), username, locale);
    }
    sendMessage(message);
  }

  private void updateLocation(Message message) {
    SendMessage sendMessage;
    var userEntity = predictionService.getUser(message.from().id());
    var location = message.location();
    var timeZone = timezoneMap.getOverlappingTimeZone(location.latitude(), location.longitude());
    if (timeZone != null) {
      var zoneId = timeZone.getZoneId();
      userEntity.setTimezone(ZoneId.of(zoneId));
      predictionService.saveUser(userEntity);
      sendMessage = new SendMessage(userEntity.getId(), messageSource.getMessage("start.location", List.of(zoneId).toArray(), userEntity.getLanguage()));
      sendMessage.replyMarkup(new ReplyKeyboardRemove());
    } else {
      sendMessage = new SendMessage(userEntity.getId(), messageSource.getMessage("start.location.error", null, userEntity.getLanguage()));
      sendMessage.replyMarkup(new ReplyKeyboardRemove());
    }
    sendMessage(sendMessage);
  }

  private void sendPredictions(Long userId) {
    var buttons = new ArrayList<KeyboardButton>();

    var user = predictionService.getUser(userId);
    var competitions = competitionService.getCompetitions();
    competitions.forEach(competition -> {
      if (competition.isActive()) {
        var keyboardButton = new KeyboardButton(competition.getName());
        keyboardButton.webAppInfo(new WebAppInfo(buildPredictionsUrl(userId, competition.getId())));
        buttons.add(keyboardButton);
      }
    });

    String message;
    if (buttons.isEmpty()) {
      message = messageSource.getMessage("predictions.no_competitions", null, user.getLanguage());
    } else {
      message = messageSource.getMessage("predictions", null, user.getLanguage());
    }
    SendMessage sendMessage = new SendMessage(userId, message);
    sendMessage.replyMarkup(new ReplyKeyboardMarkup(buttons.toArray(new KeyboardButton[0])).resizeKeyboard(true));
    sendMessage(sendMessage);
  }

  @SneakyThrows
  private void savePredictions(Message message) {
    var predictions = objectMapper.readValue(message.webAppData().data(), new TypeReference<List<Prediction>>() {});
    predictionService.savePredictions(message.from().id(), predictions);
  }

  private String buildPredictionsUrl(Long userId, UUID competitionId) {
    return applicationUrl + "/" + telegramKey + "/users/" + userId + "/predictions?leagueId=" + competitionId;
  }

  private SendMessage buildGreetingMessage(Long userId, String username, Locale locale) {
    var locationButton = new KeyboardButton(messageSource.getMessage("start.button", null, locale));
    locationButton.requestLocation(true);

    var message = new SendMessage(userId, messageSource.getMessage("start.greeting", List.of(username).toArray(), locale));
    return message.replyMarkup(new ReplyKeyboardMarkup(locationButton).resizeKeyboard(true));
  }

  private SendMessage buildChangeTimezoneMessage(Long userId, Locale locale) {
    var locationButton = new KeyboardButton(messageSource.getMessage("start.button", null, locale));
    locationButton.requestLocation(true);

    var message = new SendMessage(userId, messageSource.getMessage("start.location.change", null, locale));
    return message.replyMarkup(new ReplyKeyboardMarkup(locationButton).resizeKeyboard(true));
  }

}

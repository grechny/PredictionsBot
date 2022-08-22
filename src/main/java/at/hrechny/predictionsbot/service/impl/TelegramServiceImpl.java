package at.hrechny.predictionsbot.service.impl;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.service.CompetitionService;
import at.hrechny.predictionsbot.service.PredictionService;
import at.hrechny.predictionsbot.service.TelegramService;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.SendMessage;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
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
      var message = updateMessage.message();
      if (updateMessage.callbackQuery() != null) {
        log.info("Got callback query with data: {}", updateMessage.callbackQuery().data());
        continue;
      } else if (message != null && message.location() != null) {
        updateLocation(message);
        continue;
      } else if (message != null && message.text() != null) {
        var user = updateMessage.message().from();
        var userId = user.id();

        switch (message.text()) {
          case "/start" -> startBot(user);
          case "/predictions" -> sendPredictions(userId);
//        case "/results" -> sendResults(userId);
//        case "/fixtures" -> sendFixtures(userId);
//        case "/standings" -> sendStandings(userId);
//        case "/help" -> sendHelp(userId);
          default -> {
            log.info("Got the message: {}", message.text());
          }
        }
      } else
        log.warn("Got unexpected update: {}", updateMessage.updateId());
        continue;
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
      predictionService.saveUser(new UserEntity(user.id(), user.username(), locale, null));
      message = buildGreetingMessage(user.id(), user.username(), locale);
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

  // if there is any match without predictions made in next 3 days - show the buttons for the round
  // if the matches already has predictions - show the buttons for the active championships (if any match is planned for the next month)
  // if there is more than one season in one day without predictions - show the buttons for the championship
  // if the matches for the day already has predictions - show the button edit and then button for the next round (by the date - check if we didn't have this round before)
  // in that case show the nearest matches (3+)
  // if the next round has already predictions - show edit button and show next one
  // then show the matches for the next round
  private void sendPredictions(Long userId) {
    var user = predictionService.getUser(userId);
    var upcomingFixtures = competitionService.getUpcomingFixtures();
    var next3daysMatches = upcomingFixtures.stream().filter(match -> match.getStartTime().isBefore(Instant.now().plus(Duration.ofDays(3)))).toList();
    if (!next3daysMatches.isEmpty()) {
      for (var match : next3daysMatches) {
        var predictionMissed = match.getPredictions().stream().noneMatch(prediction -> prediction.getUser().getId().equals(userId));
        if (predictionMissed) {
          var roundMatches = competitionService.getFixtures(match.getSeason().getId(), match.getRound());
          var predictionTableMessage = buildPredictionTableMessage(user, roundMatches);
          sendMessage(predictionTableMessage);
          return;
        }
      }
    }
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

  private SendMessage buildPredictionTableMessage(UserEntity user, List<MatchEntity> roundMatches) {
    var season = roundMatches.get(0).getSeason();
    var competition = season.getCompetition().getName();
    var roundName = season.getApiFootballRounds().stream()
        .filter(round -> roundMatches.get(0).getRound().equals(round.getOrderNumber()))
        .findAny().orElseThrow(() -> new RuntimeException("Unable to find the round"))
        .getRoundName();

    var keyboard = new ArrayList<InlineKeyboardButton[]>();
    for (var match : roundMatches) {
      var keyboardRow = new ArrayList<InlineKeyboardButton>();
      var homePrediction = 0;
      var awayPrediction = 0;
      var radioButton = "\u26AA";
      var userPredictionOptional = match.getPredictions().stream()
          .filter(prediction -> prediction.getUser().equals(user))
          .findFirst();
      if (userPredictionOptional.isPresent()) {
        var userPrediction = userPredictionOptional.get();
        homePrediction = userPrediction.getPredictionHome();
        awayPrediction = userPrediction.getPredictionAway();
        if (userPrediction.isDoubleUp()) {
          radioButton = "\uD83D\uDD18";
        }
      }
      keyboardRow.add(new InlineKeyboardButton(match.getHomeTeam().getName() + "\n\r" + homePrediction).callbackData("pressed"));
      keyboardRow.add(new InlineKeyboardButton(match.getStartTime().atZone(user.getTimezone()).format(DateTimeFormatter.ofPattern("dd.MM.yyyy hh.mm")) + "\n" + radioButton).callbackData("pressed"));
      keyboardRow.add(new InlineKeyboardButton(match.getAwayTeam().getName() + "\n" + awayPrediction).callbackData("pressed"));
      keyboard.add(keyboardRow.toArray(new InlineKeyboardButton[0]));
    }

    keyboard.add(new InlineKeyboardButton[] { new InlineKeyboardButton(messageSource.getMessage("predictions.save", null, user.getLanguage())).callbackData("pressed") } );

    var message = new SendMessage(user.getId(), messageSource.getMessage("predictions.round", List.of(competition, roundName).toArray(), user.getLanguage()));
    message.replyMarkup(new InlineKeyboardMarkup(keyboard.toArray(new InlineKeyboardButton[0][])));
    return message;
  }

}

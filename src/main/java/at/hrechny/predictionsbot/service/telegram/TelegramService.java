package at.hrechny.predictionsbot.service.telegram;

import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.WebAppInfo;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.SendMessage;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramService {

  @Value("${telegram.token}")
  private String botToken;

  @Value("${application.url}")
  private String applicationUrl;

  @Value("${secrets.telegramKey}")
  private String telegramKey;

  private TelegramBot telegramBot;

  private final MessageSource messageSource;
  private final CompetitionService competitionService;
  private final UserService userService;

  @PostConstruct
  public void init() {
    telegramBot = new TelegramBot(botToken);
  }

  public void setUpListener(UpdatesListener updatesListener) {
    telegramBot.setUpdatesListener(updatesListener);
  }

  public void sendMessage(SendMessage message) {
    var response = telegramBot.execute(message);
    if (response.isOk()) {
      log.info("Message {} has been successfully sent", response.message().messageId());
    } else {
      log.error("Message was not send: [{}] {}", response.errorCode(), response.description());
    }
  }

  public void startBot(User user) {
    SendMessage message;
    try {
      userService.getUser(user.id());
      sendHelp(user);
      return;
    } catch (NotFoundException ex) {
      log.info("No active user found with id {}. New user will be created", user.id());
    }

    var username = user.username();
    if (StringUtils.isBlank(username)) {
      username = StringUtils.isNotBlank(user.firstName()) ? user.firstName() : user.lastName();
    }
    userService.saveUser(new UserEntity(user.id(), username, null, ZoneOffset.UTC, true));
    message = buildGreetingMessage(user, username);
    sendMessage(message);
  }

  public void sendHelp(User user) {
    var sendMessage = new SendMessage(user.id(), messageSource.getMessage("help", null, getLocale(user)));
    sendMessage(sendMessage);
  }

  public void sendTimezoneMessage(User user) {
    var locale = getLocale(user);
    var locationButton = new KeyboardButton(messageSource.getMessage("buttons.location", null, locale));
    locationButton.requestLocation(true);

    var message = new SendMessage(user.id(), messageSource.getMessage("start.location.change", null, locale));
    message.replyMarkup(new ReplyKeyboardMarkup(locationButton).resizeKeyboard(true));
    sendMessage(message);
  }

  public void sendPredictions(User user) {
    sendCompetitions(user, "predictions");
  }

  public void sendResults(User user) {
    sendCompetitions(user, "results");
  }

  public void sendLanguageMessage(User user) {
    var locale = getLocale(user);

    var ruLanguageButton = new InlineKeyboardButton("Русский");
    ruLanguageButton.callbackData("/language ru");

    var enLanguageButton = new InlineKeyboardButton("English");
    enLanguageButton.callbackData("/language en");

    var systemLanguageButton = new InlineKeyboardButton(messageSource.getMessage("language.system", null, locale));
    systemLanguageButton.callbackData("/language system");

    var sendMessage = new SendMessage(user.id(), messageSource.getMessage("language", null, locale));
    sendMessage.replyMarkup(new InlineKeyboardMarkup(systemLanguageButton, enLanguageButton, ruLanguageButton));
    sendMessage(sendMessage);
  }

  public void stopBot(User user) {
    sendMessage(new SendMessage(user.id(), messageSource.getMessage("stop", null, getLocale(user))));
  }

  public void sendLanguageConfirmation(User user) {
    sendMessage(new SendMessage(user.id(), messageSource.getMessage("change_success", null, getLocale(user))));
  }

  public void sendUpdateLocationConfirmation(User user, String zoneId) {
    var locale = getLocale(user);

    String message;
    if (StringUtils.isBlank(zoneId)) {
      message = messageSource.getMessage("start.location", List.of(zoneId).toArray(), locale);
    } else {
      message = messageSource.getMessage("start.location.error", null, locale);
    }

    var sendMessage = new SendMessage(user.id(), message);
    sendMessage.replyMarkup(new ReplyKeyboardRemove());
    sendMessage(sendMessage);
  }

  public void sendUsernameConfirmation(User user, String username) {
    var locale = getLocale(user);

    String message;
    if (StringUtils.isBlank(username)) {
      message = messageSource.getMessage("username.error", null, locale);
    } else {
      message = messageSource.getMessage("username.success", List.of(username).toArray(), locale);
    }

    SendMessage sendMessage = new SendMessage(user.id(), message);
    sendMessage(sendMessage);
  }

  public void sendReminder(Long userId, String messageCode, String matches, Locale locale) {
    if (locale == null) {
      locale = new Locale("ru");
    }

    sendMessage(new SendMessage(userId, messageSource.getMessage(messageCode, List.of(matches).toArray(), locale)));
  }

  private void sendCompetitions(User user, String key) {
    var locale = getLocale(user);
    var buttons = new ArrayList<KeyboardButton>();

    var competitions = competitionService.getCompetitions();
    competitions.forEach(competition -> {
      if (competition.isActive()) {
        var keyboardButton = new KeyboardButton(competition.getName());
        keyboardButton.webAppInfo(new WebAppInfo(buildGeneralUrl(user.id(), competition.getId(), key)));
        buttons.add(keyboardButton);
      }
    });

    String message;
    if (buttons.isEmpty()) {
      message = messageSource.getMessage("no_competitions", null, locale);
    } else {
      message = messageSource.getMessage(key, null, locale);
    }

    var buttonsArray = new KeyboardButton[buttons.size()][1];
    for (int i = 0; i < buttons.size(); i++) {
      KeyboardButton button = buttons.get(i);
      buttonsArray[i] = new KeyboardButton[] { button };
    }

    SendMessage sendMessage = new SendMessage(user.id(), message);
    sendMessage.replyMarkup(new ReplyKeyboardMarkup(buttonsArray).resizeKeyboard(true));
    sendMessage(sendMessage);
  }

  private String buildGeneralUrl(Long userId, UUID competitionId, String key) {
    return applicationUrl + "/" + telegramKey + "/users/" + userId + "/" + key + "?leagueId=" + competitionId;
  }

  private SendMessage buildGreetingMessage(User user, String username) {
    var locale = getLocale(user);

    var locationButton = new KeyboardButton(messageSource.getMessage("buttons.location", null, locale));
    locationButton.requestLocation(true);

    var message = new SendMessage(user.id(), messageSource.getMessage("start.greeting", List.of(username).toArray(), locale));
    return message.replyMarkup(new ReplyKeyboardMarkup(locationButton).resizeKeyboard(true));
  }

  private Locale getLocale(User user) {
    var userEntity = userService.getUser(user.id());
    return userEntity.getLanguage() != null ? userEntity.getLanguage() : new Locale(user.languageCode());
  }

}

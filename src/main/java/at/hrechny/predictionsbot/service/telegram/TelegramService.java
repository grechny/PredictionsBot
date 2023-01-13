package at.hrechny.predictionsbot.service.telegram;

import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.util.FileUtils;
import at.hrechny.predictionsbot.util.HashUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramException;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.WebAppInfo;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.KeyboardButton;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.model.request.ReplyKeyboardRemove;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
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

  @Value("${telegram.reportTo}")
  private String reportUserId;

  @Value("${application.url}")
  private String applicationUrl;

  private TelegramBot telegramBot;

  private final MessageSource messageSource;
  private final PredictionService predictionService;
  private final CompetitionService competitionService;
  private final UserService userService;
  private final HashUtils hashUtils;

  @PostConstruct
  public void init() {
    telegramBot = new TelegramBot(botToken);
  }

  public void setUpListener(UpdatesListener updatesListener) {
    telegramBot.setUpdatesListener(updatesListener);
  }

  public void sendMessage(Long userId, String message) {
    log.debug("Sending message to the user {}: {}", userId, message);
    sendMessage(new SendMessage(userId, message).parseMode(ParseMode.HTML));
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
    var locale = getLocale(user);
    var buttonsArray = getPredictionButtons(user.id());

    String message;
    if (buttonsArray.length == 0) {
      message = messageSource.getMessage("no_competitions", null, locale);
    } else {
      message = messageSource.getMessage("predictions", null, locale);
    }

    SendMessage sendMessage = new SendMessage(user.id(), message);
    sendMessage.replyMarkup(new ReplyKeyboardMarkup(buttonsArray).resizeKeyboard(true));
    sendMessage(sendMessage);
  }

  public void sendCompetitions(User user) {
    SendMessage sendMessage = new SendMessage(user.id(), messageSource.getMessage("results.competitions", null, getLocale(user)));
    sendMessage.replyMarkup(new InlineKeyboardMarkup(getCompetitionButtonsMatrix()));
    sendMessage(sendMessage);
  }

  public void sendCompetitions(User user, Integer messageId) {
    var editMessageText = new EditMessageText(user.id(), messageId, messageSource.getMessage("results.competitions", null, getLocale(user)));
    editMessageText.replyMarkup(new InlineKeyboardMarkup(getCompetitionButtonsMatrix()));
    editMessage(editMessageText);
  }

  public void sendSeasons(User user, UUID competitionId, Integer messageId) {
    var inlineKeyboardButtons = new ArrayList<InlineKeyboardButton>();
    competitionService.getSeasons(competitionId).forEach(season -> {
      var inlineKeyboardButton = new InlineKeyboardButton(season.getYear().toString());
      inlineKeyboardButton.callbackData("/season " + season.getId());
      inlineKeyboardButtons.add(inlineKeyboardButton);
    });

    var inlineKeyboardMatrix = splitKeyboardButtonList(inlineKeyboardButtons, 4);

    var backButton = new InlineKeyboardButton(messageSource.getMessage("results.seasons.back_button", null, getLocale(user)));
    backButton.callbackData("/results");
    inlineKeyboardMatrix.add(List.of(backButton).toArray(new InlineKeyboardButton[1]));

    var editMessageText = new EditMessageText(user.id(), messageId, messageSource.getMessage("results.seasons", null, getLocale(user)));
    editMessageText.replyMarkup(new InlineKeyboardMarkup(inlineKeyboardMatrix.toArray(new InlineKeyboardButton[0][])));
    editMessage(editMessageText);
  }

  public void sendResults(User user, UUID seasonId, Integer messageId) {
    var locale = getLocale(user);
    var inlineKeyboardMatrix = new ArrayList<InlineKeyboardButton[]>();
    var seasonEntity = competitionService.getSeason(seasonId);
    var competition = seasonEntity.getCompetition();
    var results = predictionService.getResults(seasonId);
    var maxLength = String.valueOf(results.size()).length()
        + results.stream().map(result -> result.getUser().getName()).mapToInt(String::length).max().orElse(0);

    var detailsButton = new InlineKeyboardButton(messageSource.getMessage("results.details", null, locale));
    detailsButton.webApp(new WebAppInfo(buildGeneralUrl(user.id(), competition.getId(), seasonId, "results")));
    inlineKeyboardMatrix.add(List.of(detailsButton).toArray(new InlineKeyboardButton[0]));

    var backButton = new InlineKeyboardButton(messageSource.getMessage("results.details.back_button", null, locale));
    backButton.callbackData("/seasons " + competition.getId());
    inlineKeyboardMatrix.add(List.of(backButton).toArray(new InlineKeyboardButton[1]));

    var message = new StringBuilder();
    message.append("<pre>");
    message.append(competition.getName()).append(" ").append(seasonEntity.getYear()).append('\n').append('\n');
    for (var i = 1; i <= results.size(); i++) {
      var resultEntry = results.get(i - 1);
      var nameAndOrder = i + ". " + resultEntry.getUser().getName();
      message.append(nameAndOrder);
      message.append(StringUtils.repeat(" ", maxLength + 4 - nameAndOrder.length()));
      message.append(resultEntry.getTotalSum()).append('\n');
    }
    message.append("</pre>");

    var editMessageText = new EditMessageText(user.id(), messageId, message.toString());
    editMessageText.replyMarkup(new InlineKeyboardMarkup(inlineKeyboardMatrix.toArray(new InlineKeyboardButton[0][])));
    editMessage(editMessageText.parseMode(ParseMode.HTML));
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
    if (StringUtils.isNotBlank(zoneId)) {
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

  public void pushUpdate(Long userId, String message, boolean updateCompetitionList) {
    log.info("Sending push update to user {}", userId);
    SendMessage sendMessage = new SendMessage(userId, message);
    sendMessage.parseMode(ParseMode.HTML);

    if (updateCompetitionList) {
      var buttonsArray = getPredictionButtons(userId);
      sendMessage.replyMarkup(new ReplyKeyboardMarkup(buttonsArray).resizeKeyboard(true));
    }

    sendMessage(sendMessage);
  }

  private KeyboardButton[][] getPredictionButtons(Long userId) {
    var buttons = new ArrayList<List<KeyboardButton>>();

    var competitions = competitionService.getCompetitions();
    competitions.forEach(competition -> {
      if (competition.isActive()) {
        var buttonsRow = new ArrayList<KeyboardButton>();
        var predictionsKeyboardButton = new KeyboardButton(competition.getName());
        predictionsKeyboardButton.webAppInfo(new WebAppInfo(buildGeneralUrl(userId, competition.getId(), null,"predictions")));
        buttonsRow.add(predictionsKeyboardButton);

        var resultsKeyboardButton = new KeyboardButton("\uD83C\uDFC6");
        resultsKeyboardButton.webAppInfo(new WebAppInfo(buildGeneralUrl(userId, competition.getId(), null, "results")));
        buttonsRow.add(resultsKeyboardButton);
        buttons.add(buttonsRow);
      }
    });

    var buttonsArray = new KeyboardButton[buttons.size()][2];
    for (int i = 0; i < buttons.size(); i++) {
      var button = buttons.get(i);
      buttonsArray[i] = button.toArray(new KeyboardButton[0]);
    }
    return buttonsArray;
  }

  private InlineKeyboardButton[][] getCompetitionButtonsMatrix() {
    var inlineKeyboardButtons = new ArrayList<InlineKeyboardButton>();
    competitionService.getCompetitions().forEach(competition -> {
      var inlineKeyboardButton = new InlineKeyboardButton(competition.getName());
      inlineKeyboardButton.callbackData("/seasons " + competition.getId());
      inlineKeyboardButtons.add(inlineKeyboardButton);
    });

    var inlineKeyboardMatrix = splitKeyboardButtonList(inlineKeyboardButtons, 2);
    return inlineKeyboardMatrix.toArray(new InlineKeyboardButton[0][]);
  }

  private List<InlineKeyboardButton[]> splitKeyboardButtonList(ArrayList<InlineKeyboardButton> inlineKeyboardButtons, int rowLength) {
    var inlineKeyboardMatrix = new ArrayList<InlineKeyboardButton[]>();
    for (var i = 0; i < inlineKeyboardButtons.size(); i = i + rowLength) {
      List<InlineKeyboardButton> inlineKeyboardButtonsRow;
      if (inlineKeyboardButtons.size() > i + rowLength) {
        inlineKeyboardButtonsRow = inlineKeyboardButtons.subList(i, i + rowLength);
      } else {
        inlineKeyboardButtonsRow = inlineKeyboardButtons.subList(i, inlineKeyboardButtons.size());
      }
      inlineKeyboardMatrix.add(inlineKeyboardButtonsRow.toArray(new InlineKeyboardButton[0]));
    }
    return inlineKeyboardMatrix;
  }

  private String buildGeneralUrl(Long userId, UUID competitionId, UUID seasonId, String key) {
    var url = applicationUrl + "/" + hashUtils.getHash(userId.toString()) + "/users/" + userId + "/" + key + "?leagueId=" + competitionId;
    return seasonId != null ? url + "&seasonId=" + seasonId : url;
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

  @SneakyThrows
  private void sendMessage(SendMessage message) {
    var response = telegramBot.execute(message);
    if (response.isOk()) {
      log.info("Message {} has been successfully sent", response.message().messageId());
    } else {
      log.error("Message was not send: [{}] {}", response.errorCode(), response.description());
      throw new TelegramException("Unable to send message", response);
    }
  }

  @SneakyThrows
  private void editMessage(EditMessageText editMessage) {
    var response = telegramBot.execute(editMessage);
    if (response.isOk()) {
      log.info("Message has been successfully updated");
    } else {
      log.error("Message could not be updated: [{}] {}", response.errorCode(), response.description());
      throw new TelegramException("Unable to send message", response);
    }
  }

  @SneakyThrows
  public void sendErrorReport(Exception exception) {
    if (StringUtils.isBlank(reportUserId)) {
      log.info("No user specified to send error report");
      return;
    }

    var reportUser = userService.getUser(Long.valueOf(reportUserId));
    var locale = reportUser.getLanguage() != null ? reportUser.getLanguage() : new Locale("en");

    var sendDocument = new SendDocument(reportUserId, FileUtils.buildPdfDocument(exception));
    sendDocument.fileName(exception.getClass().getSimpleName() + ".pdf");
    sendDocument.caption(messageSource.getMessage("error", null, locale) + ": " + exception.getMessage());
    var response = telegramBot.execute(sendDocument);
    if (response.isOk()) {
      log.info("Error report document {} has been successfully sent", response.message().messageId());
    } else {
      log.error("Error report document was not send: [{}] {}", response.errorCode(), response.description());
      throw new TelegramException("Unable to send error report", response);
    }
  }
}

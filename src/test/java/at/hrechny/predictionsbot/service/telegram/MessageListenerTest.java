package at.hrechny.predictionsbot.service.telegram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.service.predictor.PredictionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings({"unchecked", "deprecation"})
class MessageListenerTest {

  private static final Long USER_ID = 42L;

  @Mock
  private TelegramService telegramService;

  @Mock
  private PredictionService predictionService;

  @Mock
  private UserService userService;

  private MessageListener messageListener;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = objectMapper();
    messageListener = new MessageListener(telegramService, predictionService, userService);
    messageListener.init();
    verify(telegramService).setUpListener(messageListener);
    clearInvocations(telegramService, predictionService, userService);
  }

  @Test
  void processRoutesKnownTextCommandsToTelegramService() {
    var user = user();

    assertThat(messageListener.process(List.of(updateWithText(user, "/predictions"))))
        .isEqualTo(UpdatesListener.CONFIRMED_UPDATES_ALL);

    verify(telegramService).sendPredictions(telegramUser());
  }

  @Test
  void processStartCommandDelegatesToTelegramServiceStartBot() {
    var user = user();

    messageListener.process(List.of(updateWithText(user, "/start")));

    verify(telegramService).startBot(telegramUser());
  }

  @Test
  void processStopCommandStopsBotAndDeactivatesUser() {
    var user = user();

    messageListener.process(List.of(updateWithText(user, "/stop")));

    verify(telegramService).stopBot(telegramUser());
    verify(userService).deactivate(USER_ID);
  }

  @Test
  void processUsernameCommandNormalizesValidUsernameAndConfirmsIt() {
    var user = user();

    messageListener.process(List.of(updateWithText(user, "/username   Alice   Smith  ")));

    verify(userService).updateUsername(USER_ID, "Alice Smith");
    verify(telegramService).sendUsernameConfirmation(telegramUser(), eq("Alice Smith"));
  }

  @Test
  void processUsernameCommandConfirmsNullWhenUsernameLengthIsInvalid() {
    var user = user();

    messageListener.process(List.of(updateWithText(user, "/username ab")));

    verify(userService, never()).updateUsername(eq(USER_ID), org.mockito.ArgumentMatchers.anyString());
    verify(telegramService).sendUsernameConfirmation(telegramUser(), eq(null));
  }

  @Test
  void processLanguageCommandMapsSystemLanguageToNullAndConfirms() {
    var user = user();

    messageListener.process(List.of(updateWithText(user, "/language system")));

    verify(userService).updateLanguage(USER_ID, null);
    verify(telegramService).sendLanguageConfirmation(telegramUser());
  }

  @Test
  void processNotFoundFromCommandSendsActivateMessage() {
    var user = user();
    org.mockito.Mockito.doThrow(new NotFoundException("missing"))
        .when(telegramService).sendResults(telegramUser());

    messageListener.process(List.of(updateWithText(user, "/results")));

    verify(telegramService).sendActivateMessage(telegramUser());
    verify(telegramService, never()).sendErrorReport(org.mockito.ArgumentMatchers.any(Exception.class));
  }

  @Test
  void processReportsUnexpectedCommandExceptionAndContinues() {
    var user = user();
    var exception = new IllegalStateException("telegram failure");
    org.mockito.Mockito.doThrow(exception).when(telegramService).sendHelp(telegramUser());

    assertThat(messageListener.process(List.of(updateWithText(user, "/help"))))
        .isEqualTo(UpdatesListener.CONFIRMED_UPDATES_ALL);

    verify(telegramService).sendErrorReport(exception);
  }

  @Test
  void processWebAppDataDeserializesPredictionsAndSavesThemForUser() {
    var user = user();
    var firstMatchId = UUID.randomUUID();
    var secondMatchId = UUID.randomUUID();

    messageListener.process(List.of(updateWithWebAppData(user, """
        [
          {"matchId":"%s","predictionHome":2,"predictionAway":1,"doubleUp":true},
          {"matchId":"%s","predictionHome":0,"predictionAway":0,"doubleUp":false}
        ]
        """.formatted(firstMatchId, secondMatchId))));

    var predictionsCaptor = ArgumentCaptor.forClass(List.class);
    verify(predictionService).savePredictions(eq(USER_ID), predictionsCaptor.capture());
    assertThat(predictionsCaptor.getValue()).hasSize(2);
    assertThat(predictionsCaptor.getValue().get(0))
        .hasFieldOrPropertyWithValue("matchId", firstMatchId)
        .hasFieldOrPropertyWithValue("predictionHome", 2)
        .hasFieldOrPropertyWithValue("predictionAway", 1)
        .hasFieldOrPropertyWithValue("doubleUp", true);
    assertThat(predictionsCaptor.getValue().get(1))
        .hasFieldOrPropertyWithValue("matchId", secondMatchId)
        .hasFieldOrPropertyWithValue("predictionHome", 0)
        .hasFieldOrPropertyWithValue("predictionAway", 0)
        .hasFieldOrPropertyWithValue("doubleUp", false);
  }

  @Test
  void processCompetitionCallbackTogglesCompetitionAndRefreshesCompetitionMessage() {
    var user = user();
    var competitionId = UUID.randomUUID();
    var messageId = 123;

    messageListener.process(List.of(updateWithCallback(user, messageId, "/competition " + competitionId)));

    verify(userService).updateCompetitions(USER_ID, competitionId);
    verify(telegramService).sendCompetition(telegramUser(), eq(messageId), eq(competitionId));
  }

  @Test
  void processSeasonCallbackSendsSeasonResults() {
    var user = user();
    var seasonId = UUID.randomUUID();
    var messageId = 123;

    messageListener.process(List.of(updateWithCallback(user, messageId, "/season " + seasonId)));

    verify(telegramService).sendResults(telegramUser(), eq(seasonId), eq(messageId));
  }

  @Test
  void processNullAndUnsupportedUpdatesDoNotTriggerSideEffects() {
    messageListener.process(List.of(emptyUpdate()));

    verify(telegramService, never()).sendErrorReport(org.mockito.ArgumentMatchers.any(Exception.class));
    verify(telegramService, never()).sendHelp(org.mockito.ArgumentMatchers.any(User.class));
    verify(predictionService, never()).savePredictions(eq(USER_ID), org.mockito.ArgumentMatchers.anyList());
  }

  private Update updateWithText(User user, String text) {
    return updateFromJson("""
        {
          "update_id": 1,
          "message": {
            "message_id": 10,
            "from": {"id": %d, "language_code": "en"},
            "text": "%s"
          }
        }
        """.formatted(user.id(), text));
  }

  private Update updateWithWebAppData(User user, String data) {
    return updateFromJson("""
        {
          "update_id": 1,
          "message": {
            "message_id": 10,
            "from": {"id": %d, "language_code": "en"},
            "web_app_data": {
              "data": %s,
              "button_text": "Save"
            }
          }
        }
        """.formatted(user.id(), objectToJson(data)));
  }

  private Update updateWithCallback(User user, Integer messageId, String data) {
    return updateFromJson("""
        {
          "update_id": 1,
          "callback_query": {
            "id": "callback-1",
            "from": {"id": %d, "language_code": "en"},
            "message": {
              "message_id": %d,
              "date": 1,
              "chat": {"id": %d, "type": "Private"}
            },
            "data": "%s"
          }
        }
        """.formatted(user.id(), messageId, user.id(), data));
  }

  private Update emptyUpdate() {
    return updateFromJson("""
        {
          "update_id": 1
        }
        """);
  }

  private Update updateFromJson(String json) {
    try {
      return objectMapper.readValue(json, Update.class);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String objectToJson(String value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private User user() {
    return new User(USER_ID);
  }

  private User telegramUser() {
    return argThat(user -> user != null && USER_ID.equals(user.id()));
  }

  private ObjectMapper objectMapper() {
    var mapper = new ObjectMapper();
    mapper.setVisibility(PropertyAccessor.ALL, Visibility.NONE);
    mapper.setVisibility(PropertyAccessor.FIELD, Visibility.ANY);
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    mapper.configure(DeserializationFeature.FAIL_ON_INVALID_SUBTYPE, false);
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }
}

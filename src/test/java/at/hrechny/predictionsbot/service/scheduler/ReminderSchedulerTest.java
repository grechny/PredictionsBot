package at.hrechny.predictionsbot.service.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
class ReminderSchedulerTest {

  @Mock
  private UserService userService;

  @Mock
  private TelegramService telegramService;

  @Mock
  private CompetitionService competitionService;

  @Mock
  private MessageSource messageSource;

  private ReminderScheduler reminderScheduler;

  @BeforeEach
  void setUp() {
    reminderScheduler = new ReminderScheduler(userService, telegramService, competitionService, messageSource);
  }

  @Test
  void sendRemindersDoesNotSendMessagesWhenThereAreNoUpcomingFixtures() {
    var user = new UserEntity();
    user.setId(42L);
    when(userService.getUsers()).thenReturn(List.of(user));
    when(competitionService.getFixtures(any(Instant.class), any(Instant.class))).thenReturn(List.of());

    reminderScheduler.sendReminders();

    verifyNoInteractions(telegramService, messageSource);
  }
}

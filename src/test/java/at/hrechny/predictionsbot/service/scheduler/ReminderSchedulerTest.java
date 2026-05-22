package at.hrechny.predictionsbot.service.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.CompetitionEntity;
import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.PredictionEntity;
import at.hrechny.predictionsbot.database.entity.RoundEntity;
import at.hrechny.predictionsbot.database.entity.SeasonEntity;
import at.hrechny.predictionsbot.database.entity.TeamEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.database.model.RoundType;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ReminderSchedulerTest {

  private static final Long USER_ID = 42L;
  private static final Instant FIXED_NOW = Instant.parse("2026-05-22T20:30:00Z");

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
    reminderScheduler = new ReminderScheduler(
        userService,
        telegramService,
        competitionService,
        messageSource,
        Clock.fixed(FIXED_NOW, ZoneOffset.UTC));
  }

  @Test
  void sendRemindersDoesNotSendMessagesWhenThereAreNoUpcomingFixtures() {
    var user = user(List.of(competition("Premier League")));
    when(userService.getUsers()).thenReturn(List.of(user));
    when(competitionService.getFixtures(any(Instant.class), any(Instant.class))).thenReturn(List.of());

    reminderScheduler.sendReminders();

    verifyNoInteractions(telegramService, messageSource);
  }

  @Test
  void sendRemindersSendsTodayReminderWhenPredictionIsMissingBeforeFirstMatch() {
    var competition = competition("Premier League");
    var user = user(List.of(competition));
    var match = match(competition, "Arsenal", "Chelsea", FIXED_NOW.plus(Duration.ofMinutes(90)));
    when(userService.getUsers()).thenReturn(List.of(user));
    when(competitionService.getFixtures(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(match), List.<MatchEntity>of());
    when(messageSource.getMessage(eq("round"), isNull(), eq(Locale.ENGLISH))).thenReturn("Round");
    when(messageSource.getMessage(eq("reminders.today"), any(Object[].class), eq(Locale.ENGLISH)))
        .thenReturn("today reminder");

    reminderScheduler.sendReminders();

    verify(telegramService).sendMessage(USER_ID, "today reminder");

    var matchesArgument = ArgumentCaptor.forClass(Object[].class);
    verify(messageSource).getMessage(eq("reminders.today"), matchesArgument.capture(), eq(Locale.ENGLISH));
    assertThat(matchesArgument.getValue()).hasSize(1);
    assertThat(matchesArgument.getValue()[0].toString())
        .contains("Premier League")
        .contains("Arsenal - Chelsea")
        .contains("round1");
  }

  @Test
  void sendRemindersDoesNotSendTodayReminderWhenUserDoesNotParticipateInCompetition() {
    var user = user(List.of(competition("Bundesliga")));
    var match = match(competition("Premier League"), "Arsenal", "Chelsea", FIXED_NOW.plus(Duration.ofMinutes(90)));
    when(userService.getUsers()).thenReturn(List.of(user));
    when(competitionService.getFixtures(any(Instant.class), any(Instant.class)))
        .thenReturn(List.of(match), List.<MatchEntity>of());

    reminderScheduler.sendReminders();

    verifyNoInteractions(telegramService, messageSource);
  }

  @Test
  void sendRemindersSendsTomorrowReminderWhenPredictionIsMissingInNewTomorrowRoundAtUserDailyTime() {
    var competition = competition("Premier League");
    var user = user(List.of(competition));
    var match = match(competition, "Arsenal", "Chelsea", Instant.parse("2026-05-23T18:00:00Z"));
    when(userService.getUsers()).thenReturn(List.of(user));
    when(competitionService.getFixtures(any(Instant.class), any(Instant.class)))
        .thenReturn(List.<MatchEntity>of(), List.of(match));
    when(messageSource.getMessage(eq("round"), isNull(), eq(Locale.ENGLISH))).thenReturn("Round");
    when(messageSource.getMessage(eq("reminders.tomorrow"), any(Object[].class), eq(Locale.ENGLISH)))
        .thenReturn("tomorrow reminder");

    reminderScheduler.sendReminders();

    verify(telegramService).sendMessage(USER_ID, "tomorrow reminder");

    var matchesArgument = ArgumentCaptor.forClass(Object[].class);
    verify(messageSource).getMessage(eq("reminders.tomorrow"), matchesArgument.capture(), eq(Locale.ENGLISH));
    assertThat(matchesArgument.getValue()).hasSize(1);
    assertThat(matchesArgument.getValue()[0].toString())
        .contains("Premier League")
        .contains("Arsenal - Chelsea")
        .contains("round1");
  }

  @Test
  void sendRemindersSendsRecheckReminderWhenTomorrowPredictionIsOlderThanAWeek() {
    var competition = competition("Premier League");
    var user = user(List.of(competition));
    var match = match(competition, "Arsenal", "Chelsea", Instant.parse("2026-05-23T18:00:00Z"));
    match.getPredictions().add(prediction(user, match, FIXED_NOW.minus(Duration.ofDays(8))));
    when(userService.getUsers()).thenReturn(List.of(user));
    when(competitionService.getFixtures(any(Instant.class), any(Instant.class)))
        .thenReturn(List.<MatchEntity>of(), List.of(match));
    when(messageSource.getMessage(eq("round"), isNull(), eq(Locale.ENGLISH))).thenReturn("Round");
    when(messageSource.getMessage(eq("reminders.recheck"), any(Object[].class), eq(Locale.ENGLISH)))
        .thenReturn("recheck reminder");

    reminderScheduler.sendReminders();

    verify(telegramService).sendMessage(USER_ID, "recheck reminder");

    var matchesArgument = ArgumentCaptor.forClass(Object[].class);
    verify(messageSource).getMessage(eq("reminders.recheck"), matchesArgument.capture(), eq(Locale.ENGLISH));
    assertThat(matchesArgument.getValue()).hasSize(1);
    assertThat(matchesArgument.getValue()[0].toString())
        .contains("Premier League")
        .contains("Arsenal - Chelsea")
        .contains("round1");
  }

  private UserEntity user(List<CompetitionEntity> competitions) {
    var user = new UserEntity();
    user.setId(USER_ID);
    user.setTimezone(ZoneOffset.UTC);
    user.setInitialLanguage(Locale.ENGLISH);
    user.setCompetitions(competitions);
    return user;
  }

  private CompetitionEntity competition(String name) {
    var competition = new CompetitionEntity();
    competition.setId(UUID.randomUUID());
    competition.setName(name);
    return competition;
  }

  private MatchEntity match(CompetitionEntity competition, String homeTeam, String awayTeam, Instant startTime) {
    var season = new SeasonEntity();
    season.setId(UUID.randomUUID());
    season.setCompetition(competition);

    var round = new RoundEntity();
    round.setId(UUID.randomUUID());
    round.setSeason(season);
    round.setType(RoundType.SEASON);
    round.setOrderNumber(1);

    var match = new MatchEntity();
    match.setId(UUID.randomUUID());
    match.setRound(round);
    match.setStartTime(startTime);
    match.setHomeTeam(team(homeTeam));
    match.setAwayTeam(team(awayTeam));
    round.setMatches(List.of(match));
    return match;
  }

  private PredictionEntity prediction(UserEntity user, MatchEntity match, Instant updatedAt) {
    var prediction = new PredictionEntity();
    prediction.setId(UUID.randomUUID());
    prediction.setUser(user);
    prediction.setMatch(match);
    prediction.setUpdatedAt(updatedAt);
    return prediction;
  }

  private TeamEntity team(String name) {
    var team = new TeamEntity();
    team.setId(UUID.randomUUID());
    team.setName(name);
    return team;
  }
}

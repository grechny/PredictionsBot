package at.hrechny.predictionsbot.service.scheduler;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@EnableErrorReport
@RequiredArgsConstructor
public class ReminderScheduler {

  private final UserService userService;
  private final TelegramService telegramService;
  private final CompetitionService competitionService;
  private final MessageSource messageSource;

  @Scheduled(cron = "0 0 * * * *", zone = "UTC")
  public void sendReminders() {
    var todayFixtures = competitionService.getFixtures(
        Instant.now().truncatedTo(ChronoUnit.DAYS),
        Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS));
    var tomorrowFixtures = competitionService.getFixtures(
        Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS),
        Instant.now().plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.DAYS));
    var todayFixturesByRound = todayFixtures.stream().collect(Collectors.groupingBy(MatchEntity::getRound));
    var tomorrowNewRoundFixtures = tomorrowFixtures.stream()
        .filter(match -> todayFixturesByRound.getOrDefault(match.getRound(), Collections.emptyList()).stream()
            .noneMatch(todayMatch -> todayMatch.getRound().equals(match.getRound())))
        .toList();

    userService.getUsers().forEach(user -> {
      // send reminder if predictions are missing for the upcoming today's matches (<= 2 hours till the first one)
      var firstMatch = todayFixtures.stream().min(Comparator.comparing(MatchEntity::getStartTime));
      if (firstMatch.isPresent() // first match is between 1 and 2 hours from now
          && firstMatch.get().getStartTime().isBefore(Instant.now().plus(Duration.ofHours(2).plusSeconds(1)))
          && firstMatch.get().getStartTime().isAfter(Instant.now().plus(Duration.ofHours(1)))) {
        String upcomingMatchesString = getMissedPredictions(todayFixtures, user);
        if (StringUtils.isNotBlank(upcomingMatchesString)) {
          sendReminder(user, "reminders.today", upcomingMatchesString);
        }
      }

      // check if now from 19:59 till 20.59
      // then send daily reminder if no predictions made for the next day
      // or if some predictions were made more than week ago
      if (CollectionUtils.isNotEmpty(tomorrowNewRoundFixtures)) {
        var dailyNotificationTime = LocalTime.parse("20:00");
        var userTime = LocalTime.now(user.getTimezone());
        if (userTime.isAfter(dailyNotificationTime.minusMinutes(1)) && userTime.isBefore(dailyNotificationTime.plusMinutes(59))) {
          String missedPredictionsString = getMissedPredictions(tomorrowNewRoundFixtures, user);
          if (StringUtils.isNoneBlank(missedPredictionsString)) {
            sendReminder(user, "reminders.tomorrow", missedPredictionsString);
          }

          String oldPredictionsString = getOldPredictions(tomorrowNewRoundFixtures, user);
          if (StringUtils.isNoneBlank(oldPredictionsString)) {
            sendReminder(user, "reminders.recheck", oldPredictionsString);
          }
        }
      }
    });
  }

  @Nullable
  private String getMissedPredictions(List<MatchEntity> upcomingFixtures, UserEntity user) {
    var predictionMissedMatches = new ArrayList<MatchEntity>();

    for (var upcoming : upcomingFixtures) {
      // skip if user is not participating in the competition
      if (user.getCompetitions().stream().noneMatch(competition -> upcoming.getRound().getSeason().getCompetition().getId().equals(competition.getId()))) {
        continue;
      }

      // skip if user already made a prediction
      var predictionMissed = upcoming.getPredictions().stream().noneMatch(p -> p.getUser().equals(user));
      if (predictionMissed) {
        predictionMissedMatches.add(upcoming);
      }
    }

    return getStringOfUpcomingMatches(user, predictionMissedMatches);
  }

  @Nullable
  private String getOldPredictions(List<MatchEntity> upcomingFixtures, UserEntity user) {
    var oldPredictions = new ArrayList<MatchEntity>();
    var weekBeforeNow = Instant.now().minus(Duration.ofDays(7));

    for (var upcoming : upcomingFixtures) {
      // skip if user is not participating in the competition
      if (user.getCompetitions().stream().noneMatch(competition -> upcoming.getRound().getSeason().getCompetition().getId().equals(competition.getId()))) {
        continue;
      }

      // check if prediction made more than week ago
      var userPrediction = upcoming.getPredictions().stream().filter(p -> p.getUser().equals(user)).findAny();
      if (userPrediction.isPresent() && userPrediction.get().getUpdatedAt().isBefore(weekBeforeNow)) {
        var fixturesOfRound = upcoming.getRound().getMatches();
        var fixturesOfRoundThisWeek = fixturesOfRound.stream()
            .filter(match -> match.getStartTime() != null)
            .filter(match -> match.getStartTime().isAfter(weekBeforeNow) && match.getStartTime().isBefore(Instant.now()))
            .findAny();
        if (fixturesOfRoundThisWeek.isEmpty()) {
          oldPredictions.add(upcoming);
        }
      }
    }

    return getStringOfUpcomingMatches(user, oldPredictions);
  }

  @Nullable
  private String getStringOfUpcomingMatches(UserEntity user, List<MatchEntity> upcomingMatches) {
    if (!upcomingMatches.isEmpty()) {
      var seasonStrings = new ArrayList<String>();
      var predictionByCompetitionName = upcomingMatches.stream()
          .collect(Collectors.groupingBy(matchEntity -> matchEntity.getRound().getSeason().getCompetition().getName()));
      for (var matches : predictionByCompetitionName.entrySet()) {
        StringBuilder seasonString = new StringBuilder();
        seasonString.append("<u>").append(matches.getKey()).append("</u>").append('\n');
        for (var match : matches.getValue()) {
          var roundName = getRoundName(user, match);
          var startTime = match.getStartTime().atZone(user.getTimezone()).format(DateTimeFormatter.ofPattern("HH:mm"));
          seasonString
              .append(startTime).append(": ")
              .append(match.getHomeTeam().getName()).append(" - ").append(match.getAwayTeam().getName())
              .append(" (").append(roundName).append(")").append("\n");
        }
        seasonStrings.add(seasonString.toString());
      }
      return StringUtils.join(seasonStrings, "\n");
    }
    return null;
  }

  private void sendReminder(UserEntity user, String messageCode, String matches) {
    var message = messageSource.getMessage(messageCode, List.of(matches).toArray(), getLocale(user));
    telegramService.sendMessage(user.getId(), message);
    log.info("Reminder has been successfully sent to the user {}", user.getId());
  }

  private String getRoundName(UserEntity user, MatchEntity match) {
    var locale = getLocale(user);
    var roundEntity = match.getRound();
    return "$round".equals(roundEntity.getType().getName())
        ? messageSource.getMessage("round", null, locale).toLowerCase(locale) + roundEntity.getOrderNumber()
        : roundEntity.getType().getName();
  }

  private Locale getLocale(UserEntity user) {
    return user.getLanguage() != null ? user.getLanguage() : new Locale("ru");
  }

}

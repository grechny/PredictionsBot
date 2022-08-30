package at.hrechny.predictionsbot.service.scheduler;

import at.hrechny.predictionsbot.database.entity.MatchEntity;
import at.hrechny.predictionsbot.database.entity.UserEntity;
import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReminderScheduler {

  private final UserService userService;
  private final TelegramService telegramService;
  private final CompetitionService competitionService;

  @Scheduled(cron = "0 0 * * * *", zone = "UTC")
  public void sendReminders() {
    var upcomingFixtures = competitionService.getUpcomingFixtures();
    var upcomingFixturesToday = upcomingFixtures.stream()
        .filter(match -> match.getStartTime().isBefore(Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS)))
        .toList();
    var upcomingSeasonsToday = upcomingFixturesToday.stream().collect(Collectors.groupingBy(MatchEntity::getSeason));
    var upcomingFixturesNextDay = competitionService.getUpcomingFixtures().stream()
        .filter(match -> match.getStartTime().isAfter(Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS)))
        .filter(match -> upcomingSeasonsToday.get(match.getSeason()).stream().noneMatch(todayMatch -> todayMatch.getRound().equals(match.getRound())))
        .toList();

    userService.getUsers().forEach(user -> {
      // send reminder if predictions are missing for the upcoming today's matches (<= 2 hours till the first one)
      var firstMatch = upcomingFixturesToday.stream().min(Comparator.comparing(MatchEntity::getStartTime));
      if (firstMatch.isPresent() && Instant.now().plus(Duration.ofHours(2)).isAfter(firstMatch.get().getStartTime())) {
        String upcomingMatchesString = getMissedPredictions(upcomingFixturesToday, user);
        if (StringUtils.isNoneBlank(upcomingMatchesString)) {
          telegramService.sendReminder(user.getId(), "reminders.today", upcomingMatchesString, user.getLanguage());
        }
      }

      // check if now from 19:59 till 20.59
      // then send daily reminder if no predictions made for the next day
      // or if some predictions were made more than week ago
      if (CollectionUtils.isNotEmpty(upcomingFixturesNextDay)) {
        var dailyNotificationTime = LocalTime.parse("20:00");
        var userTime = LocalTime.now(user.getTimezone());
        if (userTime.isAfter(dailyNotificationTime.minusMinutes(1)) && userTime.isBefore(dailyNotificationTime.plusMinutes(59))) {
          String missedPredictionsString = getMissedPredictions(upcomingFixturesNextDay, user);
          if (StringUtils.isNoneBlank(missedPredictionsString)) {
            telegramService.sendReminder(user.getId(), "reminders.tomorrow", missedPredictionsString, user.getLanguage());
          }

          String oldPredictionsString = getOldPredictions(upcomingFixturesNextDay, user);
          if (StringUtils.isNoneBlank(oldPredictionsString)) {
            telegramService.sendReminder(user.getId(), "reminders.recheck", oldPredictionsString, user.getLanguage());
          }
        }
      }
    });
  }

  @Nullable
  private String getMissedPredictions(List<MatchEntity> upcomingFixtures, UserEntity user) {
    var predictionMissedMatches = new ArrayList<MatchEntity>();

    for (var upcoming : upcomingFixtures) {
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
    var _7daysBeforeNow = Instant.now().minus(Duration.ofDays(7));

    for (var upcoming : upcomingFixtures) {
      var userPrediction = upcoming.getPredictions().stream().filter(p -> p.getUser().equals(user)).findAny();
      if (userPrediction.isPresent() && userPrediction.get().getUpdatedAt().isBefore(_7daysBeforeNow)) {
        var fixturesOfRound = competitionService.getFixtures(upcoming.getSeason().getCompetition().getId(), upcoming.getRound());
        var fixturesOfRoundThisWeek = fixturesOfRound.stream()
            .filter(match -> match.getStartTime().isAfter(_7daysBeforeNow) && match.getStartTime().isBefore(Instant.now()))
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
    if (upcomingMatches.size() > 0) {
      var seasonStrings = new ArrayList<String>();
      var predictionBySeason = upcomingMatches.stream().collect(Collectors.groupingBy(MatchEntity::getSeason));
      for (var seasonMatches : predictionBySeason.entrySet()) {
        StringBuilder seasonString = new StringBuilder();
        var season = seasonMatches.getKey();
        var matches = seasonMatches.getValue();
        seasonString.append(season.getCompetition().getName()).append('\n');
        for (var match : matches) {
          var roundName = getRoundName(match);
          var startTime = match.getStartTime().atZone(user.getTimezone()).format(DateTimeFormatter.ofPattern("dd MMMM, HH:mm"));
          seasonString
              .append(startTime).append(": ")
              .append(match.getHomeTeam()).append(" - ").append(match.getAwayTeam())
              .append("( ").append(roundName).append(" )");
        }
        seasonStrings.add(seasonString.toString());
      }
      return StringUtils.join(seasonStrings, "\n\n");
    }
    return null;
  }

  private String getRoundName(MatchEntity match) {
    var roundEntity = match.getSeason().getApiFootballRounds().stream()
        .filter(round -> match.getRound().equals(round.getOrderNumber()))
        .findFirst().orElseThrow(() -> new NotFoundException("Round " + match.getRound() + "not found for the match " + match.getId()));
    return StringUtils.isNotBlank(roundEntity.getRoundName()) ? roundEntity.getRoundName() : "Round " + roundEntity.getOrderNumber();
  }

}

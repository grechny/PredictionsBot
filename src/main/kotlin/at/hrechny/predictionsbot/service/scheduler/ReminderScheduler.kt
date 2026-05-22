package at.hrechny.predictionsbot.service.scheduler

import at.hrechny.predictionsbot.config.MessageResolver
import at.hrechny.predictionsbot.database.entity.MatchEntity
import at.hrechny.predictionsbot.database.entity.UserEntity
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport
import at.hrechny.predictionsbot.service.predictor.CompetitionService
import at.hrechny.predictionsbot.service.predictor.UserService
import at.hrechny.predictionsbot.service.telegram.TelegramService
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import jakarta.transaction.Transactional
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.apache.commons.collections4.CollectionUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

@Singleton
@EnableErrorReport
open class ReminderScheduler(
    private val userService: UserService,
    private val telegramService: TelegramService,
    private val competitionService: CompetitionService,
    private val messageResolver: MessageResolver,
    private val clock: Clock,
) {
    @Transactional
    @Scheduled(cron = "0 0 * * * *", zoneId = "UTC")
    open fun sendReminders() {
        val now = Instant.now(clock)
        val todayFixtures = competitionService.getFixtures(
            now.truncatedTo(ChronoUnit.DAYS),
            now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS),
        )
        val tomorrowFixtures = competitionService.getFixtures(
            now.plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.DAYS),
            now.plus(Duration.ofDays(2)).truncatedTo(ChronoUnit.DAYS),
        )
        val todayFixturesByRound = todayFixtures.groupBy(MatchEntity::round)
        val tomorrowNewRoundFixtures = tomorrowFixtures
            .filter { match ->
                todayFixturesByRound.getOrDefault(match.round, emptyList())
                    .none { todayMatch -> todayMatch.round == match.round }
            }

        userService.getUsers().forEach { user ->
            val firstMatch = todayFixtures.minByOrNull { match -> match.startTime!! }
            if (firstMatch != null &&
                firstMatch.startTime!!.isBefore(now.plus(Duration.ofHours(2).plusSeconds(1))) &&
                firstMatch.startTime!!.isAfter(now.plus(Duration.ofHours(1)))
            ) {
                val upcomingMatchesString = getMissedPredictions(todayFixtures, user)
                if (StringUtils.isNotBlank(upcomingMatchesString)) {
                    sendReminder(user, "reminders.today", upcomingMatchesString!!)
                }
            }

            if (CollectionUtils.isNotEmpty(tomorrowNewRoundFixtures)) {
                val dailyNotificationTime = LocalTime.parse("20:00")
                val userTime = LocalTime.now(clock.withZone(user.timezone))
                if (userTime.isAfter(dailyNotificationTime.minusMinutes(1)) &&
                    userTime.isBefore(dailyNotificationTime.plusMinutes(59))
                ) {
                    val missedPredictionsString = getMissedPredictions(tomorrowNewRoundFixtures, user)
                    if (StringUtils.isNoneBlank(missedPredictionsString)) {
                        sendReminder(user, "reminders.tomorrow", missedPredictionsString!!)
                    }

                    val oldPredictionsString = getOldPredictions(tomorrowNewRoundFixtures, user, now)
                    if (StringUtils.isNoneBlank(oldPredictionsString)) {
                        sendReminder(user, "reminders.recheck", oldPredictionsString!!)
                    }
                }
            }
        }
    }

    private fun getMissedPredictions(upcomingFixtures: List<MatchEntity>, user: UserEntity): String? {
        val predictionMissedMatches = ArrayList<MatchEntity>()
        for (upcoming in upcomingFixtures) {
            if (user.competitions.none { competition -> upcoming.round!!.season!!.competition!!.id == competition.id }) {
                continue
            }

            val predictionMissed = upcoming.predictions.none { prediction -> prediction.user == user }
            if (predictionMissed) {
                predictionMissedMatches.add(upcoming)
            }
        }
        return getStringOfUpcomingMatches(user, predictionMissedMatches)
    }

    private fun getOldPredictions(upcomingFixtures: List<MatchEntity>, user: UserEntity, now: Instant): String? {
        val oldPredictions = ArrayList<MatchEntity>()
        val weekBeforeNow = now.minus(Duration.ofDays(7))

        for (upcoming in upcomingFixtures) {
            if (user.competitions.none { competition -> upcoming.round!!.season!!.competition!!.id == competition.id }) {
                continue
            }

            val userPrediction = upcoming.predictions.firstOrNull { prediction -> prediction.user == user }
            if (userPrediction != null && userPrediction.updatedAt!!.isBefore(weekBeforeNow)) {
                val fixturesOfRoundThisWeek = upcoming.round!!.matches
                    .filter { match -> match.startTime != null }
                    .firstOrNull { match -> match.startTime!!.isAfter(weekBeforeNow) && match.startTime!!.isBefore(now) }
                if (fixturesOfRoundThisWeek == null) {
                    oldPredictions.add(upcoming)
                }
            }
        }

        return getStringOfUpcomingMatches(user, oldPredictions)
    }

    private fun getStringOfUpcomingMatches(user: UserEntity, upcomingMatches: List<MatchEntity>): String? {
        if (upcomingMatches.isEmpty()) {
            return null
        }

        val seasonStrings = ArrayList<String>()
        val predictionByCompetitionName = upcomingMatches.groupBy { matchEntity ->
            matchEntity.round!!.season!!.competition!!.name
        }
        for (matches in predictionByCompetitionName.entries) {
            val seasonString = StringBuilder()
            seasonString.append("<u>").append(matches.key).append("</u>").append('\n')
            for (match in matches.value) {
                val roundName = getRoundName(user, match)
                val startTime = match.startTime!!.atZone(user.timezone).format(DateTimeFormatter.ofPattern("HH:mm"))
                seasonString
                    .append(startTime).append(": ")
                    .append(match.homeTeam!!.name).append(" - ").append(match.awayTeam!!.name)
                    .append(" (").append(roundName).append(")").append("\n")
            }
            seasonStrings.add(seasonString.toString())
        }
        return StringUtils.join(seasonStrings, "\n")
    }

    private fun sendReminder(user: UserEntity, messageCode: String, matches: String) {
        val message = messageResolver.getMessage(messageCode, arrayOf(matches), getLocale(user))
        telegramService.sendMessage(user.id, message)
        log.info("Reminder has been successfully sent to the user {}", user.id)
    }

    private fun getRoundName(user: UserEntity, match: MatchEntity): String {
        val locale = getLocale(user)
        val roundEntity = match.round!!
        return if ("\$round" == roundEntity.type!!.getName()) {
            messageResolver.getMessage("round", null, locale).lowercase(locale) + roundEntity.orderNumber
        } else {
            roundEntity.type!!.getName()!!
        }
    }

    private fun getLocale(user: UserEntity): Locale = user.language ?: user.initialLanguage!!

    private companion object {
        val log = LoggerFactory.getLogger(ReminderScheduler::class.java)
    }
}

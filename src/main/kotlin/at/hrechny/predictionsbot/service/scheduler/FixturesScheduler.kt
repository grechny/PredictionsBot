package at.hrechny.predictionsbot.service.scheduler

import at.hrechny.predictionsbot.database.entity.SeasonEntity
import at.hrechny.predictionsbot.exception.FixturesSynchronizationException
import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport
import at.hrechny.predictionsbot.service.predictor.CompetitionService
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import org.slf4j.LoggerFactory

@Singleton
@EnableErrorReport
open class FixturesScheduler(
    private val competitionService: CompetitionService,
    @param:Value("\${schedulers.fixtures.enabled:true}")
    private val enabled: Boolean,
) {
    @Scheduled(cron = "\${schedulers.fixtures.cron:0 0 0 * * *}", zoneId = "UTC")
    open fun refreshFixtures() {
        if (!enabled) {
            log.info("Scheduled fixture refresh is disabled")
            return
        }

        log.info("Executing scheduled job for refreshing fixtures data")
        val activeSeasons = competitionService.getActiveSeasons()
        val failures = ArrayList<Pair<SeasonEntity, RuntimeException>>()
        activeSeasons.forEach { season ->
            try {
                competitionService.refreshFixtures(season)
            } catch (exception: RuntimeException) {
                log.error("Scheduled fixture refresh failed for season {}", describeSeason(season), exception)
                failures.add(season to exception)
            }
        }

        if (failures.isNotEmpty()) {
            throw createSynchronizationException(activeSeasons.size, failures)
        }
    }

    private fun createSynchronizationException(
        totalSeasons: Int,
        failures: List<Pair<SeasonEntity, RuntimeException>>,
    ): FixturesSynchronizationException {
        val failedSeasons = failures.joinToString { (season, _) -> describeSeason(season) }
        val reportException = FixturesSynchronizationException(
            "Failed to refresh fixtures for ${failures.size}/$totalSeasons active seasons: $failedSeasons",
        )
        failures.forEach { (_, exception) -> reportException.addSuppressed(exception) }
        return reportException
    }

    private fun describeSeason(season: SeasonEntity): String =
        "${season.competition?.name ?: "unknown competition"} ${season.year ?: "unknown year"} (${season.id})"

    private companion object {
        val log = LoggerFactory.getLogger(FixturesScheduler::class.java)
    }
}

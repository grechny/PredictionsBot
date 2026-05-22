package at.hrechny.predictionsbot.service.scheduler

import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport
import at.hrechny.predictionsbot.service.predictor.CompetitionService
import io.micronaut.scheduling.annotation.Scheduled
import jakarta.inject.Singleton
import io.micronaut.transaction.annotation.Transactional
import org.slf4j.LoggerFactory

@Singleton
@EnableErrorReport
open class FixturesScheduler(
    private val competitionService: CompetitionService,
) {
    @Scheduled(cron = "0 0 0 * * *", zoneId = "UTC")
    @Transactional
    open fun refreshFixtures() {
        log.info("Executing scheduled job for refreshing fixtures data")
        val activeSeasons = competitionService.getActiveSeasons()
        activeSeasons.forEach(competitionService::refreshFixtures)
    }

    private companion object {
        val log = LoggerFactory.getLogger(FixturesScheduler::class.java)
    }
}

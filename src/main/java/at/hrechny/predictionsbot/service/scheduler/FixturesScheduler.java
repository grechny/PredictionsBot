package at.hrechny.predictionsbot.service.scheduler;

import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import lombok.extern.slf4j.Slf4j;
import io.micronaut.scheduling.annotation.Scheduled;
import jakarta.inject.Singleton;
import jakarta.transaction.Transactional;

@Slf4j
@Singleton
@EnableErrorReport
public class FixturesScheduler {

  private final CompetitionService competitionService;

  public FixturesScheduler(CompetitionService competitionService) {
    this.competitionService = competitionService;
  }

  @Scheduled(cron = "0 0 0 * * *", zoneId = "UTC")
  @Transactional
  public void refreshFixtures() {
    log.info("Executing scheduled job for refreshing fixtures data");
    var activeSeasons = competitionService.getActiveSeasons();
    activeSeasons.forEach(competitionService::refreshFixtures);
  }

}

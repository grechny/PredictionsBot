package at.hrechny.predictionsbot.service.scheduler;

import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@EnableErrorReport
@RequiredArgsConstructor
public class FixturesScheduler {

  private final CompetitionService competitionService;

  @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
  @Transactional
  public void refreshFixtures() {
    log.info("Executing scheduled job for refreshing fixtures data");
    var activeSeasons = competitionService.getActiveSeasons();
    activeSeasons.forEach(competitionService::refreshFixtures);
  }

}

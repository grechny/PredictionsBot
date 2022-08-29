package at.hrechny.predictionsbot.service.scheduler;

import at.hrechny.predictionsbot.service.predictor.CompetitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FixturesScheduler {

  private final CompetitionService competitionService;

  @Scheduled(cron = "0 0 8 * * *", zone = "UTC")
  public void refreshFixtures() {
    log.info("Start refreshing fixtures data for the all active competitions");
    competitionService.refreshFixtures();
  }

}

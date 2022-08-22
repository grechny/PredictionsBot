package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.utils.FileUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class TelegramBotController {

  @GetMapping(value = "/${secrets.telegramKey}/league/{leagueId}/predictions", produces = MediaType.TEXT_HTML_VALUE)
  public ResponseEntity<String> getPredictions(@PathVariable("leagueId") Long leagueId) {
    return ResponseEntity.ok(FileUtils.getResourceFileAsString("html/testWebApp.html"));
  }

}

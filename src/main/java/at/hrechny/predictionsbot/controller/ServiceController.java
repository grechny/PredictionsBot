package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
import at.hrechny.predictionsbot.model.PushUpdate;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnableErrorReport
@RequiredArgsConstructor
public class ServiceController {

  private final UserService userService;
  private final TelegramService telegramService;

  @PostMapping(value = "/${secrets.adminKey}", consumes = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> pushUpdate(@Valid @RequestBody PushUpdate pushUpdate) {
    userService.getUsers().forEach(user -> telegramService.pushUpdate(user.getId(), pushUpdate.getMessage(), pushUpdate.isUpdateCompetitionList()));
    return ResponseEntity.ok().build();
  }

}

package at.hrechny.predictionsbot.controller;

import at.hrechny.predictionsbot.exception.interceptor.EnableErrorReport;
import at.hrechny.predictionsbot.model.PushUpdate;
import at.hrechny.predictionsbot.service.predictor.UserService;
import at.hrechny.predictionsbot.service.telegram.TelegramService;
import jakarta.validation.Valid;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;

@Controller
@EnableErrorReport
public class ServiceController {

  private final UserService userService;
  private final TelegramService telegramService;

  public ServiceController(UserService userService, TelegramService telegramService) {
    this.userService = userService;
    this.telegramService = telegramService;
  }

  @Post(value = "/${secrets.adminKey}", consumes = MediaType.APPLICATION_JSON)
  public HttpResponse<Void> pushUpdate(@Valid @Body PushUpdate pushUpdate) {
    userService.getUsers().forEach(user -> telegramService.pushUpdate(user.getId(), pushUpdate.getMessage(), pushUpdate.isUpdateCompetitionList()));
    return HttpResponse.ok();
  }

}

package at.hrechny.predictionsbot.exception.interceptor;

import at.hrechny.predictionsbot.service.telegram.TelegramService;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Service;

@Aspect
@Service
@RequiredArgsConstructor
public class ExceptionInterceptor {

  private final TelegramService telegramService;

  @AfterThrowing(pointcut = "@within(EnableErrorReport) || @annotation(EnableErrorReport)", throwing = "exception")
  public void errorInterceptor(Exception exception) {
    telegramService.sendErrorReport(exception);
  }
}

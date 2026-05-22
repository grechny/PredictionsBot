package at.hrechny.predictionsbot.exception.interceptor;

import at.hrechny.predictionsbot.service.telegram.TelegramService;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import jakarta.inject.Singleton;

@Singleton
public class ExceptionInterceptor implements MethodInterceptor<Object, Object> {

  private final TelegramService telegramService;

  public ExceptionInterceptor(TelegramService telegramService) {
    this.telegramService = telegramService;
  }

  @Override
  public Object intercept(MethodInvocationContext<Object, Object> context) {
    try {
      return context.proceed();
    } catch (RuntimeException exception) {
      telegramService.sendErrorReport(exception);
      throw exception;
    }
  }
}

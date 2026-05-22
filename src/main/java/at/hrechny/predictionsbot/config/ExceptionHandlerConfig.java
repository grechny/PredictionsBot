package at.hrechny.predictionsbot.config;

import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.exception.RequestValidationException;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ExceptionHandlerConfig implements ExceptionHandler<Exception, HttpResponse<Object>> {

  @Override
  public HttpResponse<Object> handle(io.micronaut.http.HttpRequest request, Exception ex) {
    if (ex instanceof HttpStatusException httpStatusException) {
      log.error("Request failed: ", ex);
      return HttpResponse.status(httpStatusException.getStatus());
    }
    if (isBadRequest(ex)) {
      log.error("Bad request: ", ex);
      return HttpResponse.status(HttpStatus.BAD_REQUEST);
    }
    log.error("An unhandled exception was caught: ", ex);
    return HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR);
  }

  private boolean isBadRequest(Exception ex) {
    return ex instanceof ConversionErrorException
        || ex instanceof ConstraintViolationException
        || ex instanceof org.hibernate.exception.ConstraintViolationException
        || ex instanceof RequestValidationException
        || ex instanceof NotFoundException;
  }

}

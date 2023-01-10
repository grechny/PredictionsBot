package at.hrechny.predictionsbot.config;

import at.hrechny.predictionsbot.exception.NotFoundException;
import at.hrechny.predictionsbot.exception.RequestValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class ExceptionHandlerConfig {

  @ExceptionHandler({
      HttpRequestMethodNotSupportedException.class,
      HttpMediaTypeNotSupportedException.class,
      HttpMediaTypeNotAcceptableException.class,
      MissingPathVariableException.class,
      MissingServletRequestParameterException.class,
      ServletRequestBindingException.class,
      ConversionNotSupportedException.class,
      TypeMismatchException.class,
      HttpMessageNotReadableException.class,
      HttpMessageNotWritableException.class,
      MethodArgumentNotValidException.class,
      jakarta.validation.ConstraintViolationException.class,
      org.hibernate.exception.ConstraintViolationException.class,
      RequestValidationException.class,
      NotFoundException.class
  })
  public ResponseEntity<Object> handleBadRequestException(Exception ex) {
    log.error("Bad request: ", ex);
    return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
  }

  @ExceptionHandler(value = {Exception.class})
  public ResponseEntity<Object> handleAnyException(Exception ex) {
    log.error("An unhandled exception was caught: ", ex);
    return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
  }

}
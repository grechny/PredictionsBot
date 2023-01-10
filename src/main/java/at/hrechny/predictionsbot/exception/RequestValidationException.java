package at.hrechny.predictionsbot.exception;

public class RequestValidationException extends RuntimeException {

  public RequestValidationException(String message) {
    super(message);
  }
}

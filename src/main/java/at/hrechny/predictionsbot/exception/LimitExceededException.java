package at.hrechny.predictionsbot.exception;

public class LimitExceededException extends RuntimeException {

    public LimitExceededException(String message) {
      super(message);
    }

}

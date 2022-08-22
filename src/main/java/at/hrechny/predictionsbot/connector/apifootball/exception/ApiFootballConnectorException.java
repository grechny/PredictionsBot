package at.hrechny.predictionsbot.connector.apifootball.exception;

public class ApiFootballConnectorException extends RuntimeException {

  public enum Reason {
    TOO_OFTEN_REQUESTS,
    QUOTA_EXCEEDED,
    REQUEST_ERROR,
    INVALID_RESPONSE
  }

  public ApiFootballConnectorException(Reason reason) {
    super(reason.toString());
  }

}

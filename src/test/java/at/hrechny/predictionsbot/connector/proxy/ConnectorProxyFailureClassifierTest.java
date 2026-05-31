package at.hrechny.predictionsbot.connector.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import at.hrechny.predictionsbot.connector.impl.apifootball.ApiFootballConnector;
import at.hrechny.predictionsbot.exception.ApiConnectorException;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class ConnectorProxyFailureClassifierTest {

  private final ConnectorProxyFailureClassifier classifier = new ConnectorProxyFailureClassifier();

  @Test
  void classifiesProxyAuthenticationResponseAsProxyFailure() {
    var exception = new HttpClientResponseException(
        "Proxy authentication required",
        HttpResponse.status(HttpStatus.valueOf(407)));

    assertThat(classifier.isProxyFailure(exception)).isTrue();
  }

  @Test
  void doesNotClassifyProviderRateLimitResponseAsProxyFailure() {
    var exception = new HttpClientResponseException(
        "Too many requests",
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS));

    assertThat(classifier.isProxyFailure(exception)).isFalse();
  }

  @Test
  void classifiesTransportCauseChainAsProxyFailure() {
    assertThat(classifier.isProxyFailure(new RuntimeException(new ConnectException("Connection refused")))).isTrue();
    assertThat(classifier.isProxyFailure(new RuntimeException(new UnknownHostException("bad proxy")))).isTrue();
    assertThat(classifier.isProxyFailure(new RuntimeException(new SocketTimeoutException("connect timed out")))).isTrue();
    assertThat(classifier.isProxyFailure(new RuntimeException(new SocketException("Connection reset")))).isTrue();
  }

  @Test
  void doesNotClassifyApiConnectorExceptionsAsProxyFailure() {
    assertThat(classifier.isProxyFailure(new ApiConnectorException(
        ApiFootballConnector.NAME,
        ApiConnectorException.Reason.QUOTA_EXCEEDED,
        "quota",
        null))).isFalse();
    assertThat(classifier.isProxyFailure(new ApiConnectorException(
        ApiFootballConnector.NAME,
        ApiConnectorException.Reason.INVALID_RESPONSE,
        "invalid",
        null))).isFalse();
  }
}

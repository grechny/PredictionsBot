package at.hrechny.predictionsbot.connector.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException;
import at.hrechny.predictionsbot.database.entity.AuditEntity;
import at.hrechny.predictionsbot.database.repository.AuditRepository;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFootballConnectorTest {

  @Mock
  private AuditRepository auditRepository;

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void getFixturesIncludesSafeRateLimitHeadersWhenProviderReturnsNonSuccessResponse() throws IOException {
    startServerWithRateLimitResponse();
    var connector = new ApiFootballConnector(
        auditRepository,
        "http://localhost:" + server.getAddress().getPort(),
        "secret-api-key",
        0,
        "22:00",
        "",
        0,
        "",
        "");

    assertThatThrownBy(() -> connector.getFixtures(39L, "2025"))
        .isInstanceOf(ApiFootballConnectorException.class)
        .hasMessageContaining("REQUEST_ERROR")
        .hasMessageContaining("HTTP 429")
        .hasMessageContaining("x-ratelimit-requests-remaining=98")
        .hasMessageNotContaining("secret-api-key");

    var auditCaptor = ArgumentCaptor.forClass(AuditEntity.class);
    verify(auditRepository).save(auditCaptor.capture());
    assertThat(auditCaptor.getValue().isSuccess()).isFalse();
  }

  private void startServerWithRateLimitResponse() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/fixtures", exchange -> {
      var body = "{\"message\":\"too many requests\"}".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("x-ratelimit-requests-remaining", "98");
      exchange.getResponseHeaders().add("x-ratelimit-requests-limit", "100");
      exchange.sendResponseHeaders(429, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
  }
}

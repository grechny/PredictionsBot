package at.hrechny.predictionsbot.connector;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import at.hrechny.predictionsbot.service.connector.ApiConnectorAuditService;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConnectorHttpClientAuditFilterTest {

  @Mock
  private ApiConnectorAuditService auditService;

  @Test
  void recordsTaggedSuccessfulResponse() {
    var filter = new ConnectorHttpClientAuditFilter(auditService);
    var request = taggedRequest("/v3/fixtures?league=39&season=2025");

    filter.recordResponse(request, HttpResponse.ok());

    verify(auditService).recordRequest(
        "api-football",
        "/v3/fixtures?league=39&season=2025",
        true,
        null);
  }

  @Test
  void recordsTaggedNonSuccessResponse() {
    var filter = new ConnectorHttpClientAuditFilter(auditService);
    var request = taggedRequest("/v3/fixtures?league=39&season=2025");

    filter.recordResponse(request, HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS));

    verify(auditService).recordRequest(
        "api-football",
        "/v3/fixtures?league=39&season=2025",
        false,
        "HTTP 429 Too Many Requests");
  }

  @Test
  void recordsTaggedResponseExceptionWithoutParsingBody() {
    var filter = new ConnectorHttpClientAuditFilter(auditService);
    var request = taggedRequest("/v3/fixtures?league=39&season=2025");
    var exception = new HttpClientResponseException(
        "Too many requests",
        HttpResponse.status(HttpStatus.TOO_MANY_REQUESTS));

    filter.recordFailure(request, exception);

    verify(auditService).recordRequest(
        "api-football",
        "/v3/fixtures?league=39&season=2025",
        false,
        "HTTP 429 Too Many Requests");
  }

  @Test
  void recordsTaggedTransportExceptionWithoutLeakingMessage() {
    var filter = new ConnectorHttpClientAuditFilter(auditService);
    var request = taggedRequest("/v3/fixtures?league=39&season=2025");

    filter.recordFailure(request, new IllegalStateException("secret details"));

    verify(auditService).recordRequest(
        "api-football",
        "/v3/fixtures?league=39&season=2025",
        false,
        "IllegalStateException");
  }

  @Test
  void ignoresUntaggedResponses() {
    var filter = new ConnectorHttpClientAuditFilter(auditService);

    filter.recordResponse(HttpRequest.GET("/v3/fixtures"), HttpResponse.ok());
    filter.recordFailure(HttpRequest.GET("/v3/fixtures"), new IllegalStateException("failure"));

    verifyNoInteractions(auditService);
  }

  private HttpRequest<?> taggedRequest(String uri) {
    var request = HttpRequest.GET(uri);
    request.setAttribute(ApiConnectorHttpAttributes.CONNECTOR_NAME, "api-football");
    return request;
  }
}

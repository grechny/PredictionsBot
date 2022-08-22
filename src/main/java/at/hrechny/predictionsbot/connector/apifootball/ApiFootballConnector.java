package at.hrechny.predictionsbot.connector.apifootball;

import at.hrechny.predictionsbot.config.JsonBodyHandler;
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException;
import at.hrechny.predictionsbot.connector.apifootball.exception.ApiFootballConnectorException.Reason;
import at.hrechny.predictionsbot.connector.apifootball.model.ApiFootballResponse;
import at.hrechny.predictionsbot.connector.apifootball.model.FixturesResponse;
import at.hrechny.predictionsbot.connector.apifootball.model.RoundsResponse;
import at.hrechny.predictionsbot.database.entity.AuditEntity;
import at.hrechny.predictionsbot.database.model.ApiProvider;
import at.hrechny.predictionsbot.database.repository.AuditRepository;
import at.hrechny.predictionsbot.connector.apifootball.model.Fixture;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApiFootballConnector {

  private final AuditRepository auditRepository;

  @Value("${connectors.api-football.url}")
  private String baseUrl;

  @Value("${connectors.api-football.apiKey}")
  private String apiKey;

  @Value("${connectors.api-football.minInterval}")
  private int minInterval;

  @Value("${connectors.api-football.maxAttempts}")
  private int maxAttempts;

  @Value("${connectors.api-football.dayStarts}")
  private String dayStarts;

  public List<String> getRounds(Long leagueId, String seasonYear) {
    synchronized(this) {
      checkMaxAttempts();

      URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/fixtures/rounds")
          .queryParam("league", leagueId)
          .queryParam("season", seasonYear)
          .build().toUri();

      return sendRequest(uri, RoundsResponse.class).getResponse();
    }
  }

  public List<Fixture> getFixtures(Long leagueId, String seasonYear) throws ApiFootballConnectorException {
    synchronized(this) {
      checkMaxAttempts();

      URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/fixtures")
          .queryParam("league", leagueId)
          .queryParam("season", seasonYear)
          .build().toUri();

      return sendRequest(uri, FixturesResponse.class).getResponse();
    }
  }

  public List<Fixture> getFixtures(List<Long> fixtureIds) throws ApiFootballConnectorException {
    synchronized(this) {
      checkMinInterval();
      checkMaxAttempts();

      var fixtureIdsString = fixtureIds.stream().limit(20).map(Object::toString).toList();
      URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/fixtures")
          .queryParam("ids", String.join("-", fixtureIdsString))
          .build().toUri();

      return sendRequest(uri, FixturesResponse.class).getResponse();
    }
  }

  private <T, G extends ApiFootballResponse<T>> G sendRequest(URI uri, Class<G> clazz) throws ApiFootballConnectorException {
    HttpRequest request = HttpRequest.newBuilder()
        .GET()
        .uri(uri)
        .header("X-RapidAPI-Key", apiKey)
        .header("X-RapidAPI-Host", "api-football-v1.p.rapidapi.com")
        .build();

    var auditEntity = new AuditEntity();
    auditEntity.setApiKey(apiKey);
    auditEntity.setApiProvider(ApiProvider.API_FOOTBALL);
    auditEntity.setRequestUri(uri.toString());
    auditEntity.setRequestDate(Instant.now());

    G response;
    try {
      response = HttpClient.newHttpClient().send(request, new JsonBodyHandler<>(clazz)).body();
      if (response == null || CollectionUtils.isNotEmpty(response.getErrors()) || response.getResponse() == null) {
        auditEntity.setSuccess(false);
        log.error("API-Football response is invalid: {}", response);
        throw new ApiFootballConnectorException(Reason.INVALID_RESPONSE);
      } else {
        auditEntity.setSuccess(true);
      }
    } catch (Exception e) {
      auditEntity.setSuccess(false);
      log.error("Request to API-Football failed", e);
      throw new ApiFootballConnectorException(Reason.REQUEST_ERROR);
    } finally {
      auditRepository.save(auditEntity);
    }

    return response;
  }

  private void checkMinInterval() {
    if (minInterval <= 0) {
      return;
    }

    var lastCall = auditRepository.getFirstByApiProviderAndApiKeyOrderByRequestDateDesc(ApiProvider.API_FOOTBALL, apiKey);
    if (lastCall.isPresent()) {
      if (Instant.now().minusSeconds(minInterval).isBefore(lastCall.get().getRequestDate())) {
        log.warn("No minimal interval {}s reached between the calls", minInterval);
        throw new ApiFootballConnectorException(Reason.TOO_OFTEN_REQUESTS);
      }
    }
  }

  private void checkMaxAttempts() {
    if (maxAttempts <= 0) {
      return;
    }

    LocalDate billingStartDate = LocalDate.now(ZoneOffset.UTC);
    LocalTime billingStartTime = LocalTime.parse(dayStarts);
    if (LocalTime.now(ZoneOffset.UTC).isBefore(billingStartTime)) {
      billingStartDate = billingStartDate.minusDays(1);
    }
    var billingStartDateTime = LocalDateTime.of(billingStartDate, billingStartTime).toInstant(ZoneOffset.UTC);
    int count = auditRepository.countAllByApiProviderAndApiKeyAndRequestDateAfter(ApiProvider.API_FOOTBALL, apiKey, billingStartDateTime);
    if (count >= maxAttempts) {
      throw new ApiFootballConnectorException(Reason.QUOTA_EXCEEDED);
    }
  }

}

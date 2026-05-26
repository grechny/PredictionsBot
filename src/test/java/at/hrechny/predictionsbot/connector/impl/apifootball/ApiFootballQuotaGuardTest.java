package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.exception.ApiConnectorException;
import at.hrechny.predictionsbot.service.connector.ApiConnectorRequestAuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFootballQuotaGuardTest {

  @Mock
  private ApiConnectorRequestAuditService auditService;

  @Test
  void startupHydrationAtDailyLimitBlocksNextRequest() {
    var guard = guardAt("2026-05-26T21:30:00Z", 100, 100);

    assertThatThrownBy(() -> guard.checkRequestAllowed("/fixtures"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            org.assertj.core.api.Assertions.assertThat(exception.getReason())
                .isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED));

    verify(auditService).countRequestsSince("api-football", Instant.parse("2026-05-25T22:00:00Z"));
  }

  @Test
  void startupHydrationBelowDailyLimitAllowsRequest() {
    var guard = guardAt("2026-05-26T23:30:00Z", 100, 99);

    assertThatCode(() -> guard.checkRequestAllowed("/fixtures")).doesNotThrowAnyException();
    verify(auditService).countRequestsSince("api-football", Instant.parse("2026-05-26T22:00:00Z"));
  }

  @Test
  void localCountIsMoreRestrictiveThanProviderHeaders() {
    var guard = guardAt("2026-05-26T23:30:00Z", 1, 0);
    guard.checkRequestAllowed("/fixtures");
    guard.markRequestAttempted();
    guard.updateFromHeaders(Map.of("x-ratelimit-requests-remaining", "100"));

    assertThatThrownBy(() -> guard.checkRequestAllowed("/fixtures"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            org.assertj.core.api.Assertions.assertThat(exception.getReason())
                .isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED));
  }

  @Test
  void providerRemainingZeroBlocksNextRequest() {
    var guard = guardAt("2026-05-26T23:30:00Z", 100, 0);

    guard.updateFromHeaders(Map.of(
        "x-ratelimit-requests-remaining", "0",
        "x-ratelimit-requests-reset", "60"));

    assertThatThrownBy(() -> guard.checkRequestAllowed("/fixtures"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            org.assertj.core.api.Assertions.assertThat(exception.getReason())
                .isEqualTo(ApiConnectorException.Reason.QUOTA_EXCEEDED));
  }

  @Test
  void retryAfterBlocksNextRequest() {
    var guard = guardAt("2026-05-26T23:30:00Z", 100, 0);

    guard.updateFromHeaders(Map.of("retry-after", "60"));

    assertThatThrownBy(() -> guard.checkRequestAllowed("/fixtures"))
        .isInstanceOfSatisfying(ApiConnectorException.class, exception ->
            org.assertj.core.api.Assertions.assertThat(exception.getReason())
                .isEqualTo(ApiConnectorException.Reason.TOO_OFTEN_REQUESTS));
  }

  @Test
  void malformedHeadersDoNotCrashGuard() {
    var guard = guardAt("2026-05-26T23:30:00Z", 100, 0);

    assertThatCode(() -> guard.updateFromHeaders(Map.of(
        "x-ratelimit-requests-remaining", "invalid",
        "x-ratelimit-requests-reset", "invalid")))
        .doesNotThrowAnyException();
    assertThatCode(() -> guard.checkRequestAllowed("/fixtures")).doesNotThrowAnyException();
  }

  private ApiFootballQuotaGuard guardAt(String now, int maxAttempts, int hydratedCount) {
    when(auditService.countRequestsSince(eq("api-football"), any(Instant.class))).thenReturn(hydratedCount);
    return new ApiFootballQuotaGuard(
        auditService,
        Clock.fixed(Instant.parse(now), ZoneOffset.UTC),
        maxAttempts,
        "22:00");
  }
}

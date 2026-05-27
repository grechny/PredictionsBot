package at.hrechny.predictionsbot.service.connector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.ApiConnectorMappingCandidateEntity;
import at.hrechny.predictionsbot.database.model.ApiConnectorMappingCandidateStatus;
import at.hrechny.predictionsbot.database.model.ApiConnectorValueType;
import at.hrechny.predictionsbot.database.repository.ApiConnectorMappingCandidateRepository;
import at.hrechny.predictionsbot.exception.RequestValidationException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiConnectorMappingCandidateServiceTest {

  @Mock
  private ApiConnectorMappingCandidateRepository apiConnectorMappingCandidateRepository;

  private ApiConnectorMappingCandidateService apiConnectorMappingCandidateService;

  @BeforeEach
  void setUp() {
    apiConnectorMappingCandidateService = new ApiConnectorMappingCandidateService(apiConnectorMappingCandidateRepository);
  }

  @Test
  void recordCandidateCreatesPendingCandidateWhenMissing() {
    when(apiConnectorMappingCandidateRepository.findByConnectorCodeAndValueTypeAndRawValue(
        "api-football",
        ApiConnectorValueType.ROUND_LABEL,
        "Championship Round"))
        .thenReturn(Optional.empty());
    when(apiConnectorMappingCandidateRepository.save(any(ApiConnectorMappingCandidateEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, ApiConnectorMappingCandidateEntity.class));

    var candidate = apiConnectorMappingCandidateService.recordCandidate(
        "api-football",
        ApiConnectorValueType.ROUND_LABEL,
        "Championship Round",
        "{\"competition\":\"Premier League\"}",
        "SEASON",
        40,
        "normalizer");

    assertThat(candidate.getConnectorCode()).isEqualTo("api-football");
    assertThat(candidate.getValueType()).isEqualTo(ApiConnectorValueType.ROUND_LABEL);
    assertThat(candidate.getRawValue()).isEqualTo("Championship Round");
    assertThat(candidate.getContextJson()).isEqualTo("{\"competition\":\"Premier League\"}");
    assertThat(candidate.getSuggestedValue()).isEqualTo("SEASON");
    assertThat(candidate.getSuggestionConfidence()).isEqualTo(40);
    assertThat(candidate.getSuggestionSource()).isEqualTo("normalizer");
    assertThat(candidate.getStatus()).isEqualTo(ApiConnectorMappingCandidateStatus.PENDING);
    assertThat(candidate.getFirstSeenAt()).isNotNull();
    assertThat(candidate.getLastSeenAt()).isNotNull();
  }

  @Test
  void recordCandidateUpdatesLastSeenWithoutResettingDecisionStatus() {
    var existing = new ApiConnectorMappingCandidateEntity();
    existing.setConnectorCode("api-football");
    existing.setValueType(ApiConnectorValueType.ROUND_LABEL);
    existing.setRawValue("Championship Round");
    existing.setStatus(ApiConnectorMappingCandidateStatus.REJECTED);
    existing.setFirstSeenAt(Instant.parse("2026-05-26T10:00:00Z"));
    existing.setLastSeenAt(Instant.parse("2026-05-26T10:00:00Z"));

    when(apiConnectorMappingCandidateRepository.findByConnectorCodeAndValueTypeAndRawValue(
        "api-football",
        ApiConnectorValueType.ROUND_LABEL,
        "Championship Round"))
        .thenReturn(Optional.of(existing));
    when(apiConnectorMappingCandidateRepository.save(any(ApiConnectorMappingCandidateEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, ApiConnectorMappingCandidateEntity.class));

    var candidate = apiConnectorMappingCandidateService.recordCandidate(
        "api-football",
        ApiConnectorValueType.ROUND_LABEL,
        "Championship Round");

    assertThat(candidate.getStatus()).isEqualTo(ApiConnectorMappingCandidateStatus.REJECTED);
    assertThat(candidate.getFirstSeenAt()).isEqualTo(Instant.parse("2026-05-26T10:00:00Z"));
    assertThat(candidate.getLastSeenAt()).isAfter(candidate.getFirstSeenAt());
  }

  @Test
  void findApprovedValueReturnsApprovedSuggestedValueOnly() {
    var candidate = new ApiConnectorMappingCandidateEntity();
    candidate.setStatus(ApiConnectorMappingCandidateStatus.APPROVED);
    candidate.setSuggestedValue("ROUND_OF_16,ROUND_OF_16_RETURN");

    when(apiConnectorMappingCandidateRepository.findByConnectorCodeAndValueTypeAndRawValue(
        "api-football",
        ApiConnectorValueType.ROUND_LABEL,
        "8th Final Round"))
        .thenReturn(Optional.of(candidate));

    var approvedValue = apiConnectorMappingCandidateService.findApprovedValue(
        "api-football",
        ApiConnectorValueType.ROUND_LABEL,
        "8th Final Round");

    assertThat(approvedValue).contains("ROUND_OF_16,ROUND_OF_16_RETURN");
  }

  @Test
  void decideCandidateApprovesCandidateWithSuggestedValue() {
    var candidateId = UUID.randomUUID();
    var candidate = new ApiConnectorMappingCandidateEntity();
    candidate.setId(candidateId);
    candidate.setStatus(ApiConnectorMappingCandidateStatus.PENDING);

    when(apiConnectorMappingCandidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));
    when(apiConnectorMappingCandidateRepository.save(any(ApiConnectorMappingCandidateEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, ApiConnectorMappingCandidateEntity.class));

    var decided = apiConnectorMappingCandidateService.decideCandidate(
        candidateId,
        ApiConnectorMappingCandidateStatus.APPROVED,
        "ROUND_OF_16",
        "admin");

    assertThat(decided.getStatus()).isEqualTo(ApiConnectorMappingCandidateStatus.APPROVED);
    assertThat(decided.getSuggestedValue()).isEqualTo("ROUND_OF_16");
    assertThat(decided.getDecidedBy()).isEqualTo("admin");
    assertThat(decided.getDecidedAt()).isNotNull();
  }

  @Test
  void decideCandidateRejectsApprovalWithoutSuggestedValue() {
    var candidateId = UUID.randomUUID();
    var candidate = new ApiConnectorMappingCandidateEntity();
    candidate.setId(candidateId);
    candidate.setStatus(ApiConnectorMappingCandidateStatus.PENDING);

    when(apiConnectorMappingCandidateRepository.findById(candidateId)).thenReturn(Optional.of(candidate));

    assertThatThrownBy(() -> apiConnectorMappingCandidateService.decideCandidate(
        candidateId,
        ApiConnectorMappingCandidateStatus.APPROVED,
        null,
        "admin"))
        .isInstanceOf(RequestValidationException.class);
  }
}

package at.hrechny.predictionsbot.service.predictor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.database.entity.ApiConnectorIdEntity;
import at.hrechny.predictionsbot.database.model.ApiConnectorEntityType;
import at.hrechny.predictionsbot.database.repository.ApiConnectorIdRepository;
import at.hrechny.predictionsbot.exception.NotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiConnectorServiceTest {

  @Mock
  private ApiConnectorIdRepository apiConnectorIdRepository;

  private ApiConnectorService apiConnectorService;

  @BeforeEach
  void setUp() {
    apiConnectorService = new ApiConnectorService(apiConnectorIdRepository);
  }

  @Test
  void upsertIdCreatesNewMappingWhenMissing() {
    var internalId = UUID.randomUUID();
    when(apiConnectorIdRepository.findByConnectorCodeAndEntityTypeAndConnectorEntityId(
        "api-football",
        ApiConnectorEntityType.MATCH,
        "100"))
        .thenReturn(Optional.empty());
    when(apiConnectorIdRepository.save(any(ApiConnectorIdEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0, ApiConnectorIdEntity.class));

    var saved = apiConnectorService.upsertId("api-football", ApiConnectorEntityType.MATCH, "100", internalId);

    assertThat(saved.getConnectorCode()).isEqualTo("api-football");
    assertThat(saved.getEntityType()).isEqualTo(ApiConnectorEntityType.MATCH);
    assertThat(saved.getConnectorEntityId()).isEqualTo("100");
    assertThat(saved.getInternalId()).isEqualTo(internalId);
    assertThat(saved.getCreatedAt()).isNotNull();
    assertThat(saved.getUpdatedAt()).isNotNull();
  }

  @Test
  void requireConnectorEntityIdReturnsReverseMapping() {
    var internalId = UUID.randomUUID();
    var mapping = mapping("api-football", ApiConnectorEntityType.COMPETITION, "39", internalId);
    when(apiConnectorIdRepository.findAllByConnectorCodeAndEntityTypeAndInternalId(
        "api-football",
        ApiConnectorEntityType.COMPETITION,
        internalId))
        .thenReturn(List.of(mapping));

    assertThat(apiConnectorService.requireConnectorEntityId(
        "api-football",
        ApiConnectorEntityType.COMPETITION,
        internalId))
        .isEqualTo("39");
  }

  @Test
  void requireConnectorEntityIdFailsWhenMappingIsMissing() {
    var internalId = UUID.randomUUID();
    when(apiConnectorIdRepository.findAllByConnectorCodeAndEntityTypeAndInternalId(
        "api-football",
        ApiConnectorEntityType.MATCH,
        internalId))
        .thenReturn(List.of());

    assertThatThrownBy(() -> apiConnectorService.requireConnectorEntityId(
        "api-football",
        ApiConnectorEntityType.MATCH,
        internalId))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("api-football")
        .hasMessageContaining(internalId.toString());
  }

  @Test
  void requireConnectorEntityIdFailsWhenReverseMappingIsAmbiguous() {
    var internalId = UUID.randomUUID();
    when(apiConnectorIdRepository.findAllByConnectorCodeAndEntityTypeAndInternalId(
        "api-football",
        ApiConnectorEntityType.TEAM,
        internalId))
        .thenReturn(List.of(
            mapping("api-football", ApiConnectorEntityType.TEAM, "1", internalId),
            mapping("api-football", ApiConnectorEntityType.TEAM, "old-1", internalId)));

    assertThatThrownBy(() -> apiConnectorService.requireConnectorEntityId(
        "api-football",
        ApiConnectorEntityType.TEAM,
        internalId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Multiple connector api-football ids")
        .hasMessageContaining(internalId.toString());
  }

  @Test
  void findConnectorIdMappingsAllowsMultipleMappingsForOneConnector() {
    var internalId = UUID.randomUUID();
    when(apiConnectorIdRepository.findAllByInternalIdAndEntityType(internalId, ApiConnectorEntityType.COMPETITION))
        .thenReturn(List.of(
            mapping("api-football", ApiConnectorEntityType.COMPETITION, "39", internalId),
            mapping("api-football", ApiConnectorEntityType.COMPETITION, "premier-league", internalId),
            mapping("mock", ApiConnectorEntityType.COMPETITION, "premier-league", internalId)));

    assertThat(apiConnectorService.findConnectorIdMappings(internalId, ApiConnectorEntityType.COMPETITION))
        .extracting(ApiConnectorIdEntity::getConnectorCode, ApiConnectorIdEntity::getConnectorEntityId)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("api-football", "39"),
            org.assertj.core.groups.Tuple.tuple("api-football", "premier-league"),
            org.assertj.core.groups.Tuple.tuple("mock", "premier-league"));

    verify(apiConnectorIdRepository).findAllByInternalIdAndEntityType(internalId, ApiConnectorEntityType.COMPETITION);
    verifyNoMoreInteractions(apiConnectorIdRepository);
  }

  private ApiConnectorIdEntity mapping(
      String connectorCode,
      ApiConnectorEntityType entityType,
      String connectorEntityId,
      UUID internalId) {
    var mapping = new ApiConnectorIdEntity();
    mapping.setConnectorCode(connectorCode);
    mapping.setEntityType(entityType);
    mapping.setConnectorEntityId(connectorEntityId);
    mapping.setInternalId(internalId);
    return mapping;
  }
}

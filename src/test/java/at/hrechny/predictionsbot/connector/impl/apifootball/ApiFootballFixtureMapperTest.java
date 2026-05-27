package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import at.hrechny.predictionsbot.connector.model.RoundSyncType;
import at.hrechny.predictionsbot.database.model.ApiConnectorValueType;
import at.hrechny.predictionsbot.service.connector.ApiConnectorMappingCandidateService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApiFootballFixtureMapperTest {

  @Mock
  private ApiConnectorMappingCandidateService apiConnectorMappingCandidateService;

  private ApiFootballFixtureMapper mapper;

  @BeforeEach
  void setUp() {
    lenient().when(apiConnectorMappingCandidateService.findApprovedValue(
        anyString(),
        any(ApiConnectorValueType.class),
        anyString()))
        .thenReturn(Optional.empty());
    mapper = new ApiFootballFixtureMapper(apiConnectorMappingCandidateService);
  }

  @Test
  void mapsApiFootballRoundLabelsToCanonicalRoundTypes() {
    assertThat(mapper.toRoundSyncDto("Knockout Round Play-offs").getTypes())
        .containsExactly(RoundSyncType.ROUND_OF_32);
    assertThat(mapper.toRoundSyncDto("Play-offs").getTypes())
        .containsExactly(RoundSyncType.QUALIFYING);
    assertThat(mapper.toRoundSyncDto("Championship Play-offs").getTypes())
        .containsExactly(RoundSyncType.QUALIFYING);
    assertThat(mapper.toRoundSyncDto("Promotion Play-offs").getTypes())
        .containsExactly(RoundSyncType.QUALIFYING);
    assertThat(mapper.toRoundSyncDto("Round of 16").getTypes())
        .containsExactly(RoundSyncType.ROUND_OF_16, RoundSyncType.ROUND_OF_16_RETURN);
    verifyNoInteractions(apiConnectorMappingCandidateService);
  }

  @Test
  void extractsOrderNumberForOrderedApiFootballRounds() {
    var round = mapper.toRoundSyncDto("Regular Season - 2");

    assertThat(round.getTypes()).isEqualTo(List.of(RoundSyncType.SEASON));
    assertThat(round.getOrderNumber()).isEqualTo(2);
    verifyNoInteractions(apiConnectorMappingCandidateService);
  }

  @Test
  void rejectsUnknownApiFootballRoundLabels() {
    assertThatThrownBy(() -> mapper.toRoundSyncDto("Unknown Provider Round"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported API-Football round: Unknown Provider Round");
    verify(apiConnectorMappingCandidateService).recordCandidate(
        ApiFootballConnector.NAME,
        ApiConnectorValueType.ROUND_LABEL,
        "Unknown Provider Round",
        null,
        null,
        null,
        null);
  }

  @Test
  void mapsApprovedUnknownApiFootballRoundCandidate() {
    when(apiConnectorMappingCandidateService.findApprovedValue(
        ApiFootballConnector.NAME,
        ApiConnectorValueType.ROUND_LABEL,
        "Provider Eighth Final"))
        .thenReturn(Optional.of("ROUND_OF_16,ROUND_OF_16_RETURN"));

    var round = mapper.toRoundSyncDto("Provider Eighth Final");

    assertThat(round.getTypes()).containsExactly(RoundSyncType.ROUND_OF_16, RoundSyncType.ROUND_OF_16_RETURN);
  }
}

package at.hrechny.predictionsbot.connector.impl.apifootball;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import at.hrechny.predictionsbot.connector.model.RoundSyncType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiFootballFixtureMapperTest {

  private ApiFootballFixtureMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ApiFootballFixtureMapper();
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
  }

  @Test
  void extractsOrderNumberForOrderedApiFootballRounds() {
    var round = mapper.toRoundSyncDto("Regular Season - 2");

    assertThat(round.getTypes()).isEqualTo(List.of(RoundSyncType.SEASON));
    assertThat(round.getOrderNumber()).isEqualTo(2);
  }

  @Test
  void rejectsUnknownApiFootballRoundLabels() {
    assertThatThrownBy(() -> mapper.toRoundSyncDto("Unknown Provider Round"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported API-Football round: Unknown Provider Round");
  }
}

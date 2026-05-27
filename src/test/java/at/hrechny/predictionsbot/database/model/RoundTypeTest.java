package at.hrechny.predictionsbot.database.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RoundTypeTest {

  @Test
  void testPlayoffs() {
    // Test that "Knockout Round Play-offs" is matched as ROUND_OF_32
    List<RoundType> knockoutPlayOffs = RoundType.getByAlias("Knockout Round Play-offs");
    assertEquals(1, knockoutPlayOffs.size(), "Should return exactly one round type");
    assertEquals(RoundType.ROUND_OF_32, knockoutPlayOffs.get(0), "Should match as ROUND_OF_32");

    // Test that other variations of Play-offs are matched as QUALIFYING
    List<RoundType> playOffs = RoundType.getByAlias("Play-offs");
    assertTrue(playOffs.contains(RoundType.QUALIFYING), "Should match as QUALIFYING");
    
    playOffs = RoundType.getByAlias("Championship Play-offs");
    assertTrue(playOffs.contains(RoundType.QUALIFYING), "Should match as QUALIFYING");
    
    playOffs = RoundType.getByAlias("Promotion Play-offs");
    assertTrue(playOffs.contains(RoundType.QUALIFYING), "Should match as QUALIFYING");
  }
}
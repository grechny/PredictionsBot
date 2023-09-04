package at.hrechny.predictionsbot.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NameUtilsTest {

  @Test
  void formatNameTest() {
    assertEquals("Лёха", NameUtils.formatName("Лёха"));
    assertEquals("(°෴°)ノ", NameUtils.formatName("(°෴°)ノ"));
  }
}
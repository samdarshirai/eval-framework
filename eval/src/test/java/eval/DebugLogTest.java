package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.Test;

class DebugLogTest {
  @Test
  void offLogsNothingAndOnPrefixesEveryLine() {
    DebugLog.OFF.log("ignored");
    var buf = new ByteArrayOutputStream();
    new DebugLog(new PrintStream(buf)).log("hello");
    assertTrue(buf.toString().startsWith("[debug] "), buf.toString());
    assertTrue(buf.toString().endsWith(" hello" + System.lineSeparator()), buf.toString());
  }

  @Test
  void clipMakesOneLineOfAtMost200CharactersPlusAnEllipsis() {
    assertEquals("a b c", DebugLog.clip("a\n  b\tc "));
    assertEquals("null", DebugLog.clip(null));
    String clipped = DebugLog.clip("x".repeat(500));
    assertEquals(201, clipped.length());
    assertTrue(clipped.endsWith("…"));
  }
}

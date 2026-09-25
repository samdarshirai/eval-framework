package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StampCaseHashesTest {
  private static final String TWO_CASES =
      "# a comment that must survive\n"
          + "- id: c1\n"
          + "  question: \"Q one?\"\n"
          + "  added: \"2026-09-25\"\n"
          + "\n"
          + "- id: oos\n"
          + "  question: \"Q two?\"\n"
          + "  added: \"2026-09-25\"\n";

  @Test
  void addsTheHashAtTheEndOfTheCaseAndLeavesTheOtherCasesAlone() {
    String stamped = StampCaseHashes.stamp(TWO_CASES, Map.of("c1", "a1b2c3d4e5f6"));
    assertEquals(
        "# a comment that must survive\n"
            + "- id: c1\n"
            + "  question: \"Q one?\"\n"
            + "  added: \"2026-09-25\"\n"
            + "  confirmed_hash: \"a1b2c3d4e5f6\"\n"
            + "\n"
            + "- id: oos\n"
            + "  question: \"Q two?\"\n"
            + "  added: \"2026-09-25\"\n",
        stamped);
  }

  @Test
  void replacesAnOldHashInsteadOfAddingASecondOne() {
    String once = StampCaseHashes.stamp(TWO_CASES, Map.of("c1", "111111111111"));
    String twice = StampCaseHashes.stamp(once, Map.of("c1", "222222222222"));
    assertEquals(1, twice.split("confirmed_hash", -1).length - 1);
    assertTrue(twice.contains("confirmed_hash: \"222222222222\""), twice);
  }

  @Test
  void stampingTwiceWithTheSameHashChangesNothing() {
    String once = StampCaseHashes.stamp(TWO_CASES, Map.of("c1", "a1b2c3d4e5f6"));
    assertEquals(once, StampCaseHashes.stamp(once, Map.of("c1", "a1b2c3d4e5f6")));
  }

  @Test
  void stampsEveryListedCaseInOneFile() {
    String stamped =
        StampCaseHashes.stamp(TWO_CASES, Map.of("c1", "aaaaaaaaaaaa", "oos", "bbbbbbbbbbbb"));
    assertTrue(stamped.contains("confirmed_hash: \"aaaaaaaaaaaa\""), stamped);
    assertTrue(stamped.contains("confirmed_hash: \"bbbbbbbbbbbb\""), stamped);
  }

  @Test
  void aQuotedIdIsMatched() {
    String stamped =
        StampCaseHashes.stamp("- id: \"c1\"\n  added: \"x\"\n", Map.of("c1", "a1b2c3d4e5f6"));
    assertTrue(stamped.contains("confirmed_hash: \"a1b2c3d4e5f6\""), stamped);
  }
}

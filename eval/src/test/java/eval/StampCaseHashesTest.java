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
  void aCaseWithTheIdNotFirstNeverReceivesAnotherCasesHash() {
    String yaml = "- id: c1\n  added: \"x\"\n- question: Q2\n  id: c2\n  added: \"y\"\n";
    assertEquals(
        "- id: c1\n  added: \"x\"\n  confirmed_hash: \"aaaaaaaaaaaa\"\n"
            + "- question: Q2\n  id: c2\n  added: \"y\"\n",
        StampCaseHashes.stamp(yaml, Map.of("c1", "aaaaaaaaaaaa")));
  }

  @Test
  void findsCaseIdsOnlyOnIdLinesAndIgnoresATrailingComment() {
    String yaml = "- id: c1  # note\n  a: b\n- {id: c2, q: x}\n- id: \"c3\"\n";
    assertEquals(java.util.Set.of("c1", "c3"), StampCaseHashes.caseIdsIn(yaml));
  }

  @Test
  void refusesToStampWhenAHashedCaseHasNoIdLineToStampUnder() {
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                StampCaseHashes.requireEveryIdFound(
                    java.util.Set.of("c1", "c2"), java.util.Set.of("c1")));
    assertTrue(error.getMessage().contains("c2"), error.getMessage());
    StampCaseHashes.requireEveryIdFound(java.util.Set.of("c1"), java.util.Set.of("c1", "c9"));
  }

  @Test
  void aQuotedIdIsMatched() {
    String stamped =
        StampCaseHashes.stamp("- id: \"c1\"\n  added: \"x\"\n", Map.of("c1", "a1b2c3d4e5f6"));
    assertTrue(stamped.contains("confirmed_hash: \"a1b2c3d4e5f6\""), stamped);
  }
}

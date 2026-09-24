package eval.calibration;

import static org.junit.jupiter.api.Assertions.*;

import eval.Judge;
import java.io.*;
import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TrapPairsTest {
  @TempDir Path dir;

  private Path file() throws IOException {
    return Files.writeString(
        dir.resolve("t.yaml"),
        """
- {name: negated, fact: "Safari 14 or later is supported", claim: "All Safari versions except 14", agree: false}
- {name: paraphrase, fact: "Safari 14 or later is supported", claim: "Safari 14 and newer work", agree: true}
- name: covering
  fact: "The split is always even"
  claims:
    - {text: "The split is not even", states: false}
    - {text: "Traffic is split evenly", states: true}
""");
  }

  private static boolean isCoveringCall(String system) {
    return system.contains("JSON array");
  }

  private static long misses(List<TrapPairs.TrapResult> results) {
    return results.stream().filter(result -> !result.passed()).count();
  }

  @Test
  void countsAJudgeThatAgreesWithEverythingAsMissesOnBothKinds() throws Exception {
    var judge = new Judge((system, user) -> isCoveringCall(system) ? "[1, 2]" : "YES");
    var results = TrapPairs.run(file(), judge);
    assertEquals(2, misses(results));
    assertEquals("negated", results.get(0).name());
    assertFalse(results.get(0).passed());
    assertEquals("covering", results.get(2).name());
    assertFalse(results.get(2).passed());
  }

  @Test
  void aJudgeThatIsRightOnAllHasNoMisses() throws Exception {
    var judge =
        new Judge(
            (system, user) ->
                isCoveringCall(system) ? "[2]" : (user.contains("except") ? "NO" : "YES"));
    assertEquals(0, misses(TrapPairs.run(file(), judge)));
  }

  @Test
  void unusableJudgeReplyIsAMissNotACrashAndTheRunContinues() throws Exception {
    var results = TrapPairs.run(file(), new Judge((system, user) -> "the first one"));
    assertEquals(3, results.size());
    assertEquals(3, misses(results));
    assertTrue(results.get(0).detail().startsWith("error:"), results.get(0).detail());
  }

  @Test
  void theShippedTrapFileHasFiveAgreePairsAndTwoCoveringPairs() throws Exception {
    // A judge that says YES to everything misses the 3 agree=false traps and both covering pairs
    // (5), and passes the 2 correct paraphrases.
    var results =
        TrapPairs.runBundled(
            new Judge((system, user) -> isCoveringCall(system) ? "[1, 2]" : "YES"));
    assertEquals(7, results.size());
    assertEquals(5, misses(results));
  }
}

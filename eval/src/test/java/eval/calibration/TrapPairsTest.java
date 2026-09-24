package eval.calibration;

import static org.junit.jupiter.api.Assertions.*;

import eval.Judge;
import java.io.*;
import java.nio.file.*;
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

  @Test
  void countsAJudgeThatAgreesWithEverythingAsMissesOnBothKinds() throws Exception {
    var out = new ByteArrayOutputStream();
    var judge = new Judge((s, u) -> isCoveringCall(s) ? "[1, 2]" : "YES");
    assertEquals(2, TrapPairs.run(file(), judge, new PrintStream(out)));
    assertTrue(out.toString().contains("MISS negated"), out.toString());
    assertTrue(out.toString().contains("MISS covering"), out.toString());
  }

  @Test
  void aJudgeThatIsRightOnAllHasNoMisses() throws Exception {
    var judge =
        new Judge((s, u) -> isCoveringCall(s) ? "[2]" : (u.contains("except") ? "NO" : "YES"));
    assertEquals(0, TrapPairs.run(file(), judge, new PrintStream(new ByteArrayOutputStream())));
  }

  @Test
  void unusableJudgeReplyIsAMissNotACrashAndTheRunContinues() throws Exception {
    var out = new ByteArrayOutputStream();
    assertEquals(
        3, TrapPairs.run(file(), new Judge((s, u) -> "the first one"), new PrintStream(out)));
    assertTrue(out.toString().contains("(error:"), out.toString());
    assertTrue(out.toString().contains("3 miss(es) out of 3"), out.toString());
  }

  @Test
  void theShippedTrapFileHasFiveAgreePairsAndTwoCoveringPairs() throws Exception {
    var out = new ByteArrayOutputStream();
    // A judge that agrees with everything and returns every claim number misses all 3 agree traps
    // and both covering traps.
    TrapPairs.run(
        Path.of("calibration/trap-pairs.yaml"),
        new Judge((s, u) -> isCoveringCall(s) ? "[1, 2]" : "YES"),
        new PrintStream(out));
    assertEquals(
        7,
        out.toString().lines().filter(l -> l.startsWith("ok ") || l.startsWith("MISS ")).count());
    assertEquals(5, out.toString().lines().filter(l -> l.startsWith("MISS ")).count());
  }
}

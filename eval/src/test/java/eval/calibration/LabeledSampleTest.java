package eval.calibration;

import static org.junit.jupiter.api.Assertions.*;

import eval.Judge;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabeledSampleTest {
  private static final String SAMPLE =
      """
- {name: wrong, kind: unsupported, supported: false, passage: "Safari 14", claim: "Safari 13"}
- {name: right, kind: supported, supported: true, passage: "Safari 14", claim: "Safari 14"}
""";

  private static LabeledSample.Result run(String reply) {
    return LabeledSample.run(new StringReader(SAMPLE), new Judge((system, user) -> reply));
  }

  @Test
  void aJudgeThatMatchesTheLabelsAgreesOnEverything() {
    var judge = new Judge((system, user) -> user.contains("Claim: Safari 14") ? "YES" : "NO");
    var result = LabeledSample.run(new StringReader(SAMPLE), judge);
    assertEquals(2, result.agreed());
    assertEquals(0, result.falseSupported());
    assertTrue(result.agreementMet());
  }

  @Test
  void yesToEverythingIsAFalseSupportedOnTheUnsupportedPair() {
    var result = run("YES");
    assertEquals(1, result.falseSupported());
    assertEquals(0, result.falseUnsupported());
    assertEquals(0.5, result.agreement(), 1e-9);
    assertFalse(result.agreementMet());
    assertEquals("labeled UNSUPPORTED, judge said SUPPORTED", result.pairs().get(0).detail());
  }

  @Test
  void noToEverythingIsAFalseUnsupportedAndNotAFalseSupported() {
    var result = run("NO");
    assertEquals(0, result.falseSupported());
    assertEquals(1, result.falseUnsupported());
  }

  @Test
  void aJudgeErrorNeverCountsAsAgreeingNotEvenOnAnUnsupportedPair() {
    var result = run("Maybe");
    assertEquals(0, result.agreed());
    assertEquals(0, result.falseSupported());
    assertNull(result.pairs().get(0).actualSupported());
    assertTrue(result.pairs().get(0).detail().startsWith("error:"), result.pairs().get(0).detail());
  }

  @Test
  void errorsCountThePairsTheJudgeGaveNoUsableAnswerOn() {
    assertEquals(2, run("Maybe").errors());
    assertEquals(0, run("YES").errors());
  }

  @Test
  void ninetyPercentAgreementIsTheLine() {
    assertTrue(withAgreed(18, 20).agreementMet());
    assertFalse(withAgreed(17, 20).agreementMet());
  }

  @Test
  void anEmptySampleHasNoTargetToMiss() {
    assertTrue(
        LabeledSample.run(new StringReader("[]\n"), new Judge((s, u) -> "YES")).agreementMet());
    assertTrue(LabeledSample.Result.NONE.agreementMet());
    assertEquals(0, LabeledSample.Result.NONE.falseSupported());
  }

  @Test
  void aMapWithPassagesAndAliasesLoadsAndRuns() {
    String yaml =
        """
passages:
  safari: &safari "Safari 14"
pairs:
  - {name: wrong, kind: unsupported, supported: false, passage: *safari, claim: "Safari 13"}
  - {name: right, kind: supported, supported: true, passage: *safari, claim: "Safari 14"}
""";
    var judge = new Judge((system, user) -> user.contains("Claim: Safari 14") ? "YES" : "NO");
    var result = LabeledSample.run(new StringReader(yaml), judge);
    assertEquals(2, result.pairs().size());
    assertEquals(2, result.agreed());
  }

  private static LabeledSample.Result withAgreed(int agreed, int total) {
    List<LabeledSample.PairResult> pairs = new ArrayList<>();
    for (int index = 0; index < total; index++) {
      pairs.add(new LabeledSample.PairResult("p" + index, "supported", true, index < agreed, null));
    }
    return new LabeledSample.Result(pairs);
  }
}

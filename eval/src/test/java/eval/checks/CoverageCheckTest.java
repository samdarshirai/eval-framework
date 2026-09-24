package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class CoverageCheckTest {
  private static final ExpectedFact SAFARI =
      new ExpectedFact(
          "Safari 14 or later is supported",
          List.of("browser-support#browser-support"),
          List.of("Safari", "14"));

  private static EvalCase caseWith(ExpectedFact... facts) {
    return new EvalCase(
        "c", "q", "single-source", null, "answer", List.of(facts), null, null, null);
  }

  private static Claim claim(String text) {
    return new Claim(text, List.of("browser-support#browser-support"));
  }

  /**
   * Fake judge LLM: answers YES when the claim in the prompt contains the marker, and counts calls.
   */
  private static final class FakeLlm implements llm.Llm {
    int calls;
    final String yesWhenClaimContains;

    FakeLlm(String marker) {
      this.yesWhenClaimContains = marker;
    }

    @Override
    public String complete(String system, String user) {
      calls++;
      return user.substring(user.indexOf("Claim:")).contains(yesWhenClaimContains) ? "YES" : "NO";
    }
  }

  @Test
  void noClaimWithAllKeywordsMeansNotCoveredAndZeroJudgeCalls() {
    var llm = new FakeLlm("anything");
    var result =
        new CoverageCheck(new Judge(llm))
            .run(
                caseWith(SAFARI),
                new Answer(false, List.of(claim("Safari 13 is supported"))),
                new CaseState());
    assertFalse(result.passed());
    assertTrue(result.reason().contains("Safari 14 or later is supported"), result.reason());
    assertEquals(0, llm.calls);
  }

  @Test
  void keywordsMatchCaseInsensitively() {
    var llm = new FakeLlm("later");
    var result =
        new CoverageCheck(new Judge(llm))
            .run(
                caseWith(SAFARI),
                new Answer(false, List.of(claim("SAFARI 14 and later work"))),
                new CaseState());
    assertTrue(result.passed(), result.reason());
    assertEquals(1, llm.calls);
  }

  @Test
  void keywordHitAloneNeverPassesTheJudgeRejectsNegation() {
    var llm = new FakeLlm("NEVER-MATCHES");
    var result =
        new CoverageCheck(new Judge(llm))
            .run(
                caseWith(SAFARI),
                new Answer(false, List.of(claim("All Safari versions except 14 are supported"))),
                new CaseState());
    assertFalse(result.passed());
    assertEquals(1, llm.calls);
  }

  @Test
  void confirmedCandidatesAreRecordedAsCoveringClaims() {
    var good = claim("Safari 14 or later is supported");
    var bad = claim("Safari 14 is not supported on iOS");
    var other = claim("Unrelated statement");
    var state = new CaseState();
    var result =
        new CoverageCheck(new Judge(new FakeLlm("or later")))
            .run(caseWith(SAFARI), new Answer(false, List.of(bad, good, other)), state);
    assertTrue(result.passed(), result.reason());
    assertEquals(List.of(good), state.covering(SAFARI));
  }

  @Test
  void keywordSubstringStillOnlyMakesACandidateTheJudgeDecides() {
    var llm = new FakeLlm("NEVER-MATCHES");
    var result =
        new CoverageCheck(new Judge(llm))
            .run(
                caseWith(SAFARI),
                new Answer(false, List.of(claim("Safari 141 is supported"))),
                new CaseState());
    assertFalse(result.passed());
    assertEquals(1, llm.calls);
  }

  @Test
  void refusedAnswerFailsCoverageWithoutAnyJudgeCall() {
    var llm = new FakeLlm("x");
    var result =
        new CoverageCheck(new Judge(llm))
            .run(caseWith(SAFARI), new Answer(true, List.of()), new CaseState());
    assertFalse(result.passed());
    assertEquals(0, llm.calls);
  }

  @Test
  void nullClaimsListIsTreatedAsNoClaims() {
    var llm = new FakeLlm("x");
    var result =
        new CoverageCheck(new Judge(llm))
            .run(caseWith(SAFARI), new Answer(false, null), new CaseState());
    assertFalse(result.passed());
    assertEquals(0, llm.calls);
  }

  @Test
  void caseWithNoFactsPasses() {
    assertTrue(
        new CoverageCheck(
                new Judge(
                    (system, user) -> {
                      throw new AssertionError("no call expected");
                    }))
            .run(
                new EvalCase(
                    "c", "q", "out-of-scope", "unrelated", "refuse", List.of(), null, null, null),
                new Answer(true, List.of()),
                new CaseState())
            .passed());
  }

  @Test
  void everyUncoveredFactIsListed() {
    var f2 =
        new ExpectedFact(
            "Chrome 80 is supported",
            List.of("browser-support#browser-support"),
            List.of("Chrome", "80"));
    var result =
        new CoverageCheck(new Judge(new FakeLlm("x")))
            .run(
                caseWith(SAFARI, f2),
                new Answer(false, List.of(claim("nothing relevant"))),
                new CaseState());
    assertTrue(
        result.reason().contains("Safari 14 or later") && result.reason().contains("Chrome 80"),
        result.reason());
  }

  @Test
  void coverageIsThirdAndGatingInTheRegistrationList() {
    var third =
        Checks.registered(new KnowledgeBase(List.of()), new Judge((system, user) -> "YES")).get(2);
    assertEquals("Coverage", third.check().name());
    assertTrue(third.gating());
  }

  private static final ExpectedFact NO_KEYWORDS =
      new ExpectedFact(
          "The A/B test split is not always even", List.of("ab-test#ab-test"), List.of());

  @Test
  void factWithoutKeywordsMakesOneJudgeCallAndUsesTheReturnedIndices() {
    var calls = new int[1];
    var first = claim("Traffic can be split unevenly");
    var second = claim("Something else");
    var state = new CaseState();
    var check =
        new CoverageCheck(
            new Judge(
                (system, user) -> {
                  calls[0]++;
                  return "[1]";
                }));
    var result = check.run(caseWith(NO_KEYWORDS), new Answer(false, List.of(first, second)), state);
    assertTrue(result.passed(), result.reason());
    assertEquals(1, calls[0]);
    assertEquals(List.of(first), state.covering(NO_KEYWORDS));
  }

  @Test
  void emptyIndexListMeansNotCovered() {
    var result =
        new CoverageCheck(new Judge((system, user) -> "[]"))
            .run(caseWith(NO_KEYWORDS), new Answer(false, List.of(claim("x"))), new CaseState());
    assertFalse(result.passed());
    assertTrue(result.reason().contains("The A/B test split is not always even"), result.reason());
  }

  @Test
  void keywordlessFactWithNoClaimsMakesNoJudgeCall() {
    var result =
        new CoverageCheck(
                new Judge(
                    (system, user) -> {
                      throw new AssertionError("no call expected");
                    }))
            .run(caseWith(NO_KEYWORDS), new Answer(true, List.of()), new CaseState());
    assertFalse(result.passed());
  }

  @Test
  void unusableJudgeReplyThrowsSoTheHarnessReportsACheckError() {
    var check = new CoverageCheck(new Judge((system, user) -> "the first one"));
    assertThrows(
        IllegalStateException.class,
        () ->
            check.run(
                caseWith(NO_KEYWORDS), new Answer(false, List.of(claim("x"))), new CaseState()));
  }
}

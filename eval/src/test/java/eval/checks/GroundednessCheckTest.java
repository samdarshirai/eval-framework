package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.*;
import kb.Chunk;
import org.junit.jupiter.api.Test;

class GroundednessCheckTest {
  private static final KnowledgeBase KB =
      new KnowledgeBase(
          List.of(
              new Chunk("d#safari", "d", "Safari 14 or later is supported."),
              new Chunk("d#chrome", "d", "Chrome 69 or later is supported.")));
  private static final ExpectedFact SAFARI =
      new ExpectedFact("Safari 14 or later is supported", List.of("d#safari"), List.of("Safari"));

  private static EvalCase caseWith(ExpectedFact... facts) {
    return new EvalCase(
        "c", "q", "single-source", null, "answer", List.of(facts), null, null, null);
  }

  /** Fake judge LLM: YES when the passage in the prompt contains the marker; counts calls. */
  private static final class FakeLlm implements llm.Llm {
    int calls;
    final String yesWhenPassageContains;

    FakeLlm(String marker) {
      this.yesWhenPassageContains = marker;
    }

    @Override
    public String complete(String system, String user) {
      calls++;
      String passage = user.substring(0, user.indexOf("Claim:"));
      return passage.contains(yesWhenPassageContains) ? "YES" : "NO";
    }
  }

  private static CheckResult run(FakeLlm llm, CaseState state, Claim... claims) {
    return new GroundednessCheck(KB, new Judge(llm))
        .run(caseWith(SAFARI), new Answer(false, List.of(claims)), state);
  }

  @Test
  void coveringClaimCitingAGoldChunkPassesWithZeroJudgeCalls() {
    var llm = new FakeLlm("never");
    var claim = new Claim("Safari 14 works", List.of("d#safari"));
    var state = new CaseState();
    state.setCovering(SAFARI, List.of(claim));
    var result = run(llm, state, claim);
    assertTrue(result.passed(), result.reason());
    assertTrue(result.reason().contains("grounded (gold chunk)"), result.reason());
    assertEquals(0, llm.calls);
  }

  @Test
  void coveringClaimCitingARealChunkOutsideGoldGoesToTheJudgeAndPassesWhenSupported() {
    var llm = new FakeLlm("Chrome");
    var claim = new Claim("Chrome 69 works", List.of("d#chrome"));
    var state = new CaseState();
    state.setCovering(SAFARI, List.of(claim));
    var result = run(llm, state, claim);
    assertTrue(result.passed(), result.reason());
    assertTrue(result.reason().contains("grounded (judge)"), result.reason());
    assertEquals(1, llm.calls);
  }

  @Test
  void unsupportedClaimFailsAndShowsTheClaimAndTheChunk() {
    var llm = new FakeLlm("Safari"); // the cited chunk is the Chrome one, so NO
    var claim = new Claim("Safari 14 works", List.of("d#chrome"));
    var state = new CaseState();
    state.setCovering(SAFARI, List.of(claim));
    var result = run(llm, state, claim);
    assertFalse(result.passed());
    assertTrue(result.reason().contains("Safari 14 works"), result.reason());
    assertTrue(result.reason().contains("d#chrome"), result.reason());
    assertTrue(result.reason().contains("Chrome 69 or later"), result.reason());
  }

  @Test
  void anExtraClaimThatCoversNoFactIsJudgedToo() {
    var llm = new FakeLlm("Chrome 99");
    var covering = new Claim("Safari 14 works", List.of("d#safari"));
    var extra = new Claim("Chrome 99 works", List.of("d#chrome"));
    var state = new CaseState();
    state.setCovering(SAFARI, List.of(covering));
    var result = run(llm, state, covering, extra);
    assertFalse(result.passed());
    assertTrue(result.reason().contains("Chrome 99 works"), result.reason());
    assertEquals(1, llm.calls);
  }

  @Test
  void oneSupportingChunkAmongSeveralCitedIsEnough() {
    var llm = new FakeLlm("Safari");
    var claim = new Claim("Safari 14 works", List.of("d#chrome", "d#safari"));
    var result = run(llm, new CaseState(), claim); // covers no fact, so no gold shortcut
    assertTrue(result.passed(), result.reason());
    assertEquals(2, llm.calls);
  }

  @Test
  void claimsWithoutAnyExistingCitationAreSkippedWithNoJudgeCall() {
    var llm = new FakeLlm("never");
    var result =
        run(
            llm,
            new CaseState(),
            new Claim("fabricated", List.of("d#missing")),
            new Claim("empty", List.of()),
            new Claim("null", null));
    assertTrue(result.passed(), result.reason());
    assertTrue(result.reason().contains("skipped"), result.reason());
    assertEquals(0, llm.calls);
  }

  @Test
  void noClaimsAndNullClaimsPass() {
    var llm = new FakeLlm("never");
    var check = new GroundednessCheck(KB, new Judge(llm));
    assertTrue(check.run(caseWith(SAFARI), new Answer(true, List.of()), new CaseState()).passed());
    assertTrue(check.run(caseWith(SAFARI), new Answer(false, null), new CaseState()).passed());
    assertEquals(0, llm.calls);
  }

  @Test
  void anUnclearJudgeReplyThrowsInsteadOfPassing() {
    var check = new GroundednessCheck(KB, new Judge((system, user) -> "Maybe"));
    var answer = new Answer(false, List.of(new Claim("x", List.of("d#chrome"))));
    assertThrows(
        IllegalStateException.class,
        () -> check.run(caseWith(SAFARI), answer, new CaseState()));
  }
}

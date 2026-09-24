package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class RefusalCheckTest {
  private final RefusalCheck check = new RefusalCheck();
  private static final CaseState STATE = new CaseState();

  private EvalCase caseExpecting(String behavior) {
    return new EvalCase("c", "q", "x", null, behavior, List.of(), "authored", "o", "2026-09-24");
  }

  private static final Claim CLAIM = new Claim("c", List.of("d#a"));

  @Test
  void expectedRefuseAndRefusedPasses() {
    assertTrue(check.run(caseExpecting("refuse"), new Answer(true, List.of()), STATE).passed());
  }

  @Test
  void expectedAnswerAndAnsweredPasses() {
    assertTrue(
        check.run(caseExpecting("answer"), new Answer(false, List.of(CLAIM)), STATE).passed());
  }

  @Test
  void confidentAnswerToOutOfScopeIsHallucination() {
    var result = check.run(caseExpecting("refuse"), new Answer(false, List.of(CLAIM)), STATE);
    assertFalse(result.passed());
    assertTrue(result.reason().startsWith("hallucination"));
  }

  @Test
  void refusingACoveredQuestionIsOverRefusal() {
    var result = check.run(caseExpecting("answer"), new Answer(true, List.of()), STATE);
    assertFalse(result.passed());
    assertTrue(result.reason().startsWith("over-refusal"));
  }

  @Test
  void refusedWithClaimsIsContractViolation() {
    var result = check.run(caseExpecting("refuse"), new Answer(true, List.of(CLAIM)), STATE);
    assertFalse(result.passed());
    assertTrue(result.reason().startsWith("contract violation"));
  }

  @Test
  void refusalIsFirstAndGatingInTheRegistrationList() {
    var first = Checks.registered(new KnowledgeBase(List.of()), null).get(0);
    assertEquals("Refusal", first.check().name());
    assertTrue(first.gating());
  }

  @Test
  void citationIntegrityIsSecondAndGating() {
    var second = Checks.registered(new KnowledgeBase(List.of()), null).get(1);
    assertEquals("Citation integrity", second.check().name());
    assertTrue(second.gating());
  }
}

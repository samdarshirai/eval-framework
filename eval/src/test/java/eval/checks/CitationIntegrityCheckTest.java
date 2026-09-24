package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.List;
import kb.Chunk;
import org.junit.jupiter.api.Test;

class CitationIntegrityCheckTest {
  private final KnowledgeBase kb =
      new KnowledgeBase(List.of(new Chunk("d#a", "d", "text"), new Chunk("d#b", "d", "text")));
  private final CitationIntegrityCheck check = new CitationIntegrityCheck(kb);
  private static final EvalCase CASE =
      new EvalCase("c", "q", "single-source", null, "answer", List.of(), null, null, null);

  private CheckResult run(Claim... claims) {
    return check.run(CASE, new Answer(false, List.of(claims)), new CaseState());
  }

  @Test
  void validCitationsPass() {
    assertTrue(run(new Claim("x", List.of("d#a")), new Claim("y", List.of("d#a", "d#b"))).passed());
  }

  @Test
  void claimWithoutCitationFails() {
    var result = run(new Claim("Safari 14 works", List.of()));
    assertFalse(result.passed());
    assertTrue(
        result.reason().contains("no citation") && result.reason().contains("Safari 14 works"),
        result.reason());
  }

  @Test
  void claimWithNullCitationsFailsWithoutCrashing() {
    var result = run(new Claim("x", null));
    assertFalse(result.passed());
    assertTrue(result.reason().contains("no citation"), result.reason());
  }

  @Test
  void fabricatedCitationFailsAndNamesTheId() {
    var result = run(new Claim("x", List.of("d#nope")));
    assertFalse(result.passed());
    assertTrue(result.reason().contains("fabricated citation: d#nope"), result.reason());
  }

  @Test
  void oneFabricatedIdAmongRealOnesStillFailsAndOnlyNamesTheFabricatedOne() {
    var result = run(new Claim("x", List.of("d#a", "d#nope")));
    assertFalse(result.passed());
    assertTrue(result.reason().contains("d#nope"), result.reason());
    assertFalse(
        result.reason().contains("d#a"), "the real ID must not be blamed: " + result.reason());
  }

  @Test
  void everyBadClaimIsListed() {
    var result = run(new Claim("first", List.of()), new Claim("second", List.of("d#nope")));
    assertTrue(
        result.reason().contains("first") && result.reason().contains("second"), result.reason());
  }

  @Test
  void refusalHasNoClaimsAndPasses() {
    assertTrue(check.run(CASE, new Answer(true, List.of()), new CaseState()).passed());
  }

  @Test
  void nullClaimsListPasses() {
    assertTrue(check.run(CASE, new Answer(true, null), new CaseState()).passed());
  }
}

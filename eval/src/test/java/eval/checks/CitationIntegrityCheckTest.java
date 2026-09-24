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
    var r = run(new Claim("Safari 14 works", List.of()));
    assertFalse(r.passed());
    assertTrue(
        r.reason().contains("no citation") && r.reason().contains("Safari 14 works"), r.reason());
  }

  @Test
  void claimWithNullCitationsFailsWithoutCrashing() {
    var r = run(new Claim("x", null));
    assertFalse(r.passed());
    assertTrue(r.reason().contains("no citation"), r.reason());
  }

  @Test
  void fabricatedCitationFailsAndNamesTheId() {
    var r = run(new Claim("x", List.of("d#nope")));
    assertFalse(r.passed());
    assertTrue(r.reason().contains("fabricated citation: d#nope"), r.reason());
  }

  @Test
  void oneFabricatedIdAmongRealOnesStillFailsAndOnlyNamesTheFabricatedOne() {
    var r = run(new Claim("x", List.of("d#a", "d#nope")));
    assertFalse(r.passed());
    assertTrue(r.reason().contains("d#nope"), r.reason());
    assertFalse(r.reason().contains("d#a"), "the real ID must not be blamed: " + r.reason());
  }

  @Test
  void everyBadClaimIsListed() {
    var r = run(new Claim("first", List.of()), new Claim("second", List.of("d#nope")));
    assertTrue(r.reason().contains("first") && r.reason().contains("second"), r.reason());
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

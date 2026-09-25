package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import kb.Chunk;
import org.junit.jupiter.api.Test;

class StaleCasesTest {
  private final KnowledgeBase knowledge =
      new KnowledgeBase(List.of(new Chunk("d#a", "d", "body")));

  private EvalCase caseWith(String id, String confirmedHash, boolean withFact) {
    List<ExpectedFact> facts =
        withFact ? List.of(new ExpectedFact("f", List.of("d#a"), List.of())) : List.of();
    return new EvalCase(
        id,
        "q",
        withFact ? "single-source" : "out-of-scope",
        null,
        withFact ? "answer" : "refuse",
        facts,
        "authored",
        "me",
        "2026-09-25",
        confirmedHash);
  }

  @Test
  void aCaseWhoseHashStillMatchesIsNotStale() {
    String current = CaseHash.of(caseWith("c1", null, true), knowledge);
    assertEquals(List.of(), StaleCases.warnings(List.of(caseWith("c1", current, true)), knowledge));
  }

  @Test
  void aChangedGoldChunkWarnsAndNamesTheCase() {
    List<String> warnings =
        StaleCases.warnings(List.of(caseWith("c1", "000000000000", true)), knowledge);
    assertEquals(1, warnings.size());
    assertTrue(warnings.get(0).startsWith("case 'c1':"), warnings.get(0));
    assertTrue(warnings.get(0).contains("000000000000"), warnings.get(0));
  }

  @Test
  void aCaseWithNoConfirmedHashNeverWarns() {
    assertEquals(List.of(), StaleCases.warnings(List.of(caseWith("c1", null, true)), knowledge));
  }

  @Test
  void aRefuseCaseHasNothingToGoStale() {
    assertEquals(
        List.of(), StaleCases.warnings(List.of(caseWith("oos", "000000000000", false)), knowledge));
  }
}

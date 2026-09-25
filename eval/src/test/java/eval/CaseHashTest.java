package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import kb.Chunk;
import org.junit.jupiter.api.Test;

class CaseHashTest {
  private static EvalCase caseWith(List<ExpectedFact> facts) {
    return new EvalCase(
        "c1", "q", "single-source", null, "answer", facts, "authored", "me", "2026-09-25");
  }

  private static ExpectedFact fact(String... chunkIds) {
    return new ExpectedFact("f", List.of(chunkIds), List.of());
  }

  private static KnowledgeBase knowledge(String textOfA, String textOfB, String textOfOther) {
    return new KnowledgeBase(
        List.of(
            new Chunk("d#a", "d", textOfA),
            new Chunk("d#b", "d", textOfB),
            new Chunk("d#other", "d", textOfOther)));
  }

  @Test
  void isTwelveHexCharactersAndStable() {
    EvalCase evalCase = caseWith(List.of(fact("d#a")));
    String hash = CaseHash.of(evalCase, knowledge("one", "two", "x"));
    assertTrue(hash.matches("[0-9a-f]{12}"), hash);
    assertEquals(hash, CaseHash.of(evalCase, knowledge("one", "two", "x")));
  }

  @Test
  void changesWhenAGoldChunkTextChanges() {
    EvalCase evalCase = caseWith(List.of(fact("d#a")));
    assertNotEquals(
        CaseHash.of(evalCase, knowledge("one", "two", "x")),
        CaseHash.of(evalCase, knowledge("one, edited", "two", "x")));
  }

  @Test
  void ignoresAChangeToAChunkThatIsNotGold() {
    EvalCase evalCase = caseWith(List.of(fact("d#a")));
    assertEquals(
        CaseHash.of(evalCase, knowledge("one", "two", "x")),
        CaseHash.of(evalCase, knowledge("one", "two", "an unrelated edit")));
  }

  @Test
  void doesNotDependOnTheOrderOrRepetitionOfGoldChunks() {
    KnowledgeBase knowledge = knowledge("one", "two", "x");
    assertEquals(
        CaseHash.of(caseWith(List.of(fact("d#a", "d#b"))), knowledge),
        CaseHash.of(caseWith(List.of(fact("d#b"), fact("d#a", "d#b"))), knowledge));
  }

  @Test
  void isNullForACaseWithNoGoldChunks() {
    assertNull(CaseHash.of(caseWith(List.of()), knowledge("one", "two", "x")));
  }
}

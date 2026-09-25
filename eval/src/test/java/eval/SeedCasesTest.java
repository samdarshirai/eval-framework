package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import kb.Chunker;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class SeedCasesTest {
  private final KnowledgeBase knowledge;
  private final List<EvalCase> cases;

  SeedCasesTest() throws Exception {
    Map<String, Object> cfg = new Yaml().load(Files.readString(Path.of("eval/config.yaml")));
    List<String> categories =
        ((List<?>) cfg.get("categories")).stream().map(String::valueOf).toList();
    knowledge = new KnowledgeBase(Chunker.chunkDir(Path.of("docs")));
    cases =
        EvalCaseLoader.load(
            Path.of("eval/cases"), knowledge, categories); // throws on any bad gold chunk
  }

  @Test
  void seedsExerciseEveryCoveragePath() {
    var facts = cases.stream().flatMap(evalCase -> evalCase.facts().stream()).toList();
    assertTrue(
        facts.stream().anyMatch(fact -> fact.keywords().isEmpty()),
        "need a fact without keywords (one judge call over all claims)");
    assertTrue(
        facts.stream().anyMatch(fact -> !fact.keywords().isEmpty()),
        "need a fact with keywords (keyword filter, then judge confirm)");
    assertTrue(
        cases.stream().anyMatch(evalCase -> evalCase.facts().size() > 1),
        "need a case with two facts (every fact must be covered)");
    assertTrue(
        facts.stream().anyMatch(fact -> fact.chunks().size() > 1),
        "need a fact with alternative gold chunks (D5 any-of)");
    assertTrue(
        cases.stream()
            .anyMatch(
                evalCase ->
                    evalCase.category().equals("false-premise")
                        && evalCase.expectedBehavior().equals("answer")),
        "need a false-premise case that expects an answer (D3)");
  }

  /**
   * A keyword found in none of the fact's gold chunks is almost certainly a typo, and makes the
   * fact impossible to cover.
   */
  @Test
  void everyKeywordAppearsInAGoldChunkOfItsFact() {
    for (EvalCase evalCase : cases) {
      for (ExpectedFact fact : evalCase.facts()) {
        for (String keyword : fact.keywords()) {
          assertTrue(
              fact.chunks().stream()
                  .anyMatch(
                      id -> knowledge.get(id).text().toLowerCase().contains(keyword.toLowerCase())),
              evalCase.id()
                  + ": keyword '"
                  + keyword
                  + "' is in none of the gold chunks "
                  + fact.chunks());
        }
      }
    }
  }

  // A doc edit that changes a gold chunk must be noticed and re-confirmed, so CI fails until the
  // author checks the case and runs eval.StampCaseHashes again (D21).
  @Test
  void everySeedCaseWithFactsIsStampedAndNotStale() {
    for (EvalCase evalCase : cases) {
      String currentHash = CaseHash.of(evalCase, knowledge);
      if (currentHash == null) {
        continue;
      }
      assertEquals(
          currentHash,
          evalCase.confirmedHash(),
          evalCase.id()
              + ": confirmed_hash is missing or stale. Check the case against the docs, then run"
              + " java -cp eval/target/eval.jar eval.StampCaseHashes");
    }
  }

  private long count(String category) {
    return cases.stream().filter(evalCase -> evalCase.category().equals(category)).count();
  }

  private static String documentOf(String chunkId) {
    return chunkId.split("#")[0];
  }

  // The documented shape of the set (D2-D4, D8). Changing a number here is a deliberate decision:
  // adding a case means retiring or merging one (D19).
  @Test
  void singleSourceHasEightCases() {
    assertEquals(8, count("single-source"));
  }

  @Test
  void multiSourceHasSixCases() {
    assertEquals(6, count("multi-source"));
  }

  @Test
  void falsePremiseHasSixCases() {
    assertEquals(6, count("false-premise"));
  }

  @Test
  void outOfScopeHasFiveCasesTwoUnrelatedAndThreePlausibleNonexistent() {
    assertEquals(5, count("out-of-scope"));
    var outOfScope =
        cases.stream().filter(evalCase -> evalCase.category().equals("out-of-scope")).toList();
    assertEquals(
        2, outOfScope.stream().filter(evalCase -> "unrelated".equals(evalCase.subtype())).count());
    assertEquals(
        3,
        outOfScope.stream()
            .filter(evalCase -> "plausible-nonexistent".equals(evalCase.subtype()))
            .count());
  }

  @Test
  void edgeCaseHasThreeCases() {
    assertEquals(3, count("edge-case"));
  }

  @Test
  void falsePremiseCasesExpectAnAnswerAndOutOfScopeCasesExpectARefusalWithNoFacts() {
    for (EvalCase evalCase : cases) {
      if (evalCase.category().equals("false-premise")) {
        assertEquals("answer", evalCase.expectedBehavior(), evalCase.id());
      }
      if (evalCase.category().equals("out-of-scope")) {
        assertEquals("refuse", evalCase.expectedBehavior(), evalCase.id());
        assertTrue(evalCase.facts().isEmpty(), evalCase.id() + ": a refuse case has no facts");
      }
    }
  }

  @Test
  void everyCaseRecordsSourceOwnerAndAdded() {
    for (EvalCase evalCase : cases) {
      assertNotNull(evalCase.source(), evalCase.id() + ": source");
      assertNotNull(evalCase.owner(), evalCase.id() + ": owner");
      assertNotNull(evalCase.added(), evalCase.id() + ": added");
    }
  }

  @Test
  void everySingleSourceCaseStaysInOneDocument() {
    for (EvalCase evalCase : cases) {
      if (evalCase.category().equals("single-source")) {
        long documents =
            evalCase.facts().stream()
                .flatMap(fact -> fact.chunks().stream())
                .map(SeedCasesTest::documentOf)
                .distinct()
                .count();
        assertEquals(1, documents, evalCase.id());
      }
    }
  }

  @Test
  void everyMultiSourceCaseNeedsMoreThanOneDocument() {
    for (EvalCase evalCase : cases) {
      if (evalCase.category().equals("multi-source")) {
        long documents =
            evalCase.facts().stream()
                .flatMap(fact -> fact.chunks().stream())
                .map(SeedCasesTest::documentOf)
                .distinct()
                .count();
        assertTrue(documents > 1, evalCase.id() + ": its facts must live in different documents");
      }
    }
  }

  @Test
  void aMultiSourceSeedHasFactsInDifferentDocuments() {
    assertTrue(
        cases.stream()
            .filter(evalCase -> evalCase.category().equals("multi-source"))
            .anyMatch(
                evalCase ->
                    evalCase.facts().stream()
                            .flatMap(fact -> fact.chunks().stream())
                            .map(chunkId -> chunkId.split("#")[0])
                            .distinct()
                            .count()
                        > 1),
        "need a multi-source case whose facts live in two documents (Source check)");
  }
}

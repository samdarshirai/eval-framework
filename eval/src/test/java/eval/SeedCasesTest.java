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

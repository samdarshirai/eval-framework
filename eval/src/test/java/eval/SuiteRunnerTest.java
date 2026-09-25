package eval;

import static org.junit.jupiter.api.Assertions.*;

import eval.checks.Check;
import eval.checks.CheckResult;
import eval.checks.Registered;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SuiteRunnerTest {
  @TempDir Path dir;

  /** Attempt results per case id, consumed in order; a missing entry means "fail". */
  private final Map<String, Deque<Boolean>> script = new HashMap<>();

  private final Map<String, Integer> attempts = new HashMap<>();

  private void attempts(String id, Boolean... passes) {
    script.put(id, new ArrayDeque<>(Arrays.asList(passes)));
  }

  private Assistant assistant() {
    return (question, runId) -> {
      attempts.merge(question, 1, Integer::sum);
      Deque<Boolean> outcomes = script.get(question);
      Boolean next = outcomes == null ? null : outcomes.poll();
      if (next == null) {
        throw new IOException("assistant unavailable");
      }
      return new Answer(next, List.of());
    };
  }

  private SuiteRunner runner() {
    Check passesWhenRefused =
        new Check() {
          @Override
          public String name() {
            return "Refusal";
          }

          @Override
          public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
            return answer.refused() ? CheckResult.ok() : CheckResult.fail("answered");
          }
        };
    return new SuiteRunner(
        new CaseRunner(assistant(), List.of(new Registered(passesWhenRefused, true))));
  }

  private static EvalCase evalCase(String id) {
    return new EvalCase(
        id, id, "single-source", null, "refuse", List.of(), "src", "me", "2026-01-01");
  }

  private Baseline baseline(String... passedIds) throws IOException {
    StringBuilder json = new StringBuilder("{\"cases\":[");
    for (int index = 0; index < passedIds.length; index++) {
      json.append(index == 0 ? "" : ",")
          .append("{\"id\":\"")
          .append(passedIds[index])
          .append("\",\"passed\":true}");
    }
    Path file = dir.resolve("baseline.json");
    Files.writeString(file, json.append("]}").toString());
    return Baseline.load(file);
  }

  private static List<String> ids(SuiteRunner.Outcome outcome) {
    return outcome.results().stream().map(CaseResult::id).toList();
  }

  @Test
  void withoutABaselineEveryCaseRunsOnceAndThereIsNoComparison() {
    attempts("a", false);
    attempts("b", true);
    var outcome = runner().run(List.of(evalCase("a"), evalCase("b")), "run", null);
    assertNull(outcome.comparison());
    assertEquals(1, attempts.get("a"));
    assertEquals(List.of(false, true), outcome.results().stream().map(CaseResult::passed).toList());
  }

  @Test
  void aCaseThatPassedInTheBaselineAndFailsTwiceIsARegressionAndRanTwice() throws Exception {
    attempts("a", false, false);
    var outcome = runner().run(List.of(evalCase("a")), "run", baseline("a"));
    assertEquals(2, attempts.get("a"));
    assertEquals(List.of("a"), outcome.comparison().regressions());
    assertFalse(outcome.results().get(0).passed());
    assertEquals("baseline.json", outcome.comparison().file());
  }

  @Test
  void aFlakeThatPassesOnTheRerunCountsAsPassedAndIsListedNotFailed() throws Exception {
    attempts("a", false, true);
    var outcome = runner().run(List.of(evalCase("a")), "run", baseline("a"));
    assertTrue(outcome.results().get(0).passed());
    assertEquals(List.of(), outcome.comparison().regressions());
    assertEquals(
        List.of(new SuiteReport.Comparison.Rerun("a", true)), outcome.comparison().reruns());
  }

  @Test
  void aCaseThatFailedInTheBaselineIsNotRerun() throws Exception {
    attempts("a", false);
    var outcome = runner().run(List.of(evalCase("a")), "run", baseline("other"));
    assertEquals(1, attempts.get("a"));
    assertEquals(List.of(), outcome.comparison().reruns());
  }

  @Test
  void aNewCaseThatFailsIsNotRerunAndARetiredBaselineCaseIsIgnored() throws Exception {
    attempts("new", false);
    var outcome = runner().run(List.of(evalCase("new")), "run", baseline("retired"));
    assertEquals(1, attempts.get("new"));
    assertEquals(List.of(), outcome.comparison().reruns());
    assertNull(attempts.get("retired"));
  }

  @Test
  void aCaseThatPassedNowIsNotRerun() throws Exception {
    attempts("a", true);
    runner().run(List.of(evalCase("a")), "run", baseline("a"));
    assertEquals(1, attempts.get("a"));
  }

  @Test
  void anAssistantErrorOnTheRerunCountsAsFailingAgain() throws Exception {
    attempts("a", false); // the second attempt has no scripted outcome: the fake assistant throws
    var outcome = runner().run(List.of(evalCase("a")), "run", baseline("a"));
    assertEquals(2, attempts.get("a"));
    assertEquals(List.of("a"), outcome.comparison().regressions());
    assertEquals("assistant unavailable", outcome.results().get(0).error());
  }

  @Test
  void resultsKeepTheOrderOfTheCasesEvenWhenOneIsRerun() throws Exception {
    attempts("a", true);
    attempts("b", false, true);
    attempts("c", true);
    var outcome =
        runner().run(List.of(evalCase("a"), evalCase("b"), evalCase("c")), "run", baseline("b"));
    assertEquals(List.of("a", "b", "c"), ids(outcome));
    assertTrue(outcome.results().get(1).passed());
  }

  @Test
  void anOutOfScopeCaseIsNeverRerunSoAFlakyHallucinationStillFailsTheRun() throws Exception {
    attempts("oos", false, true);
    var outOfScope =
        new EvalCase(
            "oos",
            "oos",
            "out-of-scope",
            "unrelated",
            "refuse",
            List.of(),
            "src",
            "me",
            "2026-01-01");
    var outcome = runner().run(List.of(outOfScope), "run", baseline("oos"));
    assertEquals(1, attempts.get("oos"));
    assertFalse(outcome.results().get(0).passed());
    assertEquals(List.of(), outcome.comparison().reruns());
  }

  private Baseline baselineWith(String passedJson) throws IOException {
    Path file = dir.resolve("baseline.json");
    Files.writeString(file, "{\"cases\":[" + passedJson + "]}");
    return Baseline.load(file);
  }

  @Test
  void aCaseThatFailedInTheBaselineAndPassesNowIsListedAsImproved() throws Exception {
    attempts("a", true);
    var outcome =
        runner()
            .run(List.of(evalCase("a")), "run", baselineWith("{\"id\":\"a\",\"passed\":false}"));
    assertEquals(List.of("a"), outcome.comparison().improved());
    assertEquals(1, attempts.get("a"));
  }

  @Test
  void aCaseThatStillFailsOrIsNewOrPassedBeforeIsNotImproved() throws Exception {
    attempts("still-fails", false);
    attempts("new", true);
    attempts("was-passing", true);
    var outcome =
        runner()
            .run(
                List.of(evalCase("still-fails"), evalCase("new"), evalCase("was-passing")),
                "run",
                baselineWith(
                    "{\"id\":\"still-fails\",\"passed\":false},{\"id\":\"was-passing\",\"passed\":true}"));
    assertEquals(List.of(), outcome.comparison().improved());
  }

  @Test
  void aFlakeThatPassedInTheBaselineIsNotImprovedEvenThoughItPassesOnTheRerun() throws Exception {
    attempts("a", false, true);
    var outcome = runner().run(List.of(evalCase("a")), "run", baseline("a"));
    assertEquals(List.of(), outcome.comparison().improved());
  }

  @Test
  void anOutOfScopeCaseThatFailedBeforeAndPassesNowIsImprovedToo() throws Exception {
    attempts("oos", true);
    var outOfScope =
        new EvalCase(
            "oos",
            "oos",
            "out-of-scope",
            "unrelated",
            "refuse",
            List.of(),
            "src",
            "me",
            "2026-01-01");
    var outcome =
        runner().run(List.of(outOfScope), "run", baselineWith("{\"id\":\"oos\",\"passed\":false}"));
    assertEquals(List.of("oos"), outcome.comparison().improved());
  }
}

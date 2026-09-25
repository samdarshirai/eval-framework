# Eval Harness — Plan C: Baseline regression and the single re-run (units 18–19) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **As built (differs from the tasks below):** the final review and later requests changed five things, recorded in D46 and D47 of `grilling-decisions.md`.
> 1. Out-of-scope cases are never re-run, so a lucky re-run cannot clear a hallucination (Review Focus 4 and Task 3 hold only for other categories).
> 2. A baseline with an empty `cases` list, a duplicate id, or none of the run's cases exits 2 (Review Focus 1 also covers these).
> 3. A baseline path that is unset or has no file behind it is logged and the run goes ahead without one (Review Focus 1 and the `--baseline` missing-file test say exit 2; that only holds for a file that exists but is unusable).
> 4. The `--baseline` flag became the general `--<setting> <value>` override for every config key (D47); `baseline` and `skipCalibration` are config keys.
> 5. `HarnessTest` request counts include the reachability ping (one more than the tasks say).

**Goal:** `--baseline <file>` makes a case that passed in a previous run and fails now fail the run, even above the pass floor. Before it counts, that case is re-run once and only counts if it fails again.

**Architecture:** A small `Baseline` class reads a previous report JSON (only `cases[].id` and `cases[].passed`). A new `SuiteRunner` replaces the case loop in `Harness`: it runs every case once, then, only when a baseline is given, re-runs once each case that passed in the baseline and failed now, and keeps the re-run result. `SuiteReport` gets an optional `Comparison` (file, the re-runs and how each ended) from which the regressions and the exit reason are derived. Console and JSON report both show it. No new dependencies and no new checks.

**Tech Stack:** Java 21, Maven multi-module, JUnit 5, Jackson. No new dependencies.

**Spec:** `usercentrics-eval-harness-plan.md` (Step 4: Exit code, Non-determinism), `build-order.md` units 18–19, `grilling-decisions.md` D13, D14, D30, D45, `CONTEXT.md` (Baseline, Regression). Previous plans: `docs/superpowers/plans/2026-09-24-eval-harness-units-1-8.md`, `…plan-a-units-9-12.md`, `…plan-b-units-13-16.md`.

**Later plans (not in this one):** D = 21–22 (28 cases, doc-hash warning); E = 17, 20 (Relevance, cost); F = 23–26 (live-change rehearsal, PATTERN.md, SCALE-PLAN.md, README).

## Global Constraints

- Java 21. No new dependencies. `eval/` stays plain Java and never depends on `assistant/` (D28, D37).
- Regression (D13): a case that **passed in the baseline and fails now**. Any regression makes the exit code non-zero even when the pass rate is above the floor. Without `--baseline`, no regression check and no re-run happen.
- Baseline (CONTEXT.md): a previous report JSON, promoted by copying it to `caseResults/baseline.json` (already un-ignored in `.gitignore`). Promotion stays a manual `cp`; the harness never writes a baseline itself.
- Re-run (D14, unit 19): a suspected regression is re-run **once**, and only counts if it fails both attempts. The report shows which cases needed a re-run and how many.
- The harness never calls the assistant before every argument and file is validated: a bad `--baseline` exits 2 before any assistant call (same rule as a bad `--config`, D29).
- `eval/` tests run with the repo root as working directory. CI runs `mvn -B test` with no API key, so no test may call a real LLM.
- Code style (user preferences): every `if`/`else`/`for`/`while`/`try`/`catch` body in braces on its own lines, never a one-line body; descriptive identifiers (no `a`, `c`, `e2`); google-java-format 1.25.2 applied once at the end on the files this plan touches (Task 4).
- Commit messages are plain: no `Co-Authored-By` or `Claude-Session` trailer lines (user instruction, overrides the harness default). Never print or log `OPENROUTER_API_KEY`.

## Setup (before Task 1)

Units 13–16 are merged to `main` (PR #6), so branch from it:

```bash
git status --short                                # expect only ?? .superpowers/ and ?? eval/testing-config-param.md
git checkout -b feat/eval-harness-units-18-19     # from main
mvn -B -q test                                    # all green before any change
```

`eval/testing-config-param.md` and `.superpowers/` are untracked and not ours: leave them alone, never `git add -A`.

## Review Focus

Inputs the spec implies but no unit's criteria pin down. Each has a test in the owning task.

1. **The baseline file is missing, is not JSON, is empty, or has no `cases` list (or a case without a text `id` / boolean `passed`):** exit 2 with an `ERROR:` line naming the file, no stack trace, zero assistant calls. A silent "no regressions" from a bad file would hide every regression. (Task 1, Task 3)
2. **A case that is in the baseline but no longer in the run (retired or renamed), and a new case that is not in the baseline and fails:** neither is a regression and neither is re-run. Growing or trimming the set must not fail the run. (Task 3)
3. **A case that failed in the baseline and fails again:** not a regression, not re-run (no extra cost); the old failure is already in the pass rate. (Task 3)
4. **A regression that passes on the re-run:** the case counts as passed (the re-run result replaces the first result), is listed as flaky in the report, and does not fail the run. That includes an out-of-scope case, so the out-of-scope rule sees the final result, not the flake. (Task 3)
5. **The assistant errors or times out on the re-run:** that counts as failing again, so the regression stands. A flaky endpoint must not clear a real regression. (Task 3)

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `eval/src/main/java/eval/Baseline.java` | create | reads a previous report, answers "did this case pass there?" |
| `eval/src/main/java/eval/SuiteRunner.java` | create | runs all cases, re-runs suspected regressions once (units 18–19) |
| `eval/src/main/java/eval/SuiteReport.java` | modify | `Comparison` record, regression exit reason |
| `eval/src/main/java/eval/ConsoleReport.java` | modify | prints the baseline section |
| `eval/src/main/java/eval/Harness.java` | modify | `--baseline` flag, use `SuiteRunner` |
| `grilling-decisions.md`, `scope.md`, `usercentrics-tech-stack-and-repo-structure.md` | modify | D46, scope table, flags line |
| tests | create/modify | `BaselineTest`, `SuiteRunnerTest`, `SuiteReportTest`, `HarnessTest` |

---

### Task 1: Read a baseline report

**Files:**
- Create: `eval/src/main/java/eval/Baseline.java`
- Test: `eval/src/test/java/eval/BaselineTest.java` (create)

**Interfaces:**
- Consumes: nothing from earlier tasks. Reads the report JSON that `ReportWriter` writes (`{"cases":[{"id":"...","passed":true,...}], ...}`); every other field is ignored, so any past report loads.
- Produces: `static Baseline Baseline.load(Path file) throws IOException` (throws `IllegalArgumentException` with the file name in the message for a missing file, bad JSON, no `cases` list, or a malformed case); `boolean passed(String caseId)` (true only when the case is in the baseline **and** passed there); `String name()` (the file name, for the report).

- [ ] **Step 1: Write the failing test**

```java
package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineTest {
  @TempDir Path dir;

  private Path write(String json) throws IOException {
    Path file = dir.resolve("baseline.json");
    Files.writeString(file, json);
    return file;
  }

  @Test
  void aCaseThatPassedInTheBaselineIsReportedAsPassed() throws Exception {
    Baseline baseline =
        Baseline.load(write("{\"cases\":[{\"id\":\"a\",\"passed\":true},{\"id\":\"b\",\"passed\":false}]}"));
    assertTrue(baseline.passed("a"));
    assertFalse(baseline.passed("b"));
  }

  @Test
  void aCaseThatIsNotInTheBaselineIsNotPassed() throws Exception {
    Baseline baseline = Baseline.load(write("{\"cases\":[{\"id\":\"a\",\"passed\":true}]}"));
    assertFalse(baseline.passed("new-case"));
  }

  @Test
  void otherReportFieldsAreIgnoredSoAnyPastReportLoads() throws Exception {
    Baseline baseline =
        Baseline.load(
            write(
                "{\"runId\":\"r\",\"passRate\":1.0,\"calibration\":{\"ran\":false},"
                    + "\"cases\":[{\"id\":\"a\",\"passed\":true,\"checks\":[],\"actual\":null}]}"));
    assertTrue(baseline.passed("a"));
    assertEquals("baseline.json", baseline.name());
  }

  @Test
  void aMissingFileNamesTheFile() {
    var failure =
        assertThrows(
            IllegalArgumentException.class, () -> Baseline.load(dir.resolve("nope.json")));
    assertTrue(failure.getMessage().contains("nope.json"), failure.getMessage());
  }

  @Test
  void invalidJsonAnEmptyFileAndAMissingCasesListAreErrorsNotAnEmptyBaseline() throws Exception {
    for (String body : new String[] {"{not json", "", "{}", "{\"cases\":\"x\"}", "[]"}) {
      var failure = assertThrows(IllegalArgumentException.class, () -> Baseline.load(write(body)));
      assertTrue(failure.getMessage().contains("baseline.json"), body + " -> " + failure.getMessage());
    }
  }

  @Test
  void aCaseWithoutATextIdOrABooleanPassedIsAnError() throws Exception {
    for (String body :
        new String[] {
          "{\"cases\":[{\"passed\":true}]}",
          "{\"cases\":[{\"id\":\"a\"}]}",
          "{\"cases\":[{\"id\":\"a\",\"passed\":\"yes\"}]}"
        }) {
      var failure = assertThrows(IllegalArgumentException.class, () -> Baseline.load(write(body)));
      assertTrue(failure.getMessage().contains("baseline.json"), body + " -> " + failure.getMessage());
    }
  }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `mvn -q -pl eval -am test -Dtest=BaselineTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL, `cannot find symbol: class Baseline`.

- [ ] **Step 3: Write the implementation**

```java
package eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A previous run's report, read as the known-good reference (CONTEXT.md: Baseline). Only which
 * cases passed is used, so any report the harness wrote can be promoted by copying it to
 * caseResults/baseline.json.
 */
final class Baseline {
  private final String name;
  private final Map<String, Boolean> passedById;

  private Baseline(String name, Map<String, Boolean> passedById) {
    this.name = name;
    this.passedById = passedById;
  }

  static Baseline load(Path file) throws IOException {
    String name = file.getFileName().toString();
    if (!Files.isRegularFile(file)) {
      throw new IllegalArgumentException("baseline file not found: " + name);
    }
    JsonNode cases;
    try {
      cases = new ObjectMapper().readTree(file.toFile()).path("cases");
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException(
          "baseline " + name + " is not valid JSON: " + e.getOriginalMessage());
    }
    if (!cases.isArray()) {
      throw new IllegalArgumentException(
          "baseline " + name + " has no 'cases' list; pass a report the harness wrote");
    }
    Map<String, Boolean> passedById = new HashMap<>();
    for (int index = 0; index < cases.size(); index++) {
      JsonNode entry = cases.get(index);
      if (!entry.path("id").isTextual() || !entry.path("passed").isBoolean()) {
        throw new IllegalArgumentException(
            "baseline " + name + ": case #" + (index + 1) + " needs a text 'id' and a boolean 'passed'");
      }
      passedById.put(entry.get("id").asText(), entry.get("passed").asBoolean());
    }
    return new Baseline(name, passedById);
  }

  /** True only when the case is in the baseline and passed there. */
  boolean passed(String caseId) {
    return passedById.getOrDefault(caseId, false);
  }

  String name() {
    return name;
  }
}
```

- [ ] **Step 4: Run the test, expect PASS**

Run: `mvn -q -pl eval -am test -Dtest=BaselineTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/java/eval/Baseline.java eval/src/test/java/eval/BaselineTest.java
git commit -m "feat: read a previous report as the baseline (unit 18)"
```

---

### Task 2: The regression exit rule and the report section

**Files:**
- Modify: `eval/src/main/java/eval/SuiteReport.java`
- Modify: `eval/src/main/java/eval/ConsoleReport.java`
- Test: `eval/src/test/java/eval/SuiteReportTest.java` (add tests and one helper)

**Interfaces:**
- Consumes: nothing new.
- Produces: `SuiteReport` gets a seventh component `Comparison baseline` (`null` when no `--baseline`) and keeps its six-argument constructor (delegating with `null`), so every existing caller and test compiles unchanged. New nested record `SuiteReport.Comparison(String file, List<Rerun> reruns)` with `record Rerun(String id, boolean passedOnRerun)` and a derived `List<String> regressions()` (the ids of the reruns with `passedOnRerun == false`, in order; serialized as `regressions`). `exitReasons()` gains `regression vs baseline (<file>): id1, id2`, placed after the out-of-scope reason and before the calibration reasons.

- [ ] **Step 1: Write the failing tests**

Add to `SuiteReportTest` (imports `List` already present through `java.util.*`):

```java
  private static SuiteReport withBaseline(SuiteReport base, SuiteReport.Comparison comparison) {
    return new SuiteReport(
        base.runId(),
        base.endpoint(),
        base.passFloor(),
        base.checks(),
        base.calibration(),
        base.cases(),
        comparison);
  }

  @Test
  void aRegressionFailsTheRunEvenWhenTheRateIsAboveTheFloor() {
    var comparison =
        new SuiteReport.Comparison(
            "baseline.json", List.of(new SuiteReport.Comparison.Rerun("c-3", false)));
    var run = withBaseline(report(28, Set.of(3), Set.of()), comparison);
    assertEquals(1, run.exitCode());
    assertEquals(
        List.of("regression vs baseline (baseline.json): c-3"), run.exitReasons());
  }

  @Test
  void aCaseThatPassedOnTheRerunIsNotARegression() {
    var comparison =
        new SuiteReport.Comparison(
            "baseline.json", List.of(new SuiteReport.Comparison.Rerun("c-3", true)));
    var run = withBaseline(report(28, Set.of(), Set.of()), comparison);
    assertEquals(0, run.exitCode());
    assertEquals(List.of(), comparison.regressions());
  }

  @Test
  void regressionsListOnlyTheCasesThatFailedTheRerunInOrder() {
    var comparison =
        new SuiteReport.Comparison(
            "b.json",
            List.of(
                new SuiteReport.Comparison.Rerun("x", false),
                new SuiteReport.Comparison.Rerun("y", true),
                new SuiteReport.Comparison.Rerun("z", false)));
    assertEquals(List.of("x", "z"), comparison.regressions());
    assertEquals(
        List.of("regression vs baseline (b.json): x, z"),
        withBaseline(report(28, Set.of(), Set.of()), comparison).exitReasons());
  }

  @Test
  void noBaselineMeansNoComparisonAndNoRegressionReason() {
    assertNull(report(28, Set.of(5), Set.of()).baseline());
    assertEquals(List.of(), report(28, Set.of(5), Set.of()).exitReasons());
  }
```

- [ ] **Step 2: Run them, expect a compile failure**

Run: `mvn -q -pl eval -am test -Dtest=SuiteReportTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL, `cannot find symbol: class Comparison` (and `baseline()`).

- [ ] **Step 3: Implement `SuiteReport`**

In `SuiteReport.java`, replace the record header and add the constructor and the record:

```java
public record SuiteReport(
    String runId,
    String endpoint,
    double passFloor,
    List<CheckInfo> checks,
    Calibration calibration,
    List<CaseResult> cases,
    Comparison baseline) {

  public SuiteReport(
      String runId,
      String endpoint,
      double passFloor,
      List<CheckInfo> checks,
      Calibration calibration,
      List<CaseResult> cases) {
    this(runId, endpoint, passFloor, checks, calibration, cases, null);
  }

  /**
   * The run compared with a baseline (unit 18): every case that passed there and failed on the
   * first attempt was re-run once (unit 19). {@code passedOnRerun} false means it failed both
   * attempts, which is a regression.
   */
  public record Comparison(String file, List<Rerun> reruns) {
    public record Rerun(String id, boolean passedOnRerun) {}

    @JsonProperty("regressions")
    public List<String> regressions() {
      return reruns.stream().filter(rerun -> !rerun.passedOnRerun()).map(Rerun::id).toList();
    }
  }
```

(keep the existing `Calibration` record and everything below unchanged), then in `exitReasons()` insert right after the out-of-scope block:

```java
    if (baseline != null && !baseline.regressions().isEmpty()) {
      reasons.add(
          "regression vs baseline ("
              + baseline.file()
              + "): "
              + String.join(", ", baseline.regressions()));
    }
```

- [ ] **Step 4: Implement the console section**

In `ConsoleReport.print`, after the `for (CaseResult caseResult ...)` loop and before the `Pass rate` printf, add `printBaseline(report.baseline(), out);`, and add the method:

```java
  private static void printBaseline(SuiteReport.Comparison baseline, PrintStream out) {
    if (baseline == null) {
      return;
    }
    out.println("Baseline: " + baseline.file());
    if (baseline.reruns().isEmpty()) {
      out.println("  no case that passed there failed now");
      return;
    }
    out.println("  re-run once: " + baseline.reruns().size());
    for (SuiteReport.Comparison.Rerun rerun : baseline.reruns()) {
      out.println(
          rerun.passedOnRerun()
              ? "  flaky      " + rerun.id() + " (failed, then passed on the re-run)"
              : "  REGRESSION " + rerun.id() + " (passed in the baseline, failed twice now)");
    }
  }
```

- [ ] **Step 5: Run the tests, expect PASS**

Run: `mvn -q -pl eval -am test`
Expected: PASS, including every pre-existing test (the six-argument constructor is kept).

- [ ] **Step 6: Commit**

```bash
git add eval/src/main/java/eval/SuiteReport.java eval/src/main/java/eval/ConsoleReport.java eval/src/test/java/eval/SuiteReportTest.java
git commit -m "feat: a regression against the baseline fails the run (unit 18)"
```

---

### Task 3: Run the suite with one re-run per suspected regression, and the `--baseline` flag

**Files:**
- Create: `eval/src/main/java/eval/SuiteRunner.java`
- Modify: `eval/src/main/java/eval/Harness.java`
- Test: `eval/src/test/java/eval/SuiteRunnerTest.java` (create)
- Test: `eval/src/test/java/eval/HarnessTest.java` (add tests, one small handler change)

**Interfaces:**
- Consumes: `CaseRunner.run(EvalCase, String runId) -> CaseResult` (package-private, constructor `CaseRunner(Assistant, List<Registered>)`); `Baseline.passed(String)` and `Baseline.name()` (Task 1); `SuiteReport.Comparison` and its 7-argument `SuiteReport` constructor (Task 2); `CaseResult.id()` and `passed()`.
- Produces: `SuiteRunner(CaseRunner runner)`; `SuiteRunner.Outcome run(List<EvalCase> cases, String runId, Baseline baseline)` where `record Outcome(List<CaseResult> results, SuiteReport.Comparison comparison)`; `baseline == null` gives `comparison == null` and exactly one attempt per case. Results keep the order of `cases`; a re-run replaces the first result. CLI: `--baseline <file>`, resolved against the working directory like `--config`.

- [ ] **Step 1: Write the failing `SuiteRunnerTest`**

The fake assistant answers `refused: true` when told the next attempt for that question should pass and `refused: false` otherwise, and the single check passes when `refused` is true. That makes "attempt N of case X passes" scriptable without any LLM.

```java
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
    return new EvalCase(id, id, "single-source", null, "refuse", List.of(), "src", "me", "2026-01-01");
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
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `mvn -q -pl eval -am test -Dtest=SuiteRunnerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL, `cannot find symbol: class SuiteRunner`.

- [ ] **Step 3: Write `SuiteRunner`**

```java
package eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every case once. With a baseline it then re-runs, once, each case that passed in the
 * baseline and failed now (D14, unit 19): the second result replaces the first, so a flake that
 * passes the second time counts as a pass, and a case that fails both times is a regression.
 * Cases that failed in the baseline, and cases the baseline does not know, are never re-run: they
 * are not suspected regressions and a re-run would only cost calls.
 */
final class SuiteRunner {
  record Outcome(List<CaseResult> results, SuiteReport.Comparison comparison) {}

  private final CaseRunner runner;

  SuiteRunner(CaseRunner runner) {
    this.runner = runner;
  }

  /** The comparison in the outcome is null when there is no baseline. */
  Outcome run(List<EvalCase> cases, String runId, Baseline baseline) {
    List<CaseResult> results = new ArrayList<>();
    for (EvalCase evalCase : cases) {
      results.add(runner.run(evalCase, runId));
    }
    if (baseline == null) {
      return new Outcome(results, null);
    }
    List<SuiteReport.Comparison.Rerun> reruns = new ArrayList<>();
    for (int index = 0; index < cases.size(); index++) {
      CaseResult firstAttempt = results.get(index);
      if (firstAttempt.passed() || !baseline.passed(firstAttempt.id())) {
        continue;
      }
      CaseResult secondAttempt = runner.run(cases.get(index), runId);
      results.set(index, secondAttempt);
      reruns.add(new SuiteReport.Comparison.Rerun(firstAttempt.id(), secondAttempt.passed()));
    }
    return new Outcome(results, new SuiteReport.Comparison(baseline.name(), reruns));
  }
}
```

- [ ] **Step 4: Run it, expect PASS**

Run: `mvn -q -pl eval -am test -Dtest=SuiteRunnerTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (8 tests).

- [ ] **Step 5: Write the failing `HarnessTest` end-to-end tests**

`HarnessTest` needs one small change first so a test can make the second request answer differently. Add a field next to `replyFor` and use it in the handler:

```java
  private volatile String replyFromSecondRequest = null;
```

and in the handler replace `byte[] replyBytes = replyFor.getBytes();` with:

```java
          String reply =
              requests.get() >= 2 && replyFromSecondRequest != null
                  ? replyFromSecondRequest
                  : replyFor;
          byte[] replyBytes = reply.getBytes();
```

(`requests.incrementAndGet()` runs first, so the first request sees 1.) Then add these tests and two helpers:

```java
  private static final String CONFIDENT_ANSWER =
      "{\"refused\":false,\"claims\":[{\"claim\":\"It costs 5 euro\",\"citations\":[\"d#a\"]}]}";

  private void baseline(String json) throws IOException {
    Files.createDirectories(root.resolve("caseResults"));
    Files.writeString(root.resolve("caseResults/baseline.json"), json);
  }

  private com.fasterxml.jackson.databind.JsonNode reportJson(String output) throws IOException {
    var reportMatcher = java.util.regex.Pattern.compile("Report: (\\S+)").matcher(output);
    assertTrue(reportMatcher.find(), output);
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(root.resolve(reportMatcher.group(1)).toFile());
  }

  @Test
  void aRegressionIsRerunOnceFailsTheRunAndIsNamedInTheOutputAndTheReport() throws Exception {
    cases(OOS);
    baseline("{\"cases\":[{\"id\":\"oos-1\",\"passed\":true}]}");
    replyFor = CONFIDENT_ANSWER;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(1, code[0], output);
    assertEquals(2, requests.get(), "one attempt plus one re-run");
    assertTrue(output.contains("REGRESSION oos-1"), output);
    assertTrue(output.contains("RESULT: FAIL - regression vs baseline (baseline.json): oos-1"), output);
    var comparison = reportJson(output).get("baseline");
    assertEquals("baseline.json", comparison.get("file").asText());
    assertEquals("oos-1", comparison.get("regressions").get(0).asText());
    assertFalse(comparison.get("reruns").get(0).get("passedOnRerun").asBoolean());
  }

  @Test
  void aFlakeThatPassesOnTheRerunKeepsTheRunGreenAndIsListed() throws Exception {
    cases(OOS);
    baseline("{\"cases\":[{\"id\":\"oos-1\",\"passed\":true}]}");
    replyFor = CONFIDENT_ANSWER;
    replyFromSecondRequest = "{\"refused\":true,\"claims\":[]}";
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(0, code[0], output);
    assertEquals(2, requests.get());
    assertTrue(output.contains("flaky      oos-1"), output);
    assertTrue(output.contains("PASS") && output.contains("oos-1"), output);
    assertEquals(0, reportJson(output).get("baseline").get("regressions").size());
  }

  @Test
  void aCaseThatFailedInTheBaselineIsNotRerunAndIsNoRegression() throws Exception {
    cases(OOS);
    baseline("{\"cases\":[{\"id\":\"oos-1\",\"passed\":false}]}");
    replyFor = CONFIDENT_ANSWER;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(1, code[0], output); // the out-of-scope rule still fails the run
    assertEquals(1, requests.get());
    assertFalse(output.contains("regression vs baseline"), output);
    assertTrue(output.contains("no case that passed there failed now"), output);
  }

  @Test
  void withoutTheFlagThereIsNoBaselineSectionAndNoRerun() throws Exception {
    cases(OOS);
    replyFor = CONFIDENT_ANSWER;
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(1, requests.get());
    assertFalse(output.contains("Baseline:"), output);
    assertTrue(reportJson(output).get("baseline").isNull());
  }

  @Test
  void aBadBaselineFileExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/missing.json")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR") && output.contains("missing.json"), output);
    baseline("{not json");
    output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR") && output.contains("baseline.json"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void baselineWithoutAValueExitsTwoWithUsage() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--baseline")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR: --baseline needs a value") && output.contains("Usage"), output);
    assertEquals(0, requests.get());
  }
```

- [ ] **Step 6: Run them, expect failures**

Run: `mvn -q -pl eval -am test -Dtest=HarnessTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: the six new tests FAIL (`unknown argument '--baseline'`, exit 2 instead of 1/0); existing tests still pass.

- [ ] **Step 7: Wire the flag and the runner into `Harness`**

In `runInner`:

1. Next to `String configArg = null;` add `String baselineArg = null;`.
2. In the argument loop, add before the `--skip-calibration` branch:

```java
      } else if (args[i].equals("--baseline")
          && i + 1 < args.length
          && !args[i + 1].startsWith("--")) {
        baselineArg = args[++i];
```

3. In the error branch, make the "needs a value" test cover the new flag:

```java
                + (args[i].equals("--endpoint")
                        || args[i].equals("--config")
                        || args[i].equals("--baseline")
                    ? args[i] + " needs a value"
                    : "unknown argument '" + args[i] + "'"));
        out.println(
            "Usage: Harness [--config <file>] [--endpoint <url>] [--baseline <file>] [--skip-calibration]");
```

4. Right after `config` is loaded (before the trap-pairs check, so it happens before any assistant call), add:

```java
    Baseline baseline = baselineArg == null ? null : Baseline.load(root.resolve(baselineArg));
```

`Harness.run` already turns the `IllegalArgumentException` into `ERROR: …` and exit 2.

5. Replace the `CaseRunner` block (`CaseRunner runner = …` through the `for` loop that fills `caseResults`) and the `SuiteReport` construction with:

```java
    String runId = ReportWriter.newRunId();
    SuiteRunner.Outcome outcome =
        new SuiteRunner(new CaseRunner(client, checks)).run(cases, runId, baseline);
    SuiteReport report =
        new SuiteReport(
            runId,
            config.endpoint(),
            config.passFloor(),
            checkInfos(checks),
            calibration,
            outcome.results(),
            outcome.comparison());
```

Also update the class-level or nearby comment, if any, that describes the loop, and the `CaseRunner` class comment stays as is (it still describes one case).

- [ ] **Step 8: Run the whole module, expect PASS**

Run: `mvn -q -pl eval -am test`
Expected: PASS. If a test outside this plan fails on the `Usage:` text, it asserts only `contains("Usage")`, so it should not; fix the assertion, not the message, if one does.

- [ ] **Step 9: Commit**

```bash
git add eval/src/main/java/eval/SuiteRunner.java eval/src/main/java/eval/Harness.java eval/src/test/java/eval/SuiteRunnerTest.java eval/src/test/java/eval/HarnessTest.java
git commit -m "feat: --baseline compares with a previous run and re-runs a suspected regression once (units 18-19)"
```

---

### Task 4: Record the decision, update the docs, format

**Files:**
- Modify: `grilling-decisions.md` (add D46 after D45, above `## Open`)
- Modify: `scope.md` (table row for the baseline)
- Modify: `usercentrics-tech-stack-and-repo-structure.md:19` (flags line)

**Interfaces:** none (docs and formatting only).

- [ ] **Step 1: Add D46 to `grilling-decisions.md`**

Insert after D45 (numbering continues, same one-paragraph style):

```markdown
46. **Baseline and the single re-run as built (units 18-19).** `--baseline <file>` reads `cases[].id` and `cases[].passed` from any report the harness wrote; a bad file (missing, not JSON, no `cases` list, a case without a text `id` or boolean `passed`) exits 2 before any assistant call, so a broken baseline can never read as "no regressions". A baseline is promoted by hand: `cp caseResults/<run>.json caseResults/baseline.json` (`.gitignore` already keeps that one file). Only a case that passed in the baseline and failed on the first attempt is re-run, once, with the same run id; the re-run result replaces the first, so a flake that passes counts as a pass and the out-of-scope rule sees the final result. A case that fails both attempts is a regression: it fails the run even above the floor and is named in the output and in the report's `baseline.regressions`. Cases that failed in the baseline, cases not in the baseline (new) and baseline cases no longer in the set are never re-run and never regressions. An assistant error on the re-run counts as failing again. The report lists every re-run and how it ended (`baseline.reruns`), the free flakiness signal D14 asks for; if the re-run rate turns out high the upgrade is majority-of-N, not more retries. Not built: comparing per-check outcomes (only the case verdict is compared), and a `--promote-baseline` command.
```

- [ ] **Step 2: Update `scope.md`**

Delete the row `| Regression against a previous run (`--baseline`) | 18 | Never cut |` from "In scope, planned and not built yet" and add to the "In scope, built" table:

```markdown
| **Regression against a baseline** (`--baseline`): a case that passed there and fails now fails the run, after one re-run | Blocks a silent slide even when the pass rate stays above the floor; the re-run keeps flakes from crying wolf (D13, D14, D46) |
```

If unit 19 has its own row in the planned table, delete it too.

- [ ] **Step 3: Update the flags line** in `usercentrics-tech-stack-and-repo-structure.md:19`: replace `` `--baseline <file>` enables regression comparison`` with `` `--baseline <file>` compares with a previous report (promote one by copying it to `caseResults/baseline.json`) and fails the run on a regression after one re-run``.

- [ ] **Step 4: Format the Java files this plan changed** with google-java-format 1.25.2 in its own commit (the jar is `gjf.jar` in the session scratchpad; set `GJF_JAR` to it):

```bash
git add grilling-decisions.md scope.md usercentrics-tech-stack-and-repo-structure.md
git commit -m "docs: record the baseline regression and re-run decision (D46)"
java --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
     -jar "$GJF_JAR" --replace \
     eval/src/main/java/eval/Baseline.java eval/src/main/java/eval/SuiteRunner.java \
     eval/src/main/java/eval/SuiteReport.java eval/src/main/java/eval/ConsoleReport.java \
     eval/src/main/java/eval/Harness.java eval/src/test/java/eval/BaselineTest.java \
     eval/src/test/java/eval/SuiteRunnerTest.java eval/src/test/java/eval/SuiteReportTest.java \
     eval/src/test/java/eval/HarnessTest.java
mvn -B -q test
git commit -am "style: google-java-format on the files changed in units 18-19"
```

(`SuiteReport.java` and `HarnessTest.java` may reformat whole-file; that is why this commit is separate.)

- [ ] **Step 5: Manual check with a real run (needs `OPENROUTER_API_KEY`, done by the user, not CI)**

```bash
mvn -q -DskipTests package
java -jar assistant/target/assistant.jar &          # in another terminal
java -jar eval/target/eval.jar                       # writes caseResults/<run>.json
cp caseResults/<run>.json caseResults/baseline.json
java -jar eval/target/eval.jar --baseline caseResults/baseline.json
```

Expected: second run prints `Baseline: baseline.json` and `no case that passed there failed now`, exit code matches the pass-rate rules. To see a regression, edit one case's `expected_behavior` in `eval/cases/*.yaml`, rerun with `--baseline`, expect `REGRESSION <id>` and `RESULT: FAIL - regression vs baseline (baseline.json): <id>`, then `git checkout` the case file. Do not commit `caseResults/baseline.json` until the 28-case set exists (unit 21).

---

## Self-review

- **Spec coverage:** unit 18 (`--baseline` loads a previous results JSON, promotion by copying, regressions listed by name, non-zero even above floor, no baseline means no regression check): Tasks 1–3 and D46. Unit 19 (suspect re-run once, counts only if it fails both attempts, report shows which cases and how many): Task 3 (`SuiteRunner`, console `re-run once: N`, JSON `baseline.reruns`). D13/D14 as written in the spec.
- **Placeholder scan:** none; the only run-derived items are the manual real-run commands in Task 4 Step 5, which need an API key and are flagged as the user's.
- **Type consistency:** `Baseline.load(Path)`, `passed(String)`, `name()`; `SuiteReport.Comparison(String file, List<Rerun> reruns)`, `Comparison.Rerun(String id, boolean passedOnRerun)`, `regressions()`; `SuiteRunner.Outcome(List<CaseResult> results, SuiteReport.Comparison comparison)` and `run(List<EvalCase>, String, Baseline)` are spelled identically across Tasks 1–3. JSON keys asserted in `HarnessTest` (`baseline.file`, `baseline.reruns[].passedOnRerun`, `baseline.regressions`) match the record component and `@JsonProperty` names.
- **Review Focus:** 1 → `BaselineTest` (bad file, bad JSON, empty, no `cases`, malformed case) and `HarnessTest.aBadBaselineFileExitsTwoBeforeAnyAssistantCall`; 2 → `SuiteRunnerTest.aNewCaseThatFailsIsNotRerun…`; 3 → `aCaseThatFailedInTheBaselineIsNotRerun` (unit and end-to-end); 4 → `aFlakeThatPassesOnTheRerun…` (unit and end-to-end, the end-to-end case is out-of-scope); 5 → `anAssistantErrorOnTheRerunCountsAsFailingAgain`.

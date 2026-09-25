# Eval Harness — Plan E: Relevance and measured cost (units 17, 20) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The harness judges every claim for relevance without ever failing a case on it (unit 17), and every run reports its measured judge calls, tokens, estimated cost and wall-clock time (unit 20).

**Architecture:** Unit 17 is one new check: a `Judge.relevant` question, a `RelevanceCheck` class and one line in `Checks` with `gating=false`, plus an "advisory flagged on N cases" line in the report. Unit 20 wraps the judge's `Llm` in a `MeteredLlm` that records calls and tokens into a `UsageMeter`, labelled with the check that is running; `CaseRunner` times the assistant calls and tells the meter which check is running; `SuiteReport` gets a `usage` block that `ConsoleReport` prints and the JSON carries. `OpenRouterLlm` starts returning the `usage` token counts it already receives.

**Tech Stack:** Java 21, Maven multi-module, JUnit 5, Jackson, SnakeYAML. No new dependencies.

**Spec:** `usercentrics-eval-harness-plan.md` (Step 4: Relevance, pass rule, Cost and time), `build-order.md` units 17 and 20, `grilling-decisions.md` D12, D14–D17, D28, D30, D39, D40, D45, D47, `CONTEXT.md` (Gating check / Advisory check). Previous plans: `docs/superpowers/plans/2026-09-24-eval-harness-units-1-8.md`, `…plan-a-units-9-12.md`, `…plan-b-units-13-16.md`, `2026-09-25-eval-harness-plan-c-units-18-19.md`, `…plan-d-units-21-22.md`.

**Not in this plan (and why):**
- **Unit 23** (three rehearsed live changes) has no code left to write: adding a check is one class plus one line in `Checks` (Task 2 of this plan is exactly that change, so it is a free dry run), the gating flag and `passFloor` are one value, and a case is YAML only. The rehearsal itself is done by a person, out loud, from a clean state.
- **Units 24, 25, 26** (`PATTERN.md`, `SCALE-PLAN.md`, README) are documents. Unit 25 needs the measured numbers from Task 8 of this plan, so write it after this plan.
- **The known open item** (stub fails 6/6 multi-source cases) is deliberately left as evidence (D36, D48); D44's retrieval metric is out of scope. Do not tune the stub here.
- **Unit 22 / D45 fail-fast** stays deferred: Task 8 produces the measured numbers D45 said to wait for.

## Global Constraints

- Java 21. No new dependencies. `eval/` stays plain Java and never depends on `assistant/` (D28, D37).
- **Relevance is advisory** (D12): its failures are shown in the case detail and counted in the summary, and never change a case's pass/fail, the pass rate, the exit code, or a baseline comparison. Making it gating is changing one `false` to `true` in `Checks`.
- Relevance is judged with **the main judge model** at temperature 0 and low effort (D14, D15). It is **uncalibrated in v1** (D16): say so, do not add it to calibration.
- A judge reply that is not clearly YES/NO **throws**; it is never read as NO (D41). On an advisory check that shows as `check error: ...`, still without failing the case.
- Every setting is a config key first, and `--<key> <value>` overrides it (D47). The new setting is `judgePricing` (USD per million tokens).
- The harness sees the assistant only over HTTP (D28), so **assistant tokens are not visible**. The report gives the assistant's calls and time only. Do not add a field to the contract to fix that (D44 argues the same way for `retrieved`).
- Cost is an **estimate** = measured tokens × the configured prices. It is never printed as `$0.0000` when no prices are set: it says it was not estimated.
- `eval/` tests run with the repo root as working directory. CI runs `mvn -B test` with no API key, so no test may call a real LLM.
- Code style (user preferences): every `if`/`else`/`for`/`while`/`try`/`catch` body in braces on its own lines, never a one-line body; descriptive identifiers (no `a`, `c`, `e2`); google-java-format 1.25.2 applied once at the end on the Java files this plan touches (Task 8).
- Commit messages are plain: no `Co-Authored-By` or `Claude-Session` trailer lines (user instruction, as in plans A–D, overrides the harness default). Never print or log `OPENROUTER_API_KEY`.

## Setup (before Task 1)

Units 21–22 are merged to `main` (PR #8), so branch from it:

```bash
git status --short                                # expect only ?? .superpowers/ and ?? eval/testing-config-param.md
git checkout -b feat/eval-harness-units-17-20     # from main
mvn -B -q test                                    # all green before any change
```

`eval/testing-config-param.md` and `.superpowers/` are untracked and not ours: leave them alone, never `git add -A`.

## Review Focus

Inputs the spec implies but no unit's criteria pin down. Each has a test in the owning task.

1. **A refusal (zero claims) and a judge that throws or answers unclearly on Relevance:** a refusal makes no judge call and passes; a throwing judge becomes an advisory `check error` outcome, the case verdict is unchanged and the run keeps going; the error still counts as "flagged" so it is not invisible. (Tasks 2, 3)
2. **A provider reply with no `usage` block, or a fake `Llm` that reports no tokens:** the call is still counted, tokens are 0, nothing crashes, no `NaN`. (Tasks 4, 5)
3. **No `judgePricing` in the config:** the report prints the tokens and says the cost was not estimated; the JSON has `judgeCostUsd: null`. It never prints `$0.0000`. A malformed `judgePricing` (missing key, negative, not a number) exits 2 before any assistant call. (Tasks 5, 6, 7)
4. **A baseline re-run (unit 19) and `--skipCalibration true`:** the re-run's assistant and judge calls are counted, because they are real spend; with calibration skipped there is no `calibration` row. (Task 7)
5. **A call that fails (HTTP error, the judge throws):** it is not counted, because the provider reported no usage for it; the report says nothing about it, and the failure shows as `check error` as before. Stated in D50 so the estimate is read as a floor on spend, not a bill. (Tasks 5, 8)

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `llm/src/main/java/llm/Completion.java` | create | reply text plus the token counts the provider reported |
| `llm/src/main/java/llm/Llm.java` | modify | default `completeWithUsage` |
| `llm/src/main/java/llm/OpenRouterLlm.java` | modify | parse `usage`; `complete` delegates to `completeWithUsage` |
| `llm/src/test/java/llm/OpenRouterLlmTest.java` | modify | usage parsing tests |
| `eval/src/main/java/eval/Judge.java` | modify | `relevant(question, claim)` and its prompt |
| `eval/src/main/java/eval/checks/RelevanceCheck.java` | create | advisory check, one judge call per claim |
| `eval/src/main/java/eval/checks/Checks.java` | modify | register Relevance, last, `gating=false` |
| `eval/src/main/java/eval/UsageMeter.java` | create | counts judge calls/tokens per check and assistant calls/time; `Pricing` |
| `eval/src/main/java/eval/MeteredLlm.java` | create | `Llm` decorator that feeds the meter |
| `eval/src/main/java/eval/EvalConfig.java` | modify | `judgePricing` setting |
| `eval/src/main/java/eval/SuiteReport.java` | modify | `advisoryFlags()`, `Usage` record, `usage` component |
| `eval/src/main/java/eval/ConsoleReport.java` | modify | advisory line, "Cost and time" block |
| `eval/src/main/java/eval/CaseRunner.java` | modify | time assistant calls, label the running check |
| `eval/src/main/java/eval/Harness.java` | modify | create the meter, wrap the judge, time the run |
| `eval/config.yaml` | modify | `judgePricing` default (D40 prices) |
| tests | create/modify | `JudgeRelevantTest`, `RelevanceCheckTest`, `UsageMeterTest`, `MeteredLlmTest`; `ChecksTest`, `EvalConfigTest`, `SuiteReportTest`, `ConsoleReportTest`, `CaseRunnerTest`, `HarnessTest` |
| `grilling-decisions.md`, `scope.md`, `usercentrics-tech-stack-and-repo-structure.md` | modify | D49, D50, status moves (Task 8) |

---

### Task 1: The Relevance judge question

**Files:**
- Modify: `eval/src/main/java/eval/Judge.java`
- Create: `eval/src/test/java/eval/JudgeRelevantTest.java`

**Interfaces:**
- Consumes: `Judge`'s private `yesOrNo(String reply)` (throws `IllegalStateException` on anything but YES/NO) and its `llm` field.
- Produces: `public boolean Judge.relevant(String question, String claim)` — true when the claim is pertinent to the question. Task 2 calls it.

- [ ] **Step 1: Write the failing test**

Create `eval/src/test/java/eval/JudgeRelevantTest.java`:

```java
package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JudgeRelevantTest {
  private static Judge saying(String reply) {
    return new Judge((system, user) -> reply);
  }

  @Test
  void yesMeansRelevantInAnyCaseWithPunctuation() {
    assertTrue(saying("YES").relevant("question", "claim"));
    assertTrue(saying("Yes.").relevant("question", "claim"));
    assertTrue(saying("**YES**").relevant("question", "claim"));
  }

  @Test
  void noMeansOffTopic() {
    assertFalse(saying("NO").relevant("question", "claim"));
    assertFalse(saying("no - this is about pricing").relevant("question", "claim"));
  }

  @Test
  void anythingElseIsAnErrorNeverASilentNo() {
    assertThrows(IllegalStateException.class, () -> saying("Maybe").relevant("question", "claim"));
    assertThrows(IllegalStateException.class, () -> saying("").relevant("question", "claim"));
    assertThrows(IllegalStateException.class, () -> saying(null).relevant("question", "claim"));
  }

  @Test
  void promptCarriesTheQuestionAndTheClaim() {
    AtomicReference<String> seen = new AtomicReference<>();
    new Judge(
            (system, user) -> {
              seen.set(user);
              return "YES";
            })
        .relevant("Which browsers are supported?", "Safari 14 is supported");
    assertTrue(seen.get().contains("Which browsers are supported?"), seen.get());
    assertTrue(seen.get().contains("Safari 14 is supported"), seen.get());
  }
}
```

- [ ] **Step 2: Run it to see it fail**

Run: `mvn -B -q -pl eval -am test -Dtest=JudgeRelevantTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAIL, `cannot find symbol: method relevant(String,String)`.

- [ ] **Step 3: Implement**

In `Judge.java`, add the prompt constant after `SUPPORTS_SYSTEM_PROMPT`:

```java
  private static final String RELEVANT_SYSTEM_PROMPT =
      """
You check whether a claim made by an assistant is pertinent to the user's question.
Answer YES if the claim helps answer the question, including background the answer needs to make sense.
Answer NO if the claim is off-topic padding: it may be true, but it does not help answer this question.
Reply with exactly one word: YES or NO.\
""";
```

and the method after `supports`:

```java
  /** Is the claim pertinent to the question, and not true-but-off-topic padding? */
  public boolean relevant(String question, String claim) {
    return yesOrNo(
        llm.complete(RELEVANT_SYSTEM_PROMPT, "Question: " + question + "\nClaim: " + claim));
  }
```

Also update the class Javadoc's first line from "Three narrow judge questions used by Coverage and Groundedness" to "Four narrow judge questions used by Coverage, Groundedness and Relevance".

- [ ] **Step 4: Run it to see it pass**

Run: `mvn -B -q -pl eval -am test -Dtest=JudgeRelevantTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/java/eval/Judge.java eval/src/test/java/eval/JudgeRelevantTest.java
git commit -m "feat: judge question for claim relevance (unit 17)"
```

---

### Task 2: RelevanceCheck, registered as advisory

**Files:**
- Create: `eval/src/main/java/eval/checks/RelevanceCheck.java`
- Modify: `eval/src/main/java/eval/checks/Checks.java`
- Modify: `eval/src/test/java/eval/checks/ChecksTest.java`, `eval/src/test/java/eval/HarnessTest.java` (one expected count)
- Create: `eval/src/test/java/eval/checks/RelevanceCheckTest.java`

**Interfaces:**
- Consumes: `Judge.relevant(String question, String claim)` (Task 1); `Check` (`String name()`, `CheckResult run(EvalCase, Answer, CaseState)`); `CheckResult.ok()` / `CheckResult.fail(String)`; `Registered(Check check, boolean gating)`.
- Produces: `RelevanceCheck(Judge judge)` with `name()` `"Relevance"`; registered last in `Checks.registered(kb, judge)` with `gating=false`. Task 3 reads the name `"Relevance"` only through `CheckInfo`, not by string.

- [ ] **Step 1: Write the failing tests**

Create `eval/src/test/java/eval/checks/RelevanceCheckTest.java`:

```java
package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RelevanceCheckTest {
  private static final CaseState STATE = new CaseState();

  private static EvalCase evalCase() {
    return new EvalCase(
        "c",
        "Which browsers are supported?",
        "single-source",
        null,
        "answer",
        List.of(),
        "authored",
        "o",
        "2026-09-25");
  }

  private static Answer answerOf(String... claimTexts) {
    List<Claim> claims = new ArrayList<>();
    for (String claimText : claimTexts) {
      claims.add(new Claim(claimText, List.of("d#a")));
    }
    return new Answer(false, claims);
  }

  @Test
  void aRefusalHasNoClaimsAndMakesNoJudgeCall() {
    int[] judgeCalls = new int[1];
    RelevanceCheck check =
        new RelevanceCheck(
            new Judge(
                (system, user) -> {
                  judgeCalls[0]++;
                  return "NO";
                }));
    CheckResult result = check.run(evalCase(), new Answer(true, List.of()), STATE);
    assertTrue(result.passed());
    assertEquals(0, judgeCalls[0]);
  }

  @Test
  void aNullClaimListIsTreatedAsNoClaims() {
    RelevanceCheck check = new RelevanceCheck(new Judge((system, user) -> "NO"));
    assertTrue(check.run(evalCase(), new Answer(false, null), STATE).passed());
  }

  @Test
  void everyClaimRelevantPasses() {
    RelevanceCheck check = new RelevanceCheck(new Judge((system, user) -> "YES"));
    assertTrue(check.run(evalCase(), answerOf("Safari 14 is supported", "Chrome is"), STATE).passed());
  }

  @Test
  void aNoIsFlaggedByNameAndTheOtherClaimsAreNot() {
    RelevanceCheck check =
        new RelevanceCheck(
            new Judge((system, user) -> user.contains("padding claim") ? "NO" : "YES"));
    CheckResult result =
        check.run(evalCase(), answerOf("Safari 14 is supported", "padding claim"), STATE);
    assertFalse(result.passed());
    assertTrue(result.reason().contains("padding claim"), result.reason());
    assertFalse(result.reason().contains("Safari 14"), result.reason());
  }

  @Test
  void theJudgeIsAskedAboutTheCasesQuestion() {
    List<String> prompts = new ArrayList<>();
    RelevanceCheck check =
        new RelevanceCheck(
            new Judge(
                (system, user) -> {
                  prompts.add(user);
                  return "YES";
                }));
    check.run(evalCase(), answerOf("Safari 14 is supported"), STATE);
    assertEquals(1, prompts.size());
    assertTrue(prompts.get(0).contains("Which browsers are supported?"), prompts.get(0));
  }

  @Test
  void aJudgeErrorPropagatesSoTheRunnerRecordsItAsACheckError() {
    RelevanceCheck check = new RelevanceCheck(new Judge((system, user) -> "Maybe"));
    assertThrows(
        IllegalStateException.class, () -> check.run(evalCase(), answerOf("a claim"), STATE));
  }
}
```

Append to `ChecksTest.java` (inside the class):

```java
  @Test
  void relevanceIsRegisteredLastAndIsAdvisory() {
    var registered =
        Checks.registered(new KnowledgeBase(List.of()), new Judge((system, user) -> "YES"));
    var last = registered.get(registered.size() - 1);
    assertEquals("Relevance", last.check().name());
    assertFalse(last.gating());
    for (var others : registered.subList(0, registered.size() - 1)) {
      assertTrue(others.gating(), others.check().name());
    }
  }
```

- [ ] **Step 2: Run them to see them fail**

Run: `mvn -B -q -pl eval -am test -Dtest='RelevanceCheckTest,ChecksTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAIL, `cannot find symbol: class RelevanceCheck`.

- [ ] **Step 3: Implement**

Create `eval/src/main/java/eval/checks/RelevanceCheck.java`:

```java
package eval.checks;

import eval.*;
import java.util.*;

/**
 * Is each claim pertinent to the question, not true-but-off-topic padding? One judge call per
 * claim. Advisory (D12): the harness registers it with gating=false, so a failure here is shown and
 * counted but never fails a case. A refusal has no claims and costs no call.
 */
public final class RelevanceCheck implements Check {
  private final Judge judge;

  public RelevanceCheck(Judge judge) {
    this.judge = judge;
  }

  @Override
  public String name() {
    return "Relevance";
  }

  @Override
  public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
    List<Claim> claims = answer.claims() == null ? List.of() : answer.claims();
    List<String> offTopic = new ArrayList<>();
    for (Claim claim : claims) {
      if (!judge.relevant(evalCase.question(), claim.claim())) {
        offTopic.add("\"" + claim.claim() + "\"");
      }
    }
    if (offTopic.isEmpty()) {
      return CheckResult.ok();
    }
    return CheckResult.fail("off-topic claim(s): " + String.join("; ", offTopic));
  }
}
```

In `Checks.java`, add as the last entry of the list (after Source):

```java
        new Registered(new SourceCheck(), true),
        new Registered(new RelevanceCheck(judge), false));
```

(the `Source` line loses its closing `);`). Update the class comment nothing else.

- [ ] **Step 4: Fix the one existing test whose judge-call count changes**

In `HarnessTest.negatedClaimWithAllKeywordsFailsCoverageEndToEnd` the fake judge now also gets a Relevance call on the one claim. Change:

```java
    assertEquals(
        2,
        judgeCalls[0]); // Coverage's confirm call plus Groundedness's call on the uncovered claim
```

to

```java
    assertEquals(
        3,
        judgeCalls[0]); // Coverage's confirm call, Groundedness on the uncovered claim, Relevance
```

- [ ] **Step 5: Run the whole eval suite**

Run: `mvn -B -q test`
Expected: all PASS. If another test fails because it counts judge calls or lists checks, the change is only the extra Relevance call/entry; update that expectation and note why in the commit message. Do not loosen a test.

- [ ] **Step 6: Commit**

```bash
git add eval/src/main/java/eval/checks/RelevanceCheck.java eval/src/main/java/eval/checks/Checks.java \
  eval/src/test/java/eval/checks/RelevanceCheckTest.java eval/src/test/java/eval/checks/ChecksTest.java \
  eval/src/test/java/eval/HarnessTest.java
git commit -m "feat: advisory Relevance check, one judge call per claim (unit 17)"
```

---

### Task 3: Count advisory flags in the summary

**Files:**
- Modify: `eval/src/main/java/eval/SuiteReport.java`, `eval/src/main/java/eval/ConsoleReport.java`
- Modify: `eval/src/test/java/eval/SuiteReportTest.java`, `eval/src/test/java/eval/ConsoleReportTest.java`, `eval/src/test/java/eval/HarnessTest.java`

**Interfaces:**
- Consumes: `SuiteReport.checks()` (`List<CheckInfo>`; `CheckInfo(String name, boolean gating)`), `CaseResult.checks()` (`List<CheckOutcome>`; `CheckOutcome(String check, boolean passed, String reason)`).
- Produces: `@JsonProperty("advisoryFlags") Map<String, Long> SuiteReport.advisoryFlags()` — for each non-gating registered check, in registration order, the number of cases where it failed (a `check error` counts). Console prints one line per advisory check.

- [ ] **Step 1: Write the failing tests**

Append to `SuiteReportTest.java` (inside the class):

```java
  private static CaseResult resultWith(String id, boolean passed, CheckOutcome... outcomes) {
    return new CaseResult(
        id,
        "q",
        "single-source",
        null,
        new Expected(false, List.of()),
        passed,
        null,
        null,
        List.of(outcomes));
  }

  @Test
  void advisoryFlagsCountCasesWhereAnAdvisoryCheckFailedAndNeverTouchThePassRate() {
    SuiteReport report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(new CheckInfo("Refusal", true), new CheckInfo("Relevance", false)),
            SuiteReport.Calibration.SKIPPED,
            List.of(
                resultWith(
                    "a", true, new CheckOutcome("Refusal", true, null), new CheckOutcome("Relevance", false, "off-topic")),
                resultWith(
                    "b", true, new CheckOutcome("Refusal", true, null), new CheckOutcome("Relevance", true, null)),
                resultWith(
                    "c", true, new CheckOutcome("Refusal", true, null), new CheckOutcome("Relevance", false, "check error: boom"))));
    assertEquals(java.util.Map.of("Relevance", 2L), report.advisoryFlags());
    assertEquals(1.0, report.passRate());
    assertEquals(0, report.exitCode());
  }

  @Test
  void aGatingCheckIsNeverListedAsAdvisory() {
    SuiteReport report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(new CheckInfo("Refusal", true)),
            SuiteReport.Calibration.SKIPPED,
            List.of(resultWith("a", false, new CheckOutcome("Refusal", false, "bad"))));
    assertTrue(report.advisoryFlags().isEmpty());
  }
```

Append to `ConsoleReportTest.java` (inside the class):

```java
  @Test
  void printsHowManyCasesEachAdvisoryCheckFlaggedBeforeThePassRate() {
    CaseResult flagged =
        new CaseResult(
            "a",
            "q",
            "single-source",
            null,
            new Expected(false, List.of()),
            true,
            null,
            null,
            List.of(new CheckOutcome("Relevance", false, "off-topic claim(s): \"x\"")));
    SuiteReport report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(new CheckInfo("Relevance", false)),
            SuiteReport.Calibration.SKIPPED,
            List.of(flagged, result("b", "single-source", null, true)));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ConsoleReport.print(report, new PrintStream(buffer));
    String output = buffer.toString();
    assertTrue(output.contains("Relevance (advisory): off-topic claim(s)"), output);
    assertTrue(output.contains("Advisory Relevance (never fails a case): flagged on 1 of 2 case(s)"), output);
    assertTrue(output.indexOf("Advisory Relevance") < output.indexOf("Pass rate:"), output);
    assertTrue(output.contains("RESULT: OK"), output);
  }
```

- [ ] **Step 2: Run them to see them fail**

Run: `mvn -B -q -pl eval -am test -Dtest='SuiteReportTest,ConsoleReportTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAIL, `cannot find symbol: method advisoryFlags()`.

- [ ] **Step 3: Implement**

In `SuiteReport.java`, after `isGating`:

```java
  /**
   * For each advisory check (D12), in registration order, the number of cases where it failed. A
   * {@code check error} counts, so a broken advisory judge is visible. Never part of pass/fail.
   */
  @JsonProperty("advisoryFlags")
  public Map<String, Long> advisoryFlags() {
    Map<String, Long> flags = new LinkedHashMap<>();
    for (CheckInfo info : checks) {
      if (info.gating()) {
        continue;
      }
      long flaggedCases =
          cases.stream()
              .filter(
                  caseResult ->
                      caseResult.checks().stream()
                          .anyMatch(
                              outcome ->
                                  outcome.check().equals(info.name()) && !outcome.passed()))
              .count();
      flags.put(info.name(), flaggedCases);
    }
    return flags;
  }
```

In `ConsoleReport.print`, call `printAdvisory(report, out);` right after `printRollups(report, out);`, and add:

```java
  private static void printAdvisory(SuiteReport report, PrintStream out) {
    report
        .advisoryFlags()
        .forEach(
            (check, flaggedCases) ->
                out.printf(
                    "Advisory %s (never fails a case): flagged on %d of %d case(s)%n",
                    check, flaggedCases, report.cases().size()));
  }
```

- [ ] **Step 4: Add the end-to-end proof that a Relevance NO cannot fail a case**

Append to `HarnessTest.java` (inside the class; use the same imports the file already has, plus `java.io.ByteArrayOutputStream`/`PrintStream` which it already uses):

```java
  @Test
  void aRelevanceNoIsReportedButTheCaseAndTheRunStillPass() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: q\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[0],
            root,
            new PrintStream(buf),
            model -> (system, user) -> system.contains("pertinent") ? "NO" : "YES");
    String output = buf.toString();
    assertEquals(0, code, output);
    assertTrue(output.contains("PASS") && output.contains("c1"), output);
    assertTrue(output.contains("Relevance (advisory): off-topic claim(s)"), output);
    assertTrue(output.contains("flagged on 1 of 1 case(s)"), output);
    assertTrue(output.contains("RESULT: OK"), output);
  }
```

- [ ] **Step 5: Run the whole suite**

Run: `mvn -B -q test`
Expected: all PASS.

- [ ] **Step 6: Commit**

```bash
git add eval/src/main/java/eval/SuiteReport.java eval/src/main/java/eval/ConsoleReport.java \
  eval/src/test/java/eval/SuiteReportTest.java eval/src/test/java/eval/ConsoleReportTest.java \
  eval/src/test/java/eval/HarnessTest.java
git commit -m "feat: count advisory flags in the console and JSON report (unit 17)"
```

---

### Task 4: `OpenRouterLlm` returns the token counts it receives

**Files:**
- Create: `llm/src/main/java/llm/Completion.java`
- Modify: `llm/src/main/java/llm/Llm.java`, `llm/src/main/java/llm/OpenRouterLlm.java`
- Modify: `llm/src/test/java/llm/OpenRouterLlmTest.java`

**Interfaces:**
- Consumes: the OpenAI-compatible reply OpenRouter already sends: `{"choices":[{"message":{"content":...}}],"usage":{"prompt_tokens":N,"completion_tokens":M}}`.
- Produces:
  - `public record Completion(String text, long promptTokens, long completionTokens)`
  - `Llm` keeps its single abstract `String complete(String system, String user)` (tests use lambdas) and gains `default Completion completeWithUsage(String system, String user)` returning the text with 0 tokens.
  - `OpenRouterLlm.completeWithUsage` returns the real counts; `complete` returns `completeWithUsage(...).text()`. Package-private `static Completion parseCompletion(String json)`; `parseText` stays and returns `parseCompletion(json).text()`.

- [ ] **Step 1: Write the failing tests**

Append to `OpenRouterLlmTest.java` (inside the class):

```java
  @Test
  void parseCompletionReadsTheTokenCounts() {
    Completion completion =
        OpenRouterLlm.parseCompletion(
            "{\"choices\":[{\"message\":{\"content\":\"hi\"}}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3}}");
    assertEquals("hi", completion.text());
    assertEquals(12, completion.promptTokens());
    assertEquals(3, completion.completionTokens());
  }

  @Test
  void parseCompletionWithNoUsageBlockCountsZeroTokensAndDoesNotFail() {
    Completion completion =
        OpenRouterLlm.parseCompletion("{\"choices\":[{\"message\":{\"content\":\"hi\"}}]}");
    assertEquals("hi", completion.text());
    assertEquals(0, completion.promptTokens());
    assertEquals(0, completion.completionTokens());
  }

  @Test
  void parseCompletionStillSurfacesAnErrorBodyOn200() {
    assertThrows(
        IllegalStateException.class,
        () -> OpenRouterLlm.parseCompletion("{\"error\":{\"message\":\"boom\"}}"));
  }

  @Test
  void aPlainLlmReportsItsTextWithZeroTokens() {
    Llm llm = (system, user) -> "x";
    assertEquals(new Completion("x", 0, 0), llm.completeWithUsage("s", "u"));
  }
```

- [ ] **Step 2: Run them to see them fail**

Run: `mvn -B -q -pl llm test`
Expected: compilation FAIL, `cannot find symbol: class Completion`.

- [ ] **Step 3: Implement**

Create `llm/src/main/java/llm/Completion.java`:

```java
package llm;

/** A reply and the token counts the provider reported for it (0 when it reported none). */
public record Completion(String text, long promptTokens, long completionTokens) {}
```

Replace `llm/src/main/java/llm/Llm.java` with:

```java
package llm;

public interface Llm {
    String complete(String system, String user);

    /** The same call, plus the token counts the provider reported; 0 for an Llm that reports none. */
    default Completion completeWithUsage(String system, String user) {
        return new Completion(complete(system, user), 0, 0);
    }
}
```

In `OpenRouterLlm.java`:

1. Rename the existing `complete` method to `completeWithUsage`, change its return type to `Completion`, and change its last line `return parseText(response.body());` to `return parseCompletion(response.body());`. Keep `@Override`.
2. Add, directly above it:

```java
  @Override
  public String complete(String system, String user) {
    return completeWithUsage(system, user).text();
  }
```

3. Replace `parseText` with:

```java
  static String parseText(String json) {
    return parseCompletion(json).text();
  }

  static Completion parseCompletion(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      // OpenRouter can answer HTTP 200 with an error object instead of choices.
      if (root.has("error")) {
        throw new IllegalStateException("OpenRouter API error: " + root.get("error"));
      }
      String text = root.get("choices").get(0).get("message").get("content").asText();
      JsonNode usage = root.path("usage");
      return new Completion(
          text, usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0));
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Unexpected OpenRouter response: " + json, e);
    }
  }
```

- [ ] **Step 4: Run the whole build**

Run: `mvn -B -q test`
Expected: all PASS (the `assistant` module still compiles: it uses `Llm.complete` only).

- [ ] **Step 5: Commit**

```bash
git add llm/src/main/java/llm/Completion.java llm/src/main/java/llm/Llm.java \
  llm/src/main/java/llm/OpenRouterLlm.java llm/src/test/java/llm/OpenRouterLlmTest.java
git commit -m "feat: OpenRouterLlm returns the token counts from the reply (unit 20)"
```

---

### Task 5: The meter, the metered LLM and the `judgePricing` setting

**Files:**
- Create: `eval/src/main/java/eval/UsageMeter.java`, `eval/src/main/java/eval/MeteredLlm.java`
- Modify: `eval/src/main/java/eval/EvalConfig.java`, `eval/config.yaml`
- Modify: `eval/src/main/java/eval/SuiteReport.java` (the `Usage` record only; wiring is Task 6)
- Create: `eval/src/test/java/eval/UsageMeterTest.java`, `eval/src/test/java/eval/MeteredLlmTest.java`
- Modify: `eval/src/test/java/eval/EvalConfigTest.java`

**Interfaces:**
- Consumes: `llm.Llm`, `llm.Completion` (Task 4).
- Produces:
  - `SuiteReport.Usage` (public record): `Usage(long wallClockMillis, int assistantCalls, long assistantMillis, List<CheckUsage> byCheck, Double judgeCostUsd)` with nested `record CheckUsage(String check, int calls, long promptTokens, long completionTokens)`, and derived `@JsonProperty` accessors `judgeCalls()` (int), `judgePromptTokens()` (long), `judgeCompletionTokens()` (long), all sums over `byCheck`. `judgeCostUsd` is null when no prices are configured.
  - `UsageMeter` (package-private): `record Pricing(double inputPerMillion, double outputPerMillion)` with `double cost(long promptTokens, long completionTokens)`; `void setCheck(String check)` (null = outside any check); `void assistantCall(long millis)`; `void judgeCall(long promptTokens, long completionTokens)`; `SuiteReport.Usage usage(long wallClockMillis, Pricing pricing)` (pricing may be null).
  - `MeteredLlm implements Llm` (package-private): `MeteredLlm(Llm delegate, UsageMeter meter)`; `complete` calls `delegate.completeWithUsage` and records the call.
  - `UsageMeter.Pricing EvalConfig.judgePricing()` — null when the key is absent; throws `IllegalArgumentException` naming the file when malformed. `judgePricing` is a valid `--<key>` setting.

- [ ] **Step 1: Write the failing tests**

Create `eval/src/test/java/eval/UsageMeterTest.java`:

```java
package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class UsageMeterTest {
  @Test
  void judgeCallsAreCountedPerTheCheckThatWasRunning() {
    UsageMeter meter = new UsageMeter();
    meter.setCheck("Coverage");
    meter.judgeCall(100, 5);
    meter.judgeCall(200, 10);
    meter.setCheck("Relevance");
    meter.judgeCall(50, 1);
    SuiteReport.Usage usage = meter.usage(1000, null);
    assertEquals(
        List.of(
            new SuiteReport.Usage.CheckUsage("Coverage", 2, 300, 15),
            new SuiteReport.Usage.CheckUsage("Relevance", 1, 50, 1)),
        usage.byCheck());
    assertEquals(3, usage.judgeCalls());
    assertEquals(350, usage.judgePromptTokens());
    assertEquals(16, usage.judgeCompletionTokens());
  }

  @Test
  void aCallOutsideAnyCheckIsStillCounted() {
    UsageMeter meter = new UsageMeter();
    meter.setCheck("Coverage");
    meter.setCheck(null);
    meter.judgeCall(10, 1);
    SuiteReport.Usage usage = meter.usage(0, null);
    assertEquals(1, usage.judgeCalls());
    assertEquals("outside checks", usage.byCheck().get(0).check());
  }

  @Test
  void assistantCallsAndTimeAreSummed() {
    UsageMeter meter = new UsageMeter();
    meter.assistantCall(1500);
    meter.assistantCall(500);
    SuiteReport.Usage usage = meter.usage(9000, null);
    assertEquals(2, usage.assistantCalls());
    assertEquals(2000, usage.assistantMillis());
    assertEquals(9000, usage.wallClockMillis());
  }

  @Test
  void costIsTokensTimesThePricePerMillion() {
    UsageMeter meter = new UsageMeter();
    meter.setCheck("Coverage");
    meter.judgeCall(1_000_000, 1_000_000);
    SuiteReport.Usage usage = meter.usage(0, new UsageMeter.Pricing(5.0, 25.0));
    assertEquals(30.0, usage.judgeCostUsd(), 1e-9);
  }

  @Test
  void withoutPricesTheCostIsNullNotZero() {
    UsageMeter meter = new UsageMeter();
    meter.judgeCall(100, 5);
    assertNull(meter.usage(0, null).judgeCostUsd());
  }

  @Test
  void aRunWithNoCallsIsAllZeroesWithoutFailing() {
    SuiteReport.Usage usage = new UsageMeter().usage(0, new UsageMeter.Pricing(5.0, 25.0));
    assertEquals(0, usage.judgeCalls());
    assertEquals(0.0, usage.judgeCostUsd(), 1e-9);
    assertTrue(usage.byCheck().isEmpty());
  }
}
```

Create `eval/src/test/java/eval/MeteredLlmTest.java`:

```java
package eval;

import static org.junit.jupiter.api.Assertions.*;

import llm.Completion;
import llm.Llm;
import org.junit.jupiter.api.Test;

class MeteredLlmTest {
  private static final Llm REPORTING =
      new Llm() {
        @Override
        public String complete(String system, String user) {
          return "unused";
        }

        @Override
        public Completion completeWithUsage(String system, String user) {
          return new Completion("YES", 100, 5);
        }
      };

  @Test
  void returnsTheTextAndRecordsTheCallUnderTheRunningCheck() {
    UsageMeter meter = new UsageMeter();
    MeteredLlm metered = new MeteredLlm(REPORTING, meter);
    meter.setCheck("Coverage");
    assertEquals("YES", metered.complete("s", "u"));
    SuiteReport.Usage usage = meter.usage(0, null);
    assertEquals(1, usage.judgeCalls());
    assertEquals(100, usage.judgePromptTokens());
    assertEquals(5, usage.judgeCompletionTokens());
    assertEquals("Coverage", usage.byCheck().get(0).check());
  }

  @Test
  void anLlmThatReportsNoTokensStillCountsTheCall() {
    UsageMeter meter = new UsageMeter();
    new MeteredLlm((system, user) -> "YES", meter).complete("s", "u");
    SuiteReport.Usage usage = meter.usage(0, null);
    assertEquals(1, usage.judgeCalls());
    assertEquals(0, usage.judgePromptTokens());
  }

  @Test
  void aFailedCallIsNotCountedAndTheFailureReachesTheCaller() {
    UsageMeter meter = new UsageMeter();
    MeteredLlm metered =
        new MeteredLlm(
            (system, user) -> {
              throw new IllegalStateException("boom");
            },
            meter);
    assertThrows(IllegalStateException.class, () -> metered.complete("s", "u"));
    assertEquals(0, meter.usage(0, null).judgeCalls());
  }
}
```

Append to `EvalConfigTest.java` (inside the class; it has `write(String extra)`, `BASE` and `dir`):

```java
  @Test
  void judgePricingIsNullWhenNotSet() throws Exception {
    assertNull(EvalConfig.loadFile(write(""), null).judgePricing());
  }

  @Test
  void judgePricingReadsBothPrices() throws Exception {
    EvalConfig config =
        EvalConfig.loadFile(
            write("judgePricing:\n  inputPerMillion: 5\n  outputPerMillion: 25.5\n"), null);
    assertEquals(new UsageMeter.Pricing(5.0, 25.5), config.judgePricing());
  }

  @Test
  void judgePricingCanBeOverriddenOnTheCommandLineWithDots() throws Exception {
    EvalConfig config =
        EvalConfig.loadFile(
            write("judgePricing:\n  inputPerMillion: 5\n  outputPerMillion: 25\n"),
            java.util.Map.of("judgePricing.outputPerMillion", "10"));
    assertEquals(new UsageMeter.Pricing(5.0, 10.0), config.judgePricing());
    assertTrue(EvalConfig.isSetting("judgePricing.inputPerMillion"));
  }

  @Test
  void aMalformedJudgePricingIsAConfigErrorNamingTheFile() throws Exception {
    for (String bad :
        new String[] {
          "judgePricing: 5\n",
          "judgePricing:\n  inputPerMillion: 5\n",
          "judgePricing:\n  inputPerMillion: -1\n  outputPerMillion: 25\n",
          "judgePricing:\n  inputPerMillion: cheap\n  outputPerMillion: 25\n"
        }) {
      EvalConfig config = EvalConfig.loadFile(write(bad), null);
      IllegalArgumentException error =
          assertThrows(IllegalArgumentException.class, config::judgePricing, bad);
      assertTrue(error.getMessage().contains("team.yaml"), error.getMessage());
      assertTrue(error.getMessage().contains("judgePricing"), error.getMessage());
    }
  }
```

- [ ] **Step 2: Run them to see them fail**

Run: `mvn -B -q -pl eval -am test -Dtest='UsageMeterTest,MeteredLlmTest,EvalConfigTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAIL, `cannot find symbol: class UsageMeter`.

- [ ] **Step 3: Implement the `Usage` record**

In `SuiteReport.java`, add next to the other nested records (e.g. after `Calibration`):

```java
  /**
   * What the run measured (D17). The judge is metered through its {@code Llm}; the assistant is a
   * black box over HTTP (D28), so only its calls and time are known, never its tokens.
   * {@code judgeCostUsd} is tokens times the configured prices, null when none are configured.
   */
  public record Usage(
      long wallClockMillis,
      int assistantCalls,
      long assistantMillis,
      List<CheckUsage> byCheck,
      Double judgeCostUsd) {
    public record CheckUsage(String check, int calls, long promptTokens, long completionTokens) {}

    @JsonProperty("judgeCalls")
    public int judgeCalls() {
      return byCheck.stream().mapToInt(CheckUsage::calls).sum();
    }

    @JsonProperty("judgePromptTokens")
    public long judgePromptTokens() {
      return byCheck.stream().mapToLong(CheckUsage::promptTokens).sum();
    }

    @JsonProperty("judgeCompletionTokens")
    public long judgeCompletionTokens() {
      return byCheck.stream().mapToLong(CheckUsage::completionTokens).sum();
    }
  }
```

- [ ] **Step 4: Implement the meter and the metered LLM**

Create `eval/src/main/java/eval/UsageMeter.java`:

```java
package eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counts what a run spends (D17): judge calls and tokens per check, assistant calls and time. It
 * only counts calls that came back, so the numbers are a floor on spend: a call that failed
 * carries no usage from the provider.
 */
final class UsageMeter {
  /** USD per million tokens, from the {@code judgePricing} setting. */
  record Pricing(double inputPerMillion, double outputPerMillion) {
    double cost(long promptTokens, long completionTokens) {
      return (promptTokens * inputPerMillion + completionTokens * outputPerMillion) / 1_000_000.0;
    }
  }

  private static final String OUTSIDE_CHECKS = "outside checks";

  private final Map<String, long[]> judgeByCheck = new LinkedHashMap<>(); // {calls, prompt, completion}
  private int assistantCalls;
  private long assistantMillis;
  // ponytail: one current check, right while cases run one at a time; parallel cases need a
  // ThreadLocal here.
  private String currentCheck = OUTSIDE_CHECKS;

  /** Names the check whose judge calls are counted next; null means no check is running. */
  void setCheck(String check) {
    currentCheck = check == null ? OUTSIDE_CHECKS : check;
  }

  void assistantCall(long millis) {
    assistantCalls++;
    assistantMillis += millis;
  }

  void judgeCall(long promptTokens, long completionTokens) {
    long[] totals = judgeByCheck.computeIfAbsent(currentCheck, check -> new long[3]);
    totals[0]++;
    totals[1] += promptTokens;
    totals[2] += completionTokens;
  }

  /** The measurements so far; {@code pricing} may be null, which leaves the cost null. */
  SuiteReport.Usage usage(long wallClockMillis, Pricing pricing) {
    List<SuiteReport.Usage.CheckUsage> byCheck = new ArrayList<>();
    judgeByCheck.forEach(
        (check, totals) ->
            byCheck.add(
                new SuiteReport.Usage.CheckUsage(check, (int) totals[0], totals[1], totals[2])));
    long promptTokens = byCheck.stream().mapToLong(SuiteReport.Usage.CheckUsage::promptTokens).sum();
    long completionTokens =
        byCheck.stream().mapToLong(SuiteReport.Usage.CheckUsage::completionTokens).sum();
    Double cost = pricing == null ? null : pricing.cost(promptTokens, completionTokens);
    return new SuiteReport.Usage(wallClockMillis, assistantCalls, assistantMillis, byCheck, cost);
  }
}
```

Create `eval/src/main/java/eval/MeteredLlm.java`:

```java
package eval;

import llm.Completion;
import llm.Llm;

/** Passes every call to the real {@code Llm} and records it in the meter under the running check. */
final class MeteredLlm implements Llm {
  private final Llm delegate;
  private final UsageMeter meter;

  MeteredLlm(Llm delegate, UsageMeter meter) {
    this.delegate = delegate;
    this.meter = meter;
  }

  @Override
  public String complete(String system, String user) {
    Completion completion = delegate.completeWithUsage(system, user);
    meter.judgeCall(completion.promptTokens(), completion.completionTokens());
    return completion.text();
  }
}
```

- [ ] **Step 5: Implement the setting**

In `EvalConfig.java`: add `"judgePricing"` to the `SETTINGS` set (after `"judgeModel"`), and add this method after `requireJudgeModel()`:

```java
  /**
   * The {@code judgePricing:} block (USD per million tokens), or null when it is not set. A block
   * that is set must be complete, so a typo cannot silently turn the estimate into zero.
   */
  UsageMeter.Pricing judgePricing() {
    Object block = raw.get("judgePricing");
    if (block == null) {
      return null;
    }
    if (block instanceof Map<?, ?> pricing
        && pricing.get("inputPerMillion") instanceof Number input
        && pricing.get("outputPerMillion") instanceof Number output
        && input.doubleValue() >= 0
        && output.doubleValue() >= 0) {
      return new UsageMeter.Pricing(input.doubleValue(), output.doubleValue());
    }
    throw new IllegalArgumentException(
        fileName
            + ": 'judgePricing' needs inputPerMillion and outputPerMillion, each a number of at"
            + " least 0 (USD per million tokens)");
  }
```

In `eval/config.yaml`, after the `judgeModel:` line add:

```yaml
# What the judge costs, USD per million tokens, so the report can estimate cost from measured tokens (D17).
# Prices of judgeModel on its OpenRouter route (D40: azure/global). Not set: tokens are reported, cost is not estimated.
judgePricing:
  inputPerMillion: 5.0
  outputPerMillion: 25.0
```

- [ ] **Step 6: Run the tests**

Run: `mvn -B -q test`
Expected: all PASS.

- [ ] **Step 7: Commit**

```bash
git add eval/src/main/java/eval/UsageMeter.java eval/src/main/java/eval/MeteredLlm.java \
  eval/src/main/java/eval/EvalConfig.java eval/src/main/java/eval/SuiteReport.java eval/config.yaml \
  eval/src/test/java/eval/UsageMeterTest.java eval/src/test/java/eval/MeteredLlmTest.java \
  eval/src/test/java/eval/EvalConfigTest.java
git commit -m "feat: usage meter, metered LLM and judgePricing setting (unit 20)"
```

---

### Task 6: The report carries and prints the usage

**Files:**
- Modify: `eval/src/main/java/eval/SuiteReport.java`, `eval/src/main/java/eval/ConsoleReport.java`
- Modify: `eval/src/test/java/eval/SuiteReportTest.java`, `eval/src/test/java/eval/ConsoleReportTest.java`

**Interfaces:**
- Consumes: `SuiteReport.Usage` and `CheckUsage` (Task 5).
- Produces: `SuiteReport` gains a last component `Usage usage` (null = not measured). The existing 7-argument and 6-argument constructors stay and pass `null`, so no existing call site changes. `ConsoleReport.print` prints a "Cost and time" block between the advisory lines and `Pass rate:` when `usage` is not null. The JSON report gets a `usage` object.

- [ ] **Step 1: Write the failing tests**

Append to `ConsoleReportTest.java` (inside the class):

```java
  private static SuiteReport.Usage usage(Double cost) {
    return new SuiteReport.Usage(
        65_400,
        2,
        1_500,
        List.of(
            new SuiteReport.Usage.CheckUsage("Coverage", 3, 3000, 30),
            new SuiteReport.Usage.CheckUsage("Relevance", 2, 1000, 10)),
        cost);
  }

  private static String printWithUsage(SuiteReport.Usage usage) {
    SuiteReport report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(),
            SuiteReport.Calibration.SKIPPED,
            List.of(result("a", "single-source", null, true)),
            null,
            usage);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ConsoleReport.print(report, new PrintStream(buffer));
    return buffer.toString();
  }

  @Test
  void printsWallClockAssistantAndJudgeUsageWithAnEstimatedCost() {
    String output = printWithUsage(usage(0.02));
    assertTrue(output.contains("Cost and time"), output);
    assertTrue(output.contains("wall-clock: 65.4 s"), output);
    assertTrue(output.contains("assistant: 2 calls, 1.5 s"), output);
    assertTrue(
        output.contains("judge: 5 calls, 4000 prompt + 40 completion tokens, est. $0.0200"),
        output);
    assertTrue(output.matches("(?s).*Coverage\\s+3 calls, 3000 \\+ 30 tokens.*"), output);
    assertTrue(output.indexOf("Cost and time") < output.indexOf("Pass rate:"), output);
  }

  @Test
  void withNoPricesItSaysTheCostWasNotEstimatedInsteadOfPrintingZero() {
    String output = printWithUsage(usage(null));
    assertTrue(output.contains("cost not estimated"), output);
    assertFalse(output.contains("$0.0000"), output);
  }

  @Test
  void aReportWithNoUsageLeavesTheBlockOut() {
    assertFalse(printWithUsage(null).contains("Cost and time"));
  }
```

Append to `SuiteReportTest.java` (inside the class):

```java
  @Test
  void usageIsInTheJsonWithTheDerivedTotals() throws Exception {
    SuiteReport.Usage usage =
        new SuiteReport.Usage(
            1000,
            2,
            500,
            List.of(
                new SuiteReport.Usage.CheckUsage("Coverage", 3, 300, 30),
                new SuiteReport.Usage.CheckUsage("Relevance", 2, 100, 10)),
            null);
    SuiteReport report =
        new SuiteReport(
            "r", "http://x", 0.90, List.of(), SuiteReport.Calibration.SKIPPED, List.of(), null, usage);
    var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(
        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(report));
    assertEquals(5, json.get("usage").get("judgeCalls").asInt());
    assertEquals(400, json.get("usage").get("judgePromptTokens").asLong());
    assertEquals(40, json.get("usage").get("judgeCompletionTokens").asLong());
    assertTrue(json.get("usage").get("judgeCostUsd").isNull());
    assertEquals("Coverage", json.get("usage").get("byCheck").get(0).get("check").asText());
  }
```

- [ ] **Step 2: Run them to see them fail**

Run: `mvn -B -q -pl eval -am test -Dtest='ConsoleReportTest,SuiteReportTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAIL, no 8-argument `SuiteReport` constructor.

- [ ] **Step 3: Implement**

In `SuiteReport.java`, change the record header and constructors:

```java
public record SuiteReport(
    String runId,
    String endpoint,
    double passFloor,
    List<CheckInfo> checks,
    Calibration calibration,
    List<CaseResult> cases,
    Comparison baseline,
    Usage usage) {

  public SuiteReport(
      String runId,
      String endpoint,
      double passFloor,
      List<CheckInfo> checks,
      Calibration calibration,
      List<CaseResult> cases,
      Comparison baseline) {
    this(runId, endpoint, passFloor, checks, calibration, cases, baseline, null);
  }

  public SuiteReport(
      String runId,
      String endpoint,
      double passFloor,
      List<CheckInfo> checks,
      Calibration calibration,
      List<CaseResult> cases) {
    this(runId, endpoint, passFloor, checks, calibration, cases, null, null);
  }
```

(the old 7-argument canonical header becomes the second constructor above; the old 6-argument constructor is replaced by the third).

In `ConsoleReport.java`: add `import java.util.Locale;`, call `printUsage(report.usage(), out);` right after `printAdvisory(report, out);`, and add:

```java
  private static void printUsage(SuiteReport.Usage usage, PrintStream out) {
    if (usage == null) {
      return;
    }
    out.println("Cost and time");
    out.println(String.format(Locale.ROOT, "  wall-clock: %.1f s", usage.wallClockMillis() / 1000.0));
    out.println(
        String.format(
            Locale.ROOT,
            "  assistant: %d calls, %.1f s (its tokens are not visible over HTTP)",
            usage.assistantCalls(),
            usage.assistantMillis() / 1000.0));
    String cost =
        usage.judgeCostUsd() == null
            ? "cost not estimated (set judgePricing in the config)"
            : String.format(Locale.ROOT, "est. $%.4f", usage.judgeCostUsd());
    out.println(
        String.format(
            Locale.ROOT,
            "  judge: %d calls, %d prompt + %d completion tokens, %s",
            usage.judgeCalls(),
            usage.judgePromptTokens(),
            usage.judgeCompletionTokens(),
            cost));
    for (SuiteReport.Usage.CheckUsage byCheck : usage.byCheck()) {
      out.println(
          String.format(
              Locale.ROOT,
              "    %-20s %d calls, %d + %d tokens",
              byCheck.check(),
              byCheck.calls(),
              byCheck.promptTokens(),
              byCheck.completionTokens()));
    }
  }
```

- [ ] **Step 4: Run the whole suite**

Run: `mvn -B -q test`
Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/java/eval/SuiteReport.java eval/src/main/java/eval/ConsoleReport.java \
  eval/src/test/java/eval/SuiteReportTest.java eval/src/test/java/eval/ConsoleReportTest.java
git commit -m "feat: report usage in the console and the JSON (unit 20)"
```

---

### Task 7: Wire the meter into the run

**Files:**
- Modify: `eval/src/main/java/eval/CaseRunner.java`, `eval/src/main/java/eval/Harness.java`
- Modify: `eval/src/test/java/eval/CaseRunnerTest.java`, `eval/src/test/java/eval/HarnessTest.java`

**Interfaces:**
- Consumes: `UsageMeter` (`setCheck`, `assistantCall`, `usage`), `MeteredLlm(Llm, UsageMeter)`, `EvalConfig.judgePricing()`, `SuiteReport(..., Comparison baseline, Usage usage)`.
- Produces: `CaseRunner(Assistant, List<Registered>, UsageMeter)`; the existing 2-argument constructor stays and uses a private meter nobody reads. A finished run's console output and JSON contain the usage block.

- [ ] **Step 1: Write the failing tests**

Append to `CaseRunnerTest.java` (inside the class; add `import java.util.concurrent.atomic.AtomicReference;` if missing):

```java
  @Test
  void everyAssistantCallIsCountedIncludingOnesThatFail() {
    UsageMeter meter = new UsageMeter();
    Assistant failing =
        (question, runId) -> {
          throw new IOException("HTTP 500");
        };
    new CaseRunner(answering(), List.of(), meter).run(evalCase("a"), "run");
    new CaseRunner(failing, List.of(), meter).run(evalCase("b"), "run");
    assertEquals(2, meter.usage(0, null).assistantCalls());
  }

  @Test
  void theMeterKnowsWhichCheckIsRunningAndForgetsItAfterwards() {
    UsageMeter meter = new UsageMeter();
    AtomicReference<String> insideFirst = new AtomicReference<>();
    Check probing =
        new Check() {
          @Override
          public String name() {
            return "probe";
          }

          @Override
          public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
            meter.judgeCall(10, 1);
            insideFirst.set("ran");
            return CheckResult.ok();
          }
        };
    new CaseRunner(answering(), List.of(new Registered(probing, true)), meter)
        .run(evalCase("a"), "run");
    meter.judgeCall(1, 1); // after the case: not attributed to "probe"
    SuiteReport.Usage usage = meter.usage(0, null);
    assertEquals("ran", insideFirst.get());
    assertEquals("probe", usage.byCheck().get(0).check());
    assertEquals(1, usage.byCheck().get(0).calls());
    assertEquals("outside checks", usage.byCheck().get(1).check());
  }
```

Append to `HarnessTest.java` (inside the class; add `import llm.Completion; import llm.Llm; import com.fasterxml.jackson.databind.JsonNode; import com.fasterxml.jackson.databind.ObjectMapper;` if missing):

```java
  /** A judge that says YES and reports 100 prompt and 5 completion tokens for every call. */
  private static Llm reportingYes() {
    return new Llm() {
      @Override
      public String complete(String system, String user) {
        return "YES";
      }

      @Override
      public Completion completeWithUsage(String system, String user) {
        return new Completion("YES", 100, 5);
      }
    };
  }

  private JsonNode onlyReport() throws Exception {
    try (var files = Files.list(root.resolve("caseResults"))) {
      Path report = files.filter(path -> path.toString().endsWith(".json")).findFirst().orElseThrow();
      return new ObjectMapper().readTree(report.toFile());
    }
  }

  private static final String ONE_ANSWER_CASE =
      "- id: c1\n"
          + "  question: q\n"
          + "  category: single-source\n"
          + "  expected_behavior: answer\n"
          + "  facts:\n"
          + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n";
  private static final String ONE_CLAIM_REPLY =
      "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";

  @Test
  void theReportMeasuresCallsTokensAndCostPerCheck() throws Exception {
    config(defaultConfig() + "judgePricing:\n  inputPerMillion: 5.0\n  outputPerMillion: 25.0\n");
    cases(ONE_ANSWER_CASE);
    replyFor = ONE_CLAIM_REPLY;
    var buf = new ByteArrayOutputStream();
    int code = Harness.run(new String[0], root, new PrintStream(buf), model -> reportingYes());
    String output = buf.toString();
    assertEquals(0, code, output);
    // 1 trap pair (calibration) + Coverage confirm + Relevance; Groundedness shortcuts on the gold chunk
    assertTrue(output.contains("Cost and time"), output);
    assertTrue(output.contains("judge: 3 calls, 300 prompt + 15 completion tokens, est. $0.0019"), output);
    JsonNode usage = onlyReport().get("usage");
    assertEquals(3, usage.get("judgeCalls").asInt());
    assertEquals(1, usage.get("assistantCalls").asInt());
    assertEquals((300 * 5.0 + 15 * 25.0) / 1_000_000.0, usage.get("judgeCostUsd").asDouble(), 1e-12);
    var names = new java.util.ArrayList<String>();
    usage.get("byCheck").forEach(entry -> names.add(entry.get("check").asText()));
    assertEquals(java.util.List.of("calibration", "Coverage", "Relevance"), names);
  }

  @Test
  void withoutJudgePricingTheReportHasTokensAndNoCost() throws Exception {
    cases(ONE_ANSWER_CASE);
    replyFor = ONE_CLAIM_REPLY;
    var buf = new ByteArrayOutputStream();
    Harness.run(new String[0], root, new PrintStream(buf), model -> reportingYes());
    assertTrue(buf.toString().contains("cost not estimated"), buf.toString());
    assertFalse(buf.toString().contains("$0.0000"), buf.toString());
    assertTrue(onlyReport().get("usage").get("judgeCostUsd").isNull());
  }

  @Test
  void skippingCalibrationLeavesNoCalibrationRow() throws Exception {
    cases(ONE_ANSWER_CASE);
    replyFor = ONE_CLAIM_REPLY;
    Harness.run(
        new String[] {"--skipCalibration", "true"},
        root,
        new PrintStream(new ByteArrayOutputStream()),
        model -> reportingYes());
    var names = new java.util.ArrayList<String>();
    onlyReport().get("usage").get("byCheck").forEach(entry -> names.add(entry.get("check").asText()));
    assertEquals(java.util.List.of("Coverage", "Relevance"), names);
  }

  @Test
  void aBadJudgePricingExitsTwoBeforeAnyAssistantCall() throws Exception {
    config(defaultConfig() + "judgePricing:\n  inputPerMillion: 5.0\n");
    cases(ONE_ANSWER_CASE);
    var buf = new ByteArrayOutputStream();
    int code = Harness.run(new String[0], root, new PrintStream(buf), model -> reportingYes());
    assertEquals(2, code, buf.toString());
    assertTrue(buf.toString().contains("judgePricing"), buf.toString());
    assertEquals(0, requests.get());
  }
```

For the baseline re-run case (Review Focus 4) reuse how the existing baseline re-run tests in `HarnessTest` are set up (search for `replyFromRerun`) and add one test `aBaselineRerunIsCountedAsRealSpend`: with the baseline saying `c1` passed, first reply failing Coverage, `replyFromRerun` passing; assert the report's `usage.assistantCalls` is 2. Copy the setup of the nearest existing re-run test verbatim, changing only the assertion.

- [ ] **Step 2: Run them to see them fail**

Run: `mvn -B -q -pl eval -am test -Dtest='CaseRunnerTest,HarnessTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation FAIL, no `CaseRunner(Assistant, List, UsageMeter)`.

- [ ] **Step 3: Implement `CaseRunner`**

Add a field and constructors:

```java
  private final UsageMeter meter;

  CaseRunner(Assistant assistant, List<Registered> checks) {
    this(assistant, checks, new UsageMeter());
  }

  CaseRunner(Assistant assistant, List<Registered> checks, UsageMeter meter) {
    this.assistant = assistant;
    this.checks = checks;
    this.meter = meter;
  }
```

In `run`, time the assistant call (a call that fails still happened, so `finally`):

```java
    Answer answer;
    long askStartedNanos = System.nanoTime();
    try {
      answer = assistant.ask(evalCase.question(), runId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return CaseResult.failed(evalCase, "interrupted");
    } catch (Exception e) {
      return CaseResult.failed(evalCase, e.getMessage());
    } finally {
      meter.assistantCall((System.nanoTime() - askStartedNanos) / 1_000_000);
    }
```

and label each check: as the first line inside `for (Registered registered : checks) {` add `meter.setCheck(registered.check().name());`, and after the loop (before `return CaseResult.answered(...)`) add `meter.setCheck(null);`. Add one sentence to the class Javadoc: "The meter is told which check is running so judge calls are counted per check."

- [ ] **Step 4: Implement `Harness`**

In `runInner`:

1. First lines after the signature:

```java
    long startedNanos = System.nanoTime();
    UsageMeter meter = new UsageMeter();
```

2. Right after `EvalConfig config = EvalConfig.from(...)`:

```java
    UsageMeter.Pricing pricing = config.judgePricing(); // a bad block exits 2 before any call
```

3. Wrap the judge:

```java
    Judge judge = new Judge(new MeteredLlm(judgeLlm.apply(config.requireJudgeModel()), meter));
```

4. Label calibration:

```java
    meter.setCheck("calibration");
    SuiteReport.Calibration calibration = CalibrationRun.run(config, judge);
    meter.setCheck(null);
```

5. Pass the meter to the runner: `new CaseRunner(client, checks, meter)`.

6. Build the report with the usage:

```java
    SuiteReport.Usage usage = meter.usage((System.nanoTime() - startedNanos) / 1_000_000, pricing);
    SuiteReport report =
        new SuiteReport(
            runId,
            config.endpoint(),
            config.passFloor(),
            CheckInfo.of(checks),
            calibration,
            outcome.results(),
            outcome.comparison(),
            usage);
```

Update the Harness comment "Wiring only" is still true; no other change.

- [ ] **Step 5: Run the whole suite**

Run: `mvn -B -q test`
Expected: all PASS. If the exact cost text `est. $0.0019` differs by rounding, the arithmetic is `(300*5 + 15*25)/1e6 = 0.001875`, printed with `%.4f` as `0.0019`; fix the test only if the arithmetic, not the code, is wrong.

- [ ] **Step 6: Commit**

```bash
git add eval/src/main/java/eval/CaseRunner.java eval/src/main/java/eval/Harness.java \
  eval/src/test/java/eval/CaseRunnerTest.java eval/src/test/java/eval/HarnessTest.java
git commit -m "feat: meter judge and assistant calls through the run (unit 20)"
```

---

### Task 8: One measured run, the decisions, and formatting

**Files:**
- Modify: `grilling-decisions.md`, `scope.md`, `usercentrics-tech-stack-and-repo-structure.md`
- Format: the Java files below

**Interfaces:**
- Consumes: everything above; a real `OPENROUTER_API_KEY` (the user's; never print it).
- Produces: D49 and D50 in `grilling-decisions.md` with the measured numbers, which unit 25 (`SCALE-PLAN.md`) reads.

- [ ] **Step 1: Build and run the full command once, with calibration, against the stub**

This makes real API calls (judge is `anthropic/claude-opus-4.8`; 28 cases plus 27 calibration calls). **Ask the user before running it.** Then:

```bash
mvn -q -DskipTests package
export OPENROUTER_API_KEY=...            # already in the user's shell; do not echo it
java -jar assistant/target/assistant.jar > /tmp/stub.log 2>&1 &
java -jar eval/target/eval.jar | tee /tmp/run-e.txt
kill %1
```

Expected: the console shows `Advisory Relevance (never fails a case): flagged on N of 28 case(s)` and a `Cost and time` block with a `calibration`, `Coverage`, `Groundedness` and `Relevance` row. The exit code and the pass rate are the same as the same cases without Relevance (19–20/28 by D48): Relevance changes neither.

- [ ] **Step 2: Sanity-check the token counts against OpenRouter**

Compare the printed `judge: … prompt + … completion tokens` with the OpenRouter activity page for the run's time window. They should match within a few percent. `completion_tokens` is expected to include reasoning tokens (the judge runs at `reasoning.effort: low`, D41). If the totals disagree by more than that, stop and report; do not write D50 with numbers you do not trust.

- [ ] **Step 3: Write D49 and D50**

Append to `grilling-decisions.md` after D48 (before `## Open`), filling the bracketed numbers from `/tmp/run-e.txt` — they are the measured run, not estimates:

```markdown
49. **Relevance as built (unit 17).** `RelevanceCheck` asks the main judge one narrow question per claim (`Judge.relevant(question, claim)`): is the claim pertinent to the question, or true-but-off-topic padding? It is registered last in `Checks` with `gating=false`, so its failures show in the case detail as `Relevance (advisory): off-topic claim(s): ...`, are counted per run (`advisoryFlags` in the JSON, `Advisory Relevance (never fails a case): flagged on N of M case(s)` in the console) and never change a verdict, the pass rate, the exit code or a baseline comparison (D12, D13). A refusal has no claims and costs no call. A judge reply that is not YES/NO throws (D41) and shows as an advisory `check error`, which counts as flagged, so a broken advisory judge is not invisible. It adds one judge call per claim to every answered case. Relevance is **uncalibrated** (D16): no labeled pairs, so a flag is a prompt to look, not a measurement. First full run: flagged on [N] of 28 cases. Making it gating is changing `false` to `true` in `Checks`; do that only after a labeled sample exists.

50. **Measured cost and time as built (unit 20).** The judge's `Llm` is wrapped in `MeteredLlm`, which feeds a `UsageMeter` with the `usage.prompt_tokens` and `usage.completion_tokens` OpenRouter returns (`Llm.completeWithUsage`; 0 when a reply has no `usage`). `CaseRunner` tells the meter which check is running, so calls and tokens are reported **per check**, with calibration under its own `calibration` row; assistant calls and time are timed around each `ask`, re-runs included. **Assistant tokens are not measured**: the harness sees the assistant only over HTTP (D28), so the report gives its calls and time and says its tokens are not visible; the scale plan prices the assistant by hand (one Haiku 4.5 call per case) or from the stub's own OpenRouter activity. **Cost is an estimate**: measured judge tokens times `judgePricing` (`inputPerMillion`, `outputPerMillion`, USD, default the D40 prices of `anthropic/claude-opus-4.8` on `azure/global`); with no `judgePricing` the report says the cost was not estimated rather than printing zero, and a malformed block exits 2. Only calls that came back are counted, so the figure is a floor. The meter holds one "current check", right while cases run one at a time; parallel cases need a `ThreadLocal` there. **Measured run [run id] (full command with calibration, 28 cases, stub `anthropic/claude-haiku-4.5`, judge `anthropic/claude-opus-4.8`):** wall-clock [X] s; assistant [N] calls, [X] s; judge [N] calls, [N] prompt + [N] completion tokens, est. $[X]; by check: calibration [N] calls, Coverage [N], Groundedness [N], Relevance [N]. That is [N] judge calls per case (the "~3" guess in the plan was low, D17), of which Relevance is [N]. **For D45:** the judge calls spent on the [N] failing cases are [N] of the total, which is the ceiling on what fail-fast could save; decide from that number whether it is worth building. **For the scale plan (unit 25):** cost per run is about $[X] + assistant; one run takes [X] s sequentially, so a 5-minute CI budget needs parallelism or a subset.
```

- [ ] **Step 4: Move the status lines**

In `scope.md`: move the "Measured calls, tokens, cost, wall-clock time" and "Relevance check" rows from "In scope, planned and not built yet" to the "In scope, built" table (one row each, citing D49 and D50), and delete the two rows from the planned table. In the cut-order sentence nothing changes.

In `usercentrics-tech-stack-and-repo-structure.md`: in the LLM-calls table row replace "The client also records calls and tokens per role and per check, for the measured cost report." with "`MeteredLlm` records judge calls and tokens per check into `UsageMeter` for the measured cost report (D50)." and in the tree replace the `LlmClient.java` line with `MeteredLlm.java` / `UsageMeter.java` lines and their one-line roles.

- [ ] **Step 5: Commit the docs**

```bash
git add grilling-decisions.md scope.md usercentrics-tech-stack-and-repo-structure.md
git commit -m "docs: record Relevance and the measured cost report (D49, D50)"
```

- [ ] **Step 6: Format the Java files this plan changed** with google-java-format 1.25.2, in its own commit. Get the jar once into the session scratchpad and set `GJF_JAR`:

```bash
curl -L -o "$SCRATCH/gjf.jar" https://github.com/google/google-java-format/releases/download/v1.25.2/google-java-format-1.25.2-all-deps.jar
export GJF_JAR="$SCRATCH/gjf.jar"
java --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
     -jar "$GJF_JAR" --replace \
     llm/src/main/java/llm/Completion.java llm/src/main/java/llm/Llm.java \
     llm/src/main/java/llm/OpenRouterLlm.java llm/src/test/java/llm/OpenRouterLlmTest.java \
     eval/src/main/java/eval/Judge.java eval/src/main/java/eval/UsageMeter.java \
     eval/src/main/java/eval/MeteredLlm.java eval/src/main/java/eval/EvalConfig.java \
     eval/src/main/java/eval/SuiteReport.java eval/src/main/java/eval/ConsoleReport.java \
     eval/src/main/java/eval/CaseRunner.java eval/src/main/java/eval/Harness.java \
     eval/src/main/java/eval/checks/RelevanceCheck.java eval/src/main/java/eval/checks/Checks.java \
     eval/src/test/java/eval/JudgeRelevantTest.java eval/src/test/java/eval/UsageMeterTest.java \
     eval/src/test/java/eval/MeteredLlmTest.java eval/src/test/java/eval/EvalConfigTest.java \
     eval/src/test/java/eval/SuiteReportTest.java eval/src/test/java/eval/ConsoleReportTest.java \
     eval/src/test/java/eval/CaseRunnerTest.java eval/src/test/java/eval/HarnessTest.java \
     eval/src/test/java/eval/checks/RelevanceCheckTest.java eval/src/test/java/eval/checks/ChecksTest.java
mvn -B -q test
git commit -am "style: google-java-format on the files changed in units 17 and 20"
```

- [ ] **Step 7: Push and open the PR**

Only when the user says so. Title: `feat: advisory Relevance check and measured cost report (units 17, 20)`. Body: what changed, the measured numbers from D50, "Relevance is uncalibrated (D16)", and that assistant tokens are not visible (D28).

---

## Self-Review

**Spec coverage.**
- Unit 17 — "each claim goes to the judge with a narrow question": Tasks 1, 2. "Advisory: failures in detail and counted in the summary, never change pass/fail": Task 2 (`ChecksTest` flag), Task 3 (`advisoryFlags`, console line, end-to-end `aRelevanceNoIsReportedButTheCaseAndTheRunStillPass`; `CaseRunnerTest.advisoryFailureDoesNotFailTheCase` already pins the runner rule). "Making it gating is one flag": `Checks` line, pinned by `relevanceIsRegisteredLastAndIsAdvisory`.
- Unit 20 — "records calls and tokens by role and by check": Tasks 4, 5, 7 (judge by check; assistant calls and time). Deviation, stated in Global Constraints and D50: assistant tokens are not visible over HTTP (D28). "Report prints total calls, tokens, estimated cost, wall-clock time": Tasks 6, 7. "Scale plan cost line from a measured run": Task 8 produces the numbers; unit 25 is a later plan.
- D47: `judgePricing` is a config key with dotted override (Task 5 test).
- Gaps left on purpose: unit 23 (no code), 24–26 (documents), stub tuning, D44, D45.

**Placeholder scan.** The only bracketed values are in the D49/D50 text in Task 8 Step 3, and they are filled from the measured run in that same task (the instruction says so). The re-run test in Task 7 says to copy the nearest existing re-run test; that test exists (`replyFromRerun` in `HarnessTest`) and only its final assertion differs.

**Type consistency.** `Completion(String text, long promptTokens, long completionTokens)` (Task 4) is used unchanged in `MeteredLlm` and the tests. `UsageMeter.Pricing(double, double)` is produced by `EvalConfig.judgePricing()` and consumed by `UsageMeter.usage(long, Pricing)`. `SuiteReport.Usage(long, int, long, List<CheckUsage>, Double)` is built only in `UsageMeter.usage` and read by `ConsoleReport`. `SuiteReport`'s new last component keeps the 7- and 6-argument constructors, so `SuiteReportTest`, `ConsoleReportTest`, `SuiteRunnerTest` and `Harness` compile as before. `Checks.registered(kb, judge)` keeps its signature. `CaseRunner`'s 2-argument constructor stays for `CaseRunnerTest`, `SuiteRunnerTest`.

**Review Focus coverage.** 1: `aRefusalHasNoClaims…`, `aJudgeErrorPropagates…`, and `advisoryFlags…` counts `check error: boom`. 2: `parseCompletionWithNoUsageBlock…`, `anLlmThatReportsNoTokensStillCountsTheCall`. 3: `withNoPricesTheCostIsNullNotZero`, `withNoPricesItSaysTheCostWasNotEstimated…`, `aMalformedJudgePricing…`, `aBadJudgePricingExitsTwo…`. 4: `aBaselineRerunIsCountedAsRealSpend`, `skippingCalibrationLeavesNoCalibrationRow`. 5: `aFailedCallIsNotCountedAndTheFailureReachesTheCaller`, stated in D50.

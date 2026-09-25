# Eval Harness — Plan B: Groundedness, Source and judge calibration (units 13–16) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Where this file goes:** written in plan mode to `~/.claude/plans/toasty-drifting-torvalds.md`. First execution step: copy it to `docs/superpowers/plans/2026-09-24-eval-harness-plan-b-units-13-16.md` (the repo convention, next to plan A).

**Goal:** Two more gating checks (Groundedness, Source) and the measured calibration of the Groundedness judge, so the harness rejects a claim its cited chunk does not support and a multi-source answer that skips a required document.

**Architecture:** `Judge` gets a third narrow question, `supports(claim, passage)`. `GroundednessCheck` reads the covering claims that Coverage left in `CaseState`: a claim citing a gold chunk of its fact passes with no LLM call, every other claim goes to the judge one claim and one cited chunk at a time. `SourceCheck` is deterministic and compares the documents behind the citations with the documents of the gold chunks. A new `LabeledSample` calibration (20 hand-labeled pairs, bundled in the jar) runs inside the single harness command next to the trap pairs, is reported in the same report, and fails the exit code on a miss. Both checks are one class plus one line in `Checks`.

**Tech Stack:** Java 21, Maven multi-module, JUnit 5, Jackson, SnakeYAML. No new dependencies.

**Spec:** `usercentrics-eval-harness-plan.md` (Step 4: Groundedness, Source, Judge calibration), `build-order.md` units 13–16, `grilling-decisions.md` D5, D10, D11, D14, D15, D16, D26, D39, D40, D41, D42, `CONTEXT.md`. Previous plans: `docs/superpowers/plans/2026-09-24-eval-harness-units-1-8.md` (PR #1), `2026-09-24-eval-harness-plan-a-units-9-12.md` (PR #2). Built since: the `CaseRunner` refactor, trap pairs inside the harness command (D41), `--config <file>` (D42, PR #5, merged to `origin/main`).

**Later plans (not in this one):** C = 18–19 (baseline, re-run); D = 21–22 (28 cases, doc-hash warning); E = 17, 20 (Relevance, cost); F = 23–26 (PATTERN.md, SCALE-PLAN.md, README, live-change rehearsal).

## Global Constraints

- Java 21. No new dependencies. `eval/` stays plain Java and never depends on `assistant/` (D28, D37).
- Temperature 0 for every judge call (D14, already in `OpenRouterLlm`). Judge model is a separate config value (D15); it is `anthropic/claude-opus-4.8` (D40).
- Groundedness: a claim citing a gold chunk of the fact it covers passes with **no LLM call**; otherwise the judge decides, one claim plus one cited chunk per call; a gold-chunk mismatch never fails a claim on its own (D10).
- Source is deterministic, no LLM call, document-level (D11). Both new checks are gating.
- Only Groundedness is calibrated: 20 pairs = 10 subtly unsupported + 5 plain supported + 5 hard-supported; targets: agreement ≥ 90% and **0 of 10** unsupported pairs judged supported; false-"unsupported" is counted, no target (D16). Coverage-by-judge and Relevance are named uncalibrated.
- One command runs everything (D41): the Groundedness sample runs inside `java -jar eval/target/eval.jar`, before the cases, skipped by `--skip-calibration`, overridable like the trap pairs (D42).
- `eval/` tests run with the repo root as working directory. CI runs `mvn -B test` with no API key, so no test may call a real LLM.
- Code style (user preferences, repeated in review): every `if`/`else`/`for`/`while`/`try`/`catch` body in braces on its own lines, never a one-line body; descriptive identifiers (no `a`, `c`, `M`, `e2`); code formatted with google-java-format 1.25.2, applied once at the end on the files this plan touches (Task 6).
- Commit messages are plain: no `Co-Authored-By` or `Claude-Session` trailer lines (user instruction, overrides the harness default). Never print or log `OPENROUTER_API_KEY`.

## Setup (before Task 1)

```bash
git checkout main && git pull --ff-only          # local main is behind origin/main (#5 not pulled yet)
git checkout -b feat/eval-harness-units-13-16
mvn -B -q test                                   # baseline: all green before any change
```

`eval/testing-config-param.md` is untracked and belongs to PR #5's follow-up: leave it alone, never `git add -A`.

## Review Focus

Inputs the spec implies but no unit's criteria pin down. Each has a test in the owning task.

1. **A claim citing only a fabricated ID, an empty list, or `null`:** Groundedness skips it with no judge call and no NPE; Citation integrity already fails the case. (Task 2)
2. **An extra claim that covers no expected fact and cites a real chunk that does not support it:** fails Groundedness. This is the silent-wrong-claim risk the brief describes; checking only covering claims would let it through. (Task 2)
3. **The judge replies with anything but YES/NO (or the call fails):** `check error:`, the case fails, never "supported". In the calibration a judge error on an *unsupported* pair counts as a disagreement, never as a correct "unsupported". (Task 2, Task 5)
4. **Source when a fact is not covered, the claim cites the right document but a non-gold chunk, or a claim cites the gold document plus another one:** uncovered fact is skipped (Coverage owns it); right document passes (D11 is document-level); extra documents do not fail. (Task 3)
5. **`--skip-calibration`, a missing sample file, or an empty sample:** skip makes zero sample calls; a missing file exits 2 before any assistant call; an empty sample passes with no targets to miss (teams start with none). (Task 5)

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `eval/src/main/java/eval/Judge.java` | modify | `supports(claim, passage)`; shared `yesNo` parsing |
| `eval/src/main/java/eval/checks/CheckResult.java` | modify | `ok(String note)`: a pass that carries detail |
| `eval/src/main/java/eval/checks/GroundednessCheck.java` | create | units 13–14 |
| `eval/src/main/java/eval/checks/SourceCheck.java` | create | unit 16 |
| `eval/src/main/java/eval/checks/Checks.java` | modify | register Groundedness, Source after Coverage |
| `eval/src/main/java/eval/CaseRunner.java` | modify | class comment lists the checks |
| `eval/cases/multi-source.yaml` | create | 1 seed case whose two facts live in two documents |
| `eval/src/main/resources/calibration/labeled-sample.yaml` | create | the 20 hand-labeled pairs |
| `eval/src/main/java/eval/calibration/LabeledSample.java` | create | runs the sample against the judge, computes the numbers |
| `eval/src/main/java/eval/SuiteReport.java` | modify | `Calibration.groundedness`, exit reasons |
| `eval/src/main/java/eval/ConsoleReport.java` | modify | prints the Groundedness calibration |
| `eval/src/main/java/eval/EvalConfig.java` | modify | `calibration.labeledSample` override |
| `eval/src/main/java/eval/Harness.java` | modify | run the sample after the trap pairs |
| `eval/config.yaml` | modify | comments for `calibration.labeledSample`, judge model note |
| `calibration/calibration-notes.md` | create | measured results and prompt changes |
| `grilling-decisions.md`, `usercentrics-tech-stack-and-repo-structure.md` | modify | D43, flags line |
| tests | create/modify | `JudgeSupportsTest`, `GroundednessCheckTest`, `SourceCheckTest`, `ChecksTest`, `LabeledSampleTest`, `LabeledSampleFileTest`, `EvalConfigTest`, `SuiteReportTest`, `HarnessTest`, `SeedCasesTest` |

Chunk IDs used below follow `Chunker.slug` (`doc#heading-slug`); `SeedCasesTest` and `EvalCaseLoader` fail loudly on a wrong ID, so a typo cannot slip through.

---

### Task 1: The `supports` judge question and a pass that carries detail

**Files:**
- Modify: `eval/src/main/java/eval/Judge.java`
- Modify: `eval/src/main/java/eval/checks/CheckResult.java`
- Test: `eval/src/test/java/eval/JudgeSupportsTest.java` (create)

**Interfaces:**
- Consumes: `llm.Llm.complete(String system, String user)`.
- Produces: `boolean Judge.supports(String claim, String passage)` (throws `IllegalStateException` on an unclear reply, like `agree`); `static CheckResult CheckResult.ok(String note)` (`passed == true`, `reason == note`). `CaseRunner` already copies `reason` into the report for passing checks.

- [ ] **Step 1: Write the failing test**

```java
package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JudgeSupportsTest {
  private static Judge saying(String reply) {
    return new Judge((system, user) -> reply);
  }

  @Test
  void yesMeansSupportedInAnyCaseWithPunctuation() {
    assertTrue(saying("YES").supports("claim", "passage"));
    assertTrue(saying("Yes.").supports("claim", "passage"));
    assertTrue(saying("**YES**").supports("claim", "passage"));
  }

  @Test
  void noMeansNotSupported() {
    assertFalse(saying("NO").supports("claim", "passage"));
    assertFalse(saying("no - the passage says 13").supports("claim", "passage"));
  }

  @Test
  void anythingElseIsAnErrorNeverASilentNo() {
    assertThrows(IllegalStateException.class, () -> saying("Maybe").supports("claim", "passage"));
    assertThrows(IllegalStateException.class, () -> saying("").supports("claim", "passage"));
    assertThrows(IllegalStateException.class, () -> saying(null).supports("claim", "passage"));
  }

  @Test
  void thePromptCarriesThePassageAndTheClaim() {
    var seen = new AtomicReference<String>();
    var judge =
        new Judge(
            (system, user) -> {
              seen.set(user);
              return "YES";
            });
    judge.supports("Safari 14 works", "Safari: 14");
    assertTrue(seen.get().contains("Passage:\nSafari: 14"), seen.get());
    assertTrue(seen.get().contains("Claim: Safari 14 works"), seen.get());
  }
}
```

- [ ] **Step 2: Run it, expect a compile failure**

Run: `mvn -q -pl eval -am test -Dtest=JudgeSupportsTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL, `cannot find symbol: method supports`.

- [ ] **Step 3: Implement**

In `Judge.java` add the prompt next to the other two, add `supports`, and move the YES/NO parsing out of `agree` into a private helper both use:

```java
  private static final String SUPPORTS_SYSTEM =
      """
You check whether a passage from documentation supports a claim made by an assistant.
Answer YES only if everything the claim states is stated in the passage or follows directly from it, including every number, version, condition and qualifier such as "always", "only" or "all".
Answer NO if the claim adds a detail the passage does not state, changes a number or version, overstates or drops a qualifier, contradicts the passage, or is not about what the passage says.
The claim may be worded differently from the passage and may combine sentences of the passage.
Reply with exactly one word: YES or NO.\
""";

  /** Does the passage support the claim? */
  public boolean supports(String claim, String passage) {
    return yesNo(llm.complete(SUPPORTS_SYSTEM, "Passage:\n" + passage + "\n\nClaim: " + claim));
  }

  private static boolean yesNo(String reply) {
    Matcher matcher = FIRST_WORD.matcher(reply == null ? "" : reply);
    if (!matcher.find()) {
      throw new IllegalStateException("judge returned neither YES nor NO: \"" + reply + "\"");
    }
    return matcher.group(1).equalsIgnoreCase("yes");
  }
```

`agree` becomes `return yesNo(llm.complete(AGREE_SYSTEM, "Fact: " + fact + "\nClaim: " + claim));`. Update the class comment to "Three narrow judge questions ...".

In `CheckResult.java` add: `public static CheckResult ok(String note) { return new CheckResult(true, note); }` (in braces on its own lines, per the style rule).

- [ ] **Step 4: Run the judge tests**

Run: `mvn -q -pl eval -am test -Dtest='Judge*Test' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS (the existing `JudgeTest` still passes: `agree` behaves as before).

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/java/eval/Judge.java eval/src/main/java/eval/checks/CheckResult.java eval/src/test/java/eval/JudgeSupportsTest.java
git commit -m "feat: judge question for whether a passage supports a claim"
```

---

### Task 2: Groundedness (units 13 and 14)

**Files:**
- Create: `eval/src/main/java/eval/checks/GroundednessCheck.java`
- Modify: `eval/src/main/java/eval/checks/Checks.java`
- Modify: `eval/src/test/java/eval/HarnessTest.java` (one expectation, Step 6)
- Test: `eval/src/test/java/eval/checks/GroundednessCheckTest.java` (create), `eval/src/test/java/eval/checks/ChecksTest.java` (create)

**Interfaces:**
- Consumes: `Judge.supports(String claim, String passage)`, `CheckResult.ok(String)`, `CaseState.covering(ExpectedFact) : List<Claim>` (filled by Coverage), `KnowledgeBase.has(String)`, `KnowledgeBase.get(String) : kb.Chunk` (`text()`), `ExpectedFact.chunks()`, `Answer.claims()` (may be null).
- Produces: `new GroundednessCheck(KnowledgeBase kb, Judge judge)`, `name()` = `"Groundedness"`; registered gating after Coverage. Later tasks and plan C rely on the name string.

Rules implemented (ruling, D43): every claim in the answer is checked, not only covering ones. A claim that covers a fact and cites one of that fact's gold chunks is "grounded (gold chunk)" with no call. Any other claim goes to the judge against each cited chunk that exists in the knowledge base, stopping at the first YES; no YES fails it. A claim with no existing cited chunk is skipped (Citation integrity owns that failure).

- [ ] **Step 1: Write the failing tests**

```java
package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.*;
import kb.Chunk;
import org.junit.jupiter.api.Test;

class GroundednessCheckTest {
  private static final KnowledgeBase KB =
      new KnowledgeBase(
          List.of(
              new Chunk("d#safari", "d", "Safari 14 or later is supported."),
              new Chunk("d#chrome", "d", "Chrome 69 or later is supported.")));
  private static final ExpectedFact SAFARI =
      new ExpectedFact("Safari 14 or later is supported", List.of("d#safari"), List.of("Safari"));

  private static EvalCase caseWith(ExpectedFact... facts) {
    return new EvalCase(
        "c", "q", "single-source", null, "answer", List.of(facts), null, null, null);
  }

  /** Fake judge LLM: YES when the passage in the prompt contains the marker; counts calls. */
  private static final class FakeLlm implements llm.Llm {
    int calls;
    final String yesWhenPassageContains;

    FakeLlm(String marker) {
      this.yesWhenPassageContains = marker;
    }

    @Override
    public String complete(String system, String user) {
      calls++;
      String passage = user.substring(0, user.indexOf("Claim:"));
      return passage.contains(yesWhenPassageContains) ? "YES" : "NO";
    }
  }

  private static CheckResult run(FakeLlm llm, CaseState state, Claim... claims) {
    return new GroundednessCheck(KB, new Judge(llm))
        .run(caseWith(SAFARI), new Answer(false, List.of(claims)), state);
  }

  @Test
  void coveringClaimCitingAGoldChunkPassesWithZeroJudgeCalls() {
    var llm = new FakeLlm("never");
    var claim = new Claim("Safari 14 works", List.of("d#safari"));
    var state = new CaseState();
    state.setCovering(SAFARI, List.of(claim));
    var result = run(llm, state, claim);
    assertTrue(result.passed(), result.reason());
    assertTrue(result.reason().contains("grounded (gold chunk)"), result.reason());
    assertEquals(0, llm.calls);
  }

  @Test
  void coveringClaimCitingARealChunkOutsideGoldGoesToTheJudgeAndPassesWhenSupported() {
    var llm = new FakeLlm("Chrome");
    var claim = new Claim("Chrome 69 works", List.of("d#chrome"));
    var state = new CaseState();
    state.setCovering(SAFARI, List.of(claim));
    var result = run(llm, state, claim);
    assertTrue(result.passed(), result.reason());
    assertTrue(result.reason().contains("grounded (judge)"), result.reason());
    assertEquals(1, llm.calls);
  }

  @Test
  void unsupportedClaimFailsAndShowsTheClaimAndTheChunk() {
    var llm = new FakeLlm("Safari"); // the cited chunk is the Chrome one, so NO
    var claim = new Claim("Safari 14 works", List.of("d#chrome"));
    var state = new CaseState();
    state.setCovering(SAFARI, List.of(claim));
    var result = run(llm, state, claim);
    assertFalse(result.passed());
    assertTrue(result.reason().contains("Safari 14 works"), result.reason());
    assertTrue(result.reason().contains("d#chrome"), result.reason());
    assertTrue(result.reason().contains("Chrome 69 or later"), result.reason());
  }

  @Test
  void anExtraClaimThatCoversNoFactIsJudgedToo() {
    var llm = new FakeLlm("Chrome 99");
    var covering = new Claim("Safari 14 works", List.of("d#safari"));
    var extra = new Claim("Chrome 99 works", List.of("d#chrome"));
    var state = new CaseState();
    state.setCovering(SAFARI, List.of(covering));
    var result = run(llm, state, covering, extra);
    assertFalse(result.passed());
    assertTrue(result.reason().contains("Chrome 99 works"), result.reason());
    assertEquals(1, llm.calls);
  }

  @Test
  void oneSupportingChunkAmongSeveralCitedIsEnough() {
    var llm = new FakeLlm("Safari");
    var claim = new Claim("Safari 14 works", List.of("d#chrome", "d#safari"));
    var result = run(llm, new CaseState(), claim); // covers no fact, so no gold shortcut
    assertTrue(result.passed(), result.reason());
    assertEquals(2, llm.calls);
  }

  @Test
  void claimsWithoutAnyExistingCitationAreSkippedWithNoJudgeCall() {
    var llm = new FakeLlm("never");
    var result =
        run(
            llm,
            new CaseState(),
            new Claim("fabricated", List.of("d#missing")),
            new Claim("empty", List.of()),
            new Claim("null", null));
    assertTrue(result.passed(), result.reason());
    assertTrue(result.reason().contains("skipped"), result.reason());
    assertEquals(0, llm.calls);
  }

  @Test
  void noClaimsAndNullClaimsPass() {
    var llm = new FakeLlm("never");
    var check = new GroundednessCheck(KB, new Judge(llm));
    assertTrue(check.run(caseWith(SAFARI), new Answer(true, List.of()), new CaseState()).passed());
    assertTrue(check.run(caseWith(SAFARI), new Answer(false, null), new CaseState()).passed());
    assertEquals(0, llm.calls);
  }

  @Test
  void anUnclearJudgeReplyThrowsInsteadOfPassing() {
    var check = new GroundednessCheck(KB, new Judge((system, user) -> "Maybe"));
    var answer = new Answer(false, List.of(new Claim("x", List.of("d#chrome"))));
    assertThrows(
        IllegalStateException.class,
        () -> check.run(caseWith(SAFARI), answer, new CaseState()));
  }
}
```

`ChecksTest`:

```java
package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.Judge;
import eval.KnowledgeBase;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChecksTest {
  private static List<String> names() {
    return Checks.registered(new KnowledgeBase(List.of()), new Judge((system, user) -> "YES"))
        .stream()
        .map(registered -> registered.check().name())
        .toList();
  }

  @Test
  void groundednessRunsAfterCitationIntegrityAndCoverage() {
    List<String> names = names();
    assertTrue(names.indexOf("Citation integrity") < names.indexOf("Coverage"));
    assertTrue(names.indexOf("Coverage") < names.indexOf("Groundedness"));
  }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `mvn -q -pl eval -am test -Dtest='GroundednessCheckTest,ChecksTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL, `cannot find symbol: class GroundednessCheck`.

- [ ] **Step 3: Implement `GroundednessCheck`**

```java
package eval.checks;

import eval.*;
import java.util.*;
import kb.Chunk;

/**
 * Does each claim's cited chunk support it? (D10) A claim that covers a fact and cites one of that
 * fact's gold chunks passes with no LLM call. Every other claim goes to the judge, one claim and
 * one cited chunk per call, and passes when any one cited chunk supports it. A gold-chunk mismatch
 * alone never fails a claim. A claim with no existing cited chunk is skipped: Citation integrity
 * reports that.
 */
public final class GroundednessCheck implements Check {
  private static final int EXCERPT_LENGTH = 120;

  private final KnowledgeBase kb;
  private final Judge judge;

  public GroundednessCheck(KnowledgeBase kb, Judge judge) {
    this.kb = kb;
    this.judge = judge;
  }

  @Override
  public String name() {
    return "Groundedness";
  }

  @Override
  public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
    List<Claim> claims = answer.claims() == null ? List.of() : answer.claims();
    Set<Claim> goldGrounded = claimsCitingAGoldChunk(evalCase, state);
    List<String> notes = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    for (Claim claim : claims) {
      if (goldGrounded.contains(claim)) {
        notes.add("grounded (gold chunk): \"" + claim.claim() + "\"");
        continue;
      }
      List<Chunk> cited = citedChunks(claim);
      if (cited.isEmpty()) {
        notes.add("skipped, no existing citation (see Citation integrity): \"" + claim.claim() + "\"");
      } else if (anyChunkSupports(claim, cited)) {
        notes.add("grounded (judge): \"" + claim.claim() + "\"");
      } else {
        problems.add("unsupported: \"" + claim.claim() + "\" (cited " + describe(cited) + ")");
      }
    }
    if (!problems.isEmpty()) {
      return CheckResult.fail(String.join("; ", problems));
    }
    return notes.isEmpty() ? CheckResult.ok() : CheckResult.ok(String.join("; ", notes));
  }

  private static Set<Claim> claimsCitingAGoldChunk(EvalCase evalCase, CaseState state) {
    Set<Claim> grounded = new HashSet<>();
    for (ExpectedFact fact : evalCase.facts()) {
      for (Claim claim : state.covering(fact)) {
        if (claim.citations() != null && claim.citations().stream().anyMatch(fact.chunks()::contains)) {
          grounded.add(claim);
        }
      }
    }
    return grounded;
  }

  private List<Chunk> citedChunks(Claim claim) {
    List<Chunk> chunks = new ArrayList<>();
    if (claim.citations() == null) {
      return chunks;
    }
    for (String id : claim.citations()) {
      if (kb.has(id)) {
        chunks.add(kb.get(id));
      }
    }
    return chunks;
  }

  private boolean anyChunkSupports(Claim claim, List<Chunk> cited) {
    for (Chunk chunk : cited) {
      if (judge.supports(claim.claim(), chunk.text())) {
        return true;
      }
    }
    return false;
  }

  private static String describe(List<Chunk> cited) {
    List<String> parts = new ArrayList<>();
    for (Chunk chunk : cited) {
      String text = chunk.text().replaceAll("\\s+", " ");
      String excerpt = text.length() <= EXCERPT_LENGTH ? text : text.substring(0, EXCERPT_LENGTH) + "...";
      parts.add(chunk.id() + ": \"" + excerpt + "\"");
    }
    return String.join(", ", parts);
  }
}
```

In `Checks.registered` add, after Coverage: `new Registered(new GroundednessCheck(kb, judge), true)`.

- [ ] **Step 4: Run the new tests**

Run: `mvn -q -pl eval -am test -Dtest='GroundednessCheckTest,ChecksTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS.

- [ ] **Step 5: Run the whole suite and fix the tests the new check changes**

Run: `mvn -B -q test`
Expected: one failure, `HarnessTest.negatedClaimWithAllKeywordsFailsCoverageEndToEnd`: it counted exactly 1 judge call (Coverage's); the uncovered claim now also gets one Groundedness call. Change its `assertEquals(1, judgeCalls[0])` to `assertEquals(2, judgeCalls[0])` with the comment `// Coverage's confirm call plus Groundedness's call on the uncovered claim`. The `"Coverage: not covered"` assertion stays. Any other failure: the smallest test-only edit that keeps the test's intent; do not weaken a production check.

- [ ] **Step 6: Commit**

```bash
git add eval/src/main/java/eval/checks/GroundednessCheck.java eval/src/main/java/eval/checks/Checks.java eval/src/test/java/eval/checks/GroundednessCheckTest.java eval/src/test/java/eval/checks/ChecksTest.java eval/src/test/java/eval/HarnessTest.java
git commit -m "feat: Groundedness check, gold chunk shortcut then judge (units 13-14)"
```

---

### Task 3: Source (unit 16) and a multi-source seed case

**Files:**
- Create: `eval/src/main/java/eval/checks/SourceCheck.java`
- Create: `eval/cases/multi-source.yaml`
- Modify: `eval/src/main/java/eval/checks/Checks.java`, `eval/src/main/java/eval/CaseRunner.java` (class comment only)
- Test: `eval/src/test/java/eval/checks/SourceCheckTest.java` (create), `eval/src/test/java/eval/checks/ChecksTest.java`, `eval/src/test/java/eval/SeedCasesTest.java`

**Interfaces:**
- Consumes: `CaseState.covering(ExpectedFact)`, `ExpectedFact.fact()/chunks()`, `Claim.citations()` (may be null).
- Produces: `new SourceCheck()` (no constructor arguments), `name()` = `"Source"`, gating, registered after Groundedness. Rule: for each fact that Coverage marked covered, some covering claim must cite a chunk whose document (the ID text before the first `#`) is a document of one of the fact's gold chunks.

- [ ] **Step 1: Write the failing tests**

```java
package eval.checks;

import static org.junit.jupiter.api.Assertions.*;

import eval.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SourceCheckTest {
  private static final ExpectedFact GEO =
      new ExpectedFact(
          "Do not combine GDPR and TCF in one rule",
          List.of("geolocation-rules#constraints"),
          List.of("TCF"));
  private static final ExpectedFact GTAG =
      new ExpectedFact(
          "Consent Mode requires gtag.js or Google Tag Manager",
          List.of("consent-mode#prerequisites"),
          List.of("gtag.js"));

  private static EvalCase caseWith(ExpectedFact... facts) {
    return new EvalCase(
        "c", "q", "multi-source", null, "answer", List.of(facts), null, null, null);
  }

  private static CheckResult run(EvalCase evalCase, CaseState state) {
    return new SourceCheck().run(evalCase, new Answer(false, List.of()), state);
  }

  private static CaseState covered(ExpectedFact fact, Claim... claims) {
    var state = new CaseState();
    state.setCovering(fact, List.of(claims));
    return state;
  }

  @Test
  void claimCitingTheGoldDocumentPasses() {
    var state = covered(GEO, new Claim("c", List.of("geolocation-rules#constraints")));
    assertTrue(run(caseWith(GEO), state).passed());
  }

  @Test
  void claimCitingAnotherChunkOfTheGoldDocumentPasses() {
    var state = covered(GEO, new Claim("c", List.of("geolocation-rules#overview")));
    assertTrue(run(caseWith(GEO), state).passed());
  }

  @Test
  void claimCitingOnlyAnotherDocumentFailsAndNamesBothDocuments() {
    var state = covered(GEO, new Claim("c", List.of("consent-mode#prerequisites")));
    var result = run(caseWith(GEO), state);
    assertFalse(result.passed());
    assertTrue(result.reason().contains(GEO.fact()), result.reason());
    assertTrue(result.reason().contains("consent-mode"), result.reason());
    assertTrue(result.reason().contains("geolocation-rules"), result.reason());
  }

  @Test
  void oneCoveringClaimFromTheGoldDocumentIsEnough() {
    var state =
        covered(
            GEO,
            new Claim("wrong doc", List.of("consent-mode#prerequisites")),
            new Claim("right doc", List.of("geolocation-rules#constraints")));
    assertTrue(run(caseWith(GEO), state).passed());
  }

  @Test
  void aClaimCitingTheGoldDocumentAndAnotherDocumentPasses() {
    var state =
        covered(
            GEO,
            new Claim(
                "c", List.of("consent-mode#prerequisites", "geolocation-rules#constraints")));
    assertTrue(run(caseWith(GEO), state).passed());
  }

  @Test
  void multiSourceCaseFailsWhenOnlyOneFactUsesItsDocument() {
    var state = new CaseState();
    var geolocationClaim = new Claim("c", List.of("geolocation-rules#constraints"));
    state.setCovering(GEO, List.of(geolocationClaim));
    state.setCovering(GTAG, List.of(geolocationClaim)); // right for GEO, wrong document for GTAG
    var result = run(caseWith(GEO, GTAG), state);
    assertFalse(result.passed());
    assertTrue(result.reason().contains(GTAG.fact()), result.reason());
    assertFalse(result.reason().contains(GEO.fact()), result.reason());
  }

  @Test
  void aFactCoverageDidNotCoverIsSkipped() {
    assertTrue(run(caseWith(GEO), new CaseState()).passed());
  }

  @Test
  void aChunkIdWithoutAHashIsItsOwnDocument() {
    var plain = new ExpectedFact("f", List.of("plain"), List.of());
    var state = covered(plain, new Claim("c", List.of("plain")));
    assertTrue(run(caseWith(plain), state).passed());
  }

  @Test
  void aCoveringClaimWithNullCitationsFailsWithoutThrowing() {
    var state = covered(GEO, new Claim("c", null));
    assertFalse(run(caseWith(GEO), state).passed());
  }
}
```

Add to `ChecksTest`:

```java
  @Test
  void sourceRunsAfterGroundedness() {
    List<String> names = names();
    assertTrue(names.indexOf("Groundedness") < names.indexOf("Source"));
  }
```

Add to `SeedCasesTest` (uses its existing `cases` field):

```java
  @Test
  void aMultiSourceSeedHasFactsInDifferentDocuments() {
    assertTrue(
        cases.stream()
            .filter(evalCase -> evalCase.category().equals("multi-source"))
            .anyMatch(
                evalCase ->
                    evalCase.facts().stream()
                            .flatMap(fact -> fact.chunks().stream())
                            .map(chunkId -> chunkId.substring(0, chunkId.indexOf('#')))
                            .distinct()
                            .count()
                        > 1),
        "need a multi-source case whose facts live in two documents (Source check)");
  }
```

- [ ] **Step 2: Run, expect failures**

Run: `mvn -q -pl eval -am test -Dtest='SourceCheckTest,ChecksTest,SeedCasesTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`cannot find symbol: class SourceCheck`).

- [ ] **Step 3: Implement**

```java
package eval.checks;

import eval.*;
import java.util.*;

/**
 * Deterministic (D11): each covered fact must be backed by a claim that cites a chunk of the same
 * document as one of the fact's gold chunks. Document-level on purpose: a different chunk of the
 * right document is fine (that is Groundedness's business), citing only some other document is
 * not. A fact Coverage did not cover is skipped, Coverage already fails that.
 */
public final class SourceCheck implements Check {
  @Override
  public String name() {
    return "Source";
  }

  @Override
  public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
    List<String> problems = new ArrayList<>();
    for (ExpectedFact fact : evalCase.facts()) {
      List<Claim> covering = state.covering(fact);
      if (covering.isEmpty()) {
        continue;
      }
      Set<String> goldDocuments = new LinkedHashSet<>();
      for (String chunkId : fact.chunks()) {
        goldDocuments.add(documentOf(chunkId));
      }
      Set<String> citedDocuments = new LinkedHashSet<>();
      for (Claim claim : covering) {
        if (claim.citations() != null) {
          for (String chunkId : claim.citations()) {
            citedDocuments.add(documentOf(chunkId));
          }
        }
      }
      if (citedDocuments.stream().noneMatch(goldDocuments::contains)) {
        problems.add(
            "\""
                + fact.fact()
                + "\" is covered only by claims citing "
                + citedDocuments
                + "; expected a chunk of "
                + goldDocuments);
      }
    }
    return problems.isEmpty() ? CheckResult.ok() : CheckResult.fail(String.join("; ", problems));
  }

  /** The document behind a chunk ID: the text before the first '#'. */
  private static String documentOf(String chunkId) {
    int hash = chunkId.indexOf('#');
    return hash < 0 ? chunkId : chunkId.substring(0, hash);
  }
}
```

In `Checks.registered` add `new Registered(new SourceCheck(), true)` after Groundedness. In the `CaseRunner` class comment change `(currently Refusal, Citation integrity and Coverage)` to `(currently Refusal, Citation integrity, Coverage, Groundedness and Source)`.

Create `eval/cases/multi-source.yaml`:

```yaml
- id: ms-geo-tcf-and-consent-mode
  question: "Can I put a GDPR configuration and a TCF configuration in the same geolocation ruleset, and which Google tag libraries does Google Consent Mode require?"
  category: multi-source
  expected_behavior: answer
  facts:
    - fact: "Configurations supporting TCF 2.2 should belong to a single ruleset, so a GDPR configuration and a TCF configuration should not be combined in one rule"
      chunks: [geolocation-rules#constraints]
      keywords: ["TCF"]
    - fact: "Google Consent Mode requires gtag.js or Google Tag Manager"
      chunks: [consent-mode#prerequisites]
      keywords: ["gtag.js"]
  source: authored
  owner: platform
  added: "2026-09-24"
```

- [ ] **Step 4: Run the full suite**

Run: `mvn -B -q test`
Expected: PASS, including `SeedCasesTest` (the loader accepts both gold chunk IDs and both keywords appear in their gold chunks). If the loader rejects an ID, list the real ones with `java -cp kb/target/classes kb.Chunker` and use the reported ID.

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/java/eval/checks/SourceCheck.java eval/src/main/java/eval/checks/Checks.java eval/src/main/java/eval/CaseRunner.java eval/cases/multi-source.yaml eval/src/test/java/eval/checks/SourceCheckTest.java eval/src/test/java/eval/checks/ChecksTest.java eval/src/test/java/eval/SeedCasesTest.java
git commit -m "feat: Source check and a multi-source seed case (unit 16)"
```

---

### Task 4: The 20 hand-labeled pairs (unit 15, data)

**Files:**
- Create: `eval/src/main/resources/calibration/labeled-sample.yaml`
- Test: `eval/src/test/java/eval/calibration/LabeledSampleFileTest.java` (create)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: a classpath resource `/calibration/labeled-sample.yaml`: a YAML list of maps with keys `name` (unique), `kind` (`unsupported` | `supported` | `hard-supported`), `supported` (boolean label), `passage` (text copied from `docs/`), `claim`. Task 5 reads it. Passages are inline so a team with other docs can supply its own file (`calibration.labeledSample`, Task 5).

These labels are the calibration's ground truth. They were drafted from the docs; **the user should review each label** before the numbers are trusted (mention it in the PR description).

- [ ] **Step 1: Write the failing shape test**

```java
package eval.calibration;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class LabeledSampleFileTest {
  private static List<Map<String, Object>> sample() throws IOException {
    try (Reader reader =
        new InputStreamReader(
            LabeledSampleFileTest.class.getResourceAsStream("/calibration/labeled-sample.yaml"),
            StandardCharsets.UTF_8)) {
      return new Yaml().load(reader);
    }
  }

  private static String collapse(String text) {
    return text.replaceAll("\\s+", " ").strip();
  }

  @Test
  void holdsTenUnsupportedFivePlainAndFiveHardSupportedPairs() throws Exception {
    Map<Object, Long> byKind =
        sample().stream().collect(Collectors.groupingBy(pair -> pair.get("kind"), Collectors.counting()));
    assertEquals(Map.of("unsupported", 10L, "supported", 5L, "hard-supported", 5L), byKind);
  }

  @Test
  void theLabelMatchesTheKind() throws Exception {
    for (Map<String, Object> pair : sample()) {
      boolean shouldBeSupported = !"unsupported".equals(pair.get("kind"));
      assertEquals(shouldBeSupported, pair.get("supported"), String.valueOf(pair.get("name")));
    }
  }

  @Test
  void everyPairIsCompleteAndNamesAreUnique() throws Exception {
    Set<Object> names = new HashSet<>();
    for (Map<String, Object> pair : sample()) {
      for (String key : List.of("name", "claim", "passage")) {
        assertTrue(
            pair.get(key) instanceof String text && !text.isBlank(),
            key + " missing in " + pair.get("name"));
      }
      assertTrue(names.add(pair.get("name")), "duplicate name " + pair.get("name"));
    }
  }

  @Test
  void everyPassageIsCopiedVerbatimFromTheDocs() throws Exception {
    StringBuilder docs = new StringBuilder();
    try (var files = Files.list(Path.of("docs"))) {
      for (Path file : files.filter(path -> path.toString().endsWith(".md")).toList()) {
        docs.append(collapse(Files.readString(file))).append(' ');
      }
    }
    for (Map<String, Object> pair : sample()) {
      assertTrue(
          docs.toString().contains(collapse((String) pair.get("passage"))),
          pair.get("name") + ": the passage is not text from docs/");
    }
  }
}
```

- [ ] **Step 2: Run, expect failure**

Run: `mvn -q -pl eval -am test -Dtest=LabeledSampleFileTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`NullPointerException`: the resource does not exist yet).

- [ ] **Step 3: Create `eval/src/main/resources/calibration/labeled-sample.yaml`**

```yaml
# Groundedness calibration (D16): does the judge agree with a hand label on whether the passage
# supports the claim? 10 subtly unsupported, 5 plain supported, 5 hard-supported.
# Passages are copied from docs/ so the file works without the knowledge base. Labels are ground
# truth: if the judge disagrees, change the judge prompt, never the label.

# ---- 10 subtly unsupported (target: the judge calls none of them supported) ----
- name: wrong-safari-version
  kind: unsupported
  supported: false
  passage: &bundle |
    Former versions of our standard script tags, bundle.js and bundle_legacy.js, are still supported. These are the starting browser versions that we support for each script:
    * bundle.js
      * Chrome: 69
      * Edge: 79
      * Firefox: 67
      * Opera: 51
      * Safari: 14
  claim: "bundle.js supports Safari starting from version 13."

- name: wrong-zonejs-version
  kind: unsupported
  supported: false
  passage: &libraries |
    All our script tags (loader, bundle and bundle_legacy) only support the following libraries starting on the version displayed:
    * zone.js: 0.11.4
    * prototype.js: 1.7.3
  claim: "All script tags support zone.js starting from version 0.12.0."

- name: swapped-chrome-version
  kind: unsupported
  supported: false
  passage: &loader |
    Loader.js is our standard script tag. It supports browsers starting from these versions:
    * Chrome: 43
    * Edge: 77
    * Firefox: 49
    * Opera: 31
    * Safari: 11
    * Yandex: 17.6
  claim: "Loader.js supports Chrome starting from version 49."

- name: custom-ab-split
  kind: unsupported
  supported: false
  passage: &internal |
    You can enable the internal A/B testing by selecting the "Activate with Usercentrics" option in the Admin Interface under Implementation / A/B Testing. When using Usercentrics to display your variants, they are always **evenly distributed** (e.g. 50:50 in case of two variants).
  claim: "With Usercentrics internal A/B testing you can choose a custom traffic split, such as 80:20."

- name: variant-set-after-cmp
  kind: unsupported
  supported: false
  passage: &thirdparty |
    * **UC_AB_VARIANT**: This variable defines what A/B variant you will be testing on. As this variable will be delivered by the Usercentrics Consent Management Platform to the A/B testing tool to display the different variants, it needs to be set before the Consent Management Platform is loaded.

    * **Script Order**: We recommend that you add your **A/B testing tool** script in the `<head>` section of your code before the Usercentrics Consent Management Platform, since the A/B testing tool needs to be loaded before the Consent Management Platform to do the **splitting** properly.
  claim: "The UC_AB_VARIANT variable must be set after the Usercentrics CMP has loaded."

- name: tool-after-cmp
  kind: unsupported
  supported: false
  passage: *thirdparty
  claim: "The A/B testing tool script should be placed after the Usercentrics CMP in the body of the page."

- name: default-for-all-customers
  kind: unsupported
  supported: false
  passage: &default |
    For new customers, Google Consent Mode is enabled by default.
  claim: "Google Consent Mode is enabled by default for all customers."

- name: added-exception
  kind: unsupported
  supported: false
  passage: &constraints |
    -   Configurations belonging to a ruleset cannot be unassigned from the company or permanently deleted
    -   Companies having rulesets cannot be deleted. You need to delete the ruleset first
    -   Configurations supporting TCF 2.2 should belong to a single ruleset and need to be implemented with the respective TCF Script Tag. Example - Do not combine GDPR and TCF configuration in one rule. It will not always work as expected **(not applicable to In-App SDK)**
  claim: "A company that has rulesets can be deleted as long as its configurations are unassigned first."

- name: extra-analytics-js
  kind: unsupported
  supported: false
  passage: &prereq |
    Consent Mode requires that you use gtag.js or Google Tag Manager. If you use older tags versions (like ga.js or analytics.js) you need to update to the latest tag versions first.
  claim: "Consent Mode requires gtag.js, Google Tag Manager or analytics.js."

- name: wrong-consent-signal
  kind: unsupported
  supported: false
  passage: &advertiser |
    When using Consent Mode, it's also possible to manage Advertiser Consent Mode. When enabled, Google will deduce the consent signals for ad_storage, ad_user_data and ad_personalisation from the TC String. It's recommended to enable the Advertiser Consent Mode when enabling Google Consent Mode.
  claim: "When Advertiser Consent Mode is enabled, Google deduces the analytics_storage consent signal from the TC String."

# ---- 5 plain supported ----
- name: plain-chrome-version
  kind: supported
  supported: true
  passage: *loader
  claim: "Loader.js supports Chrome starting from version 43."

- name: plain-even-split
  kind: supported
  supported: true
  passage: *internal
  claim: "With Usercentrics internal A/B testing the variants are always evenly distributed, for example 50:50 for two variants."

- name: plain-ruleset-delete
  kind: supported
  supported: true
  passage: *constraints
  claim: "A company that has rulesets cannot be deleted until the ruleset is deleted first."

- name: plain-variant-before-cmp
  kind: supported
  supported: true
  passage: *thirdparty
  claim: "The UC_AB_VARIANT variable must be set before the Usercentrics CMP is loaded."

- name: plain-default-for-new-customers
  kind: supported
  supported: true
  passage: *default
  claim: "Google Consent Mode is enabled by default for new customers."

# ---- 5 hard-supported: paraphrase, split across sentences, equivalent numbers ----
- name: hard-oldest-safari
  kind: hard-supported
  supported: true
  passage: *bundle
  claim: "The oldest Safari version that bundle.js supports is 14."

- name: hard-split-across-sentences
  kind: hard-supported
  supported: true
  passage: *advertiser
  claim: "Enabling Advertiser Consent Mode is recommended, and it lets Google derive the ad_user_data signal from the TC String."

- name: hard-half-of-visitors
  kind: hard-supported
  supported: true
  passage: *internal
  claim: "With two variants in the internal A/B test, each variant is shown to half of the visitors."

- name: hard-gdpr-and-tcf
  kind: hard-supported
  supported: true
  passage: *constraints
  claim: "A GDPR configuration and a TCF configuration should not be put in the same ruleset."

- name: hard-update-analytics-js
  kind: hard-supported
  supported: true
  passage: *prereq
  claim: "Sites that still use analytics.js must move to a newer tag version before they can use Consent Mode."
```

- [ ] **Step 4: Run the shape test**

Run: `mvn -q -pl eval -am test -Dtest=LabeledSampleFileTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. If `everyPassageIsCopiedVerbatimFromTheDocs` names a pair, the passage differs from `docs/` (a curly quote, a dash, a trailing marker): copy the exact text from the doc into the passage. Never edit a claim or a label to make this pass.

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/resources/calibration/labeled-sample.yaml eval/src/test/java/eval/calibration/LabeledSampleFileTest.java
git commit -m "feat: 20 hand-labeled pairs for the Groundedness judge (unit 15 data)"
```

---

### Task 5: Run the sample inside the harness command (unit 15, code)

**Files:**
- Create: `eval/src/main/java/eval/calibration/LabeledSample.java`
- Modify: `eval/src/main/java/eval/SuiteReport.java`, `eval/src/main/java/eval/ConsoleReport.java`, `eval/src/main/java/eval/EvalConfig.java`, `eval/src/main/java/eval/Harness.java`
- Test: `eval/src/test/java/eval/calibration/LabeledSampleTest.java` (create), `eval/src/test/java/eval/EvalConfigTest.java`, `eval/src/test/java/eval/SuiteReportTest.java`, `eval/src/test/java/eval/HarnessTest.java` (modify)

**Interfaces:**
- Consumes: `Judge.supports(claim, passage)` (Task 1); the resource `/calibration/labeled-sample.yaml` (Task 4); existing `TrapPairs` (same pattern: bundled resource, `Path` override, `Reader` core), `SuiteReport.Calibration(boolean ran, List<TrapResult> pairs)`.
- Produces:
  - `LabeledSample.PairResult(String name, String kind, boolean labeledSupported, Boolean judged, String detail)` with `boolean agreed()` (false when `judged` is null).
  - `LabeledSample.Result(List<PairResult> pairs)` with `NONE`, `long agreed()`, `double agreement()`, `long unsupportedPairs()`, `long falseSupported()`, `long falseUnsupported()`, `boolean agreementMet()` (≥ 90%, integer maths).
  - `LabeledSample.runBundled(Judge)`, `run(Path, Judge)`, `run(Reader, Judge)`.
  - `SuiteReport.Calibration(boolean ran, List<TrapResult> pairs, LabeledSample.Result groundedness)` plus the old two-argument constructor (groundedness = `NONE`); `SKIPPED` uses `NONE`.
  - `EvalConfig.labeledSampleFile()`: `Path` from `calibration.labeledSample`, or null for the bundled sample.
  - JSON: `calibration.groundedness = {pairs:[{name,kind,labeledSupported,judged,detail,agreed}], agreement, unsupportedPairs, falseSupported, falseUnsupported}`.

- [ ] **Step 1: Write the failing `LabeledSampleTest`**

```java
package eval.calibration;

import static org.junit.jupiter.api.Assertions.*;

import eval.Judge;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LabeledSampleTest {
  private static final String SAMPLE =
      """
- {name: wrong, kind: unsupported, supported: false, passage: "Safari 14", claim: "Safari 13"}
- {name: right, kind: supported, supported: true, passage: "Safari 14", claim: "Safari 14"}
""";

  private static LabeledSample.Result run(String reply) {
    return LabeledSample.run(new StringReader(SAMPLE), new Judge((system, user) -> reply));
  }

  @Test
  void aJudgeThatMatchesTheLabelsAgreesOnEverything() {
    var judge = new Judge((system, user) -> user.contains("Claim: Safari 14") ? "YES" : "NO");
    var result = LabeledSample.run(new StringReader(SAMPLE), judge);
    assertEquals(2, result.agreed());
    assertEquals(0, result.falseSupported());
    assertTrue(result.agreementMet());
  }

  @Test
  void yesToEverythingIsAFalseSupportedOnTheUnsupportedPair() {
    var result = run("YES");
    assertEquals(1, result.falseSupported());
    assertEquals(0, result.falseUnsupported());
    assertEquals(0.5, result.agreement(), 1e-9);
    assertFalse(result.agreementMet());
    assertEquals("labeled UNSUPPORTED, judge said SUPPORTED", result.pairs().get(0).detail());
  }

  @Test
  void noToEverythingIsAFalseUnsupportedAndNotAFalseSupported() {
    var result = run("NO");
    assertEquals(0, result.falseSupported());
    assertEquals(1, result.falseUnsupported());
  }

  @Test
  void aJudgeErrorNeverCountsAsAgreeingNotEvenOnAnUnsupportedPair() {
    var result = run("Maybe");
    assertEquals(0, result.agreed());
    assertEquals(0, result.falseSupported());
    assertNull(result.pairs().get(0).judged());
    assertTrue(result.pairs().get(0).detail().startsWith("error:"), result.pairs().get(0).detail());
  }

  @Test
  void ninetyPercentAgreementIsTheLine() {
    assertTrue(withAgreed(18, 20).agreementMet());
    assertFalse(withAgreed(17, 20).agreementMet());
  }

  @Test
  void anEmptySampleHasNoTargetToMiss() {
    assertTrue(LabeledSample.run(new StringReader("[]\n"), new Judge((s, u) -> "YES")).agreementMet());
    assertTrue(LabeledSample.Result.NONE.agreementMet());
    assertEquals(0, LabeledSample.Result.NONE.falseSupported());
  }

  private static LabeledSample.Result withAgreed(int agreed, int total) {
    List<LabeledSample.PairResult> pairs = new ArrayList<>();
    for (int index = 0; index < total; index++) {
      pairs.add(new LabeledSample.PairResult("p" + index, "supported", true, index < agreed, null));
    }
    return new LabeledSample.Result(pairs);
  }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `mvn -q -pl eval -am test -Dtest=LabeledSampleTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL (`cannot find symbol: class LabeledSample`).

- [ ] **Step 3: Implement `LabeledSample`**

```java
package eval.calibration;

import com.fasterxml.jackson.annotation.JsonProperty;
import eval.Judge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/**
 * Measures the Groundedness judge against hand-labeled claim and passage pairs (D16). The two kinds
 * of miss are not equal: a false "supported" lets a wrong claim through silently, so it fails the
 * run; a false "unsupported" only fails a good answer, so it is counted and reported, not gated.
 */
public final class LabeledSample {
  private static final String BUNDLED = "/calibration/labeled-sample.yaml";

  /** {@code judged} is null when the judge threw or answered unclearly: never an agreement. */
  public record PairResult(
      String name, String kind, boolean labeledSupported, Boolean judged, String detail) {
    @JsonProperty("agreed")
    public boolean agreed() {
      return judged != null && judged == labeledSupported;
    }
  }

  public record Result(List<PairResult> pairs) {
    public static final Result NONE = new Result(List.of());

    public long agreed() {
      return pairs.stream().filter(PairResult::agreed).count();
    }

    @JsonProperty("agreement")
    public double agreement() {
      return pairs.isEmpty() ? 1.0 : agreed() / (double) pairs.size();
    }

    @JsonProperty("unsupportedPairs")
    public long unsupportedPairs() {
      return pairs.stream().filter(pair -> !pair.labeledSupported()).count();
    }

    /** Labeled unsupported, judged supported: the miss that lets a wrong claim through. */
    @JsonProperty("falseSupported")
    public long falseSupported() {
      return pairs.stream()
          .filter(pair -> !pair.labeledSupported() && Boolean.TRUE.equals(pair.judged()))
          .count();
    }

    @JsonProperty("falseUnsupported")
    public long falseUnsupported() {
      return pairs.stream()
          .filter(pair -> pair.labeledSupported() && Boolean.FALSE.equals(pair.judged()))
          .count();
    }

    /** At least 90% agreement; integer maths so 18 of 20 is exactly on the line. */
    public boolean agreementMet() {
      return agreed() * 10 >= pairs.size() * 9L;
    }
  }

  /** Runs the sample shipped inside the jar. */
  public static Result runBundled(Judge judge) throws IOException {
    try (Reader reader =
        new InputStreamReader(LabeledSample.class.getResourceAsStream(BUNDLED), StandardCharsets.UTF_8)) {
      return run(reader, judge);
    }
  }

  public static Result run(Path file, Judge judge) throws IOException {
    try (Reader reader = Files.newBufferedReader(file)) {
      return run(reader, judge);
    }
  }

  /** A judge that throws on a pair counts as not agreeing on it. */
  public static Result run(Reader source, Judge judge) {
    List<Map<String, Object>> entries = new Yaml().load(source);
    List<PairResult> results = new ArrayList<>();
    for (Map<String, Object> entry : entries == null ? List.<Map<String, Object>>of() : entries) {
      boolean labeledSupported = (Boolean) entry.get("supported");
      Boolean judged;
      String detail = null;
      try {
        judged = judge.supports((String) entry.get("claim"), (String) entry.get("passage"));
        if (judged != labeledSupported) {
          detail = "labeled " + word(labeledSupported) + ", judge said " + word(judged);
        }
      } catch (IllegalStateException e) {
        judged = null;
        detail = "error: " + e.getMessage();
      }
      results.add(
          new PairResult(
              (String) entry.get("name"),
              (String) entry.get("kind"),
              labeledSupported,
              judged,
              detail));
    }
    return new Result(results);
  }

  private static String word(boolean supported) {
    return supported ? "SUPPORTED" : "UNSUPPORTED";
  }
}
```

- [ ] **Step 4: Run `LabeledSampleTest`, expect PASS**

Run: `mvn -q -pl eval -am test -Dtest=LabeledSampleTest -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 5: Write the failing report and config tests**

Add to `SuiteReportTest` (it already imports `SuiteReport`, `List`, JUnit assertions; add `import eval.calibration.LabeledSample;` if missing):

```java
  @Test
  void anUnsupportedPairJudgedSupportedFailsTheRun() {
    var missed =
        new LabeledSample.PairResult(
            "u1", "unsupported", false, true, "labeled UNSUPPORTED, judge said SUPPORTED");
    var report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(),
            new SuiteReport.Calibration(true, List.of(), new LabeledSample.Result(List.of(missed))),
            List.of());
    assertTrue(
        report.exitReasons().stream()
            .anyMatch(reason -> reason.contains("groundedness calibration: 1 of 1 unsupported pair(s) judged supported")),
        report.exitReasons().toString());
  }

  @Test
  void agreementBelowNinetyPercentFailsTheRun() {
    var agreeing = new LabeledSample.PairResult("a", "supported", true, true, null);
    var disagreeing = new LabeledSample.PairResult("b", "supported", true, false, "labeled SUPPORTED, judge said UNSUPPORTED");
    var report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(),
            new SuiteReport.Calibration(true, List.of(), new LabeledSample.Result(List.of(agreeing, disagreeing))),
            List.of());
    assertTrue(
        report.exitReasons().stream().anyMatch(reason -> reason.contains("groundedness calibration: agreement 50.0%")),
        report.exitReasons().toString());
  }

  @Test
  void aSkippedOrEmptyGroundednessSampleAddsNoExitReason() {
    var report = new SuiteReport("r", "http://x", 0.90, List.of(), SuiteReport.Calibration.SKIPPED, List.of());
    assertTrue(report.exitReasons().stream().noneMatch(reason -> reason.contains("groundedness")));
  }
```

Add to `EvalConfigTest` (add `@TempDir Path dir;`, `java.nio.file.*`, `org.junit.jupiter.api.io.TempDir` if the class does not have them):

```java
  @Test
  void labeledSampleFileIsNullByDefaultAndResolvedAgainstTheConfigFolderWhenSet() throws Exception {
    String base = "endpoint: http://x\npassFloor: 0.9\ncategories: [out-of-scope]\njudgeModel: m\n";
    Path plain = Files.writeString(dir.resolve("plain.yaml"), base);
    assertNull(EvalConfig.loadFile(plain, null).labeledSampleFile());
    Path custom =
        Files.writeString(dir.resolve("custom.yaml"), base + "calibration:\n  labeledSample: pairs.yaml\n");
    assertEquals(dir.resolve("pairs.yaml"), EvalConfig.loadFile(custom, null).labeledSampleFile());
  }
```

- [ ] **Step 6: Implement the report, config and console changes**

`SuiteReport.java`: import `eval.calibration.LabeledSample`, replace the `Calibration` record:

```java
    /** Judge trap pairs and the Groundedness sample: {@code ran} false means --skip-calibration. */
    public record Calibration(boolean ran, List<TrapResult> pairs, LabeledSample.Result groundedness) {
        public static final Calibration SKIPPED = new Calibration(false, List.of(), LabeledSample.Result.NONE);

        public Calibration(boolean ran, List<TrapResult> pairs) {
            this(ran, pairs, LabeledSample.Result.NONE);
        }

        @JsonProperty("misses")
        public long misses() { return pairs.stream().filter(pair -> !pair.passed()).count(); }
    }
```

In `exitReasons()`, after the trap-pair reason:

```java
        LabeledSample.Result sample = calibration.groundedness();
        if (sample.falseSupported() > 0) {
            reasons.add("groundedness calibration: " + sample.falseSupported() + " of " + sample.unsupportedPairs() + " unsupported pair(s) judged supported");
        }
        if (!sample.agreementMet()) {
            reasons.add(String.format("groundedness calibration: agreement %.1f%% is below the 90%% target", sample.agreement() * 100));
        }
```

`EvalConfig.java`: turn the body of `trapPairsFile()` into a shared helper:

```java
  /** The {@code calibration.trapPairs} file, or null when the bundled trap pairs should be used. */
  Path trapPairsFile() {
    return calibrationFile("trapPairs");
  }

  /** The {@code calibration.labeledSample} file, or null when the bundled sample should be used. */
  Path labeledSampleFile() {
    return calibrationFile("labeledSample");
  }

  private Path calibrationFile(String key) {
    Object block = raw.get("calibration");
    if (block == null) {
      return null;
    }
    if (!(block instanceof Map<?, ?> calibration)) {
      throw new IllegalArgumentException(fileName + ": 'calibration' must be a map");
    }
    Object value = calibration.get(key);
    return value == null || value.toString().isBlank() ? null : baseDir.resolve(value.toString());
  }
```

`ConsoleReport.java`: import `eval.calibration.LabeledSample`; at the end of `printCalibration` (after the trap-pair summary line) add:

```java
    LabeledSample.Result sample = calibration.groundedness();
    if (sample.pairs().isEmpty()) {
      return;
    }
    out.println("Groundedness calibration");
    for (LabeledSample.PairResult pair : sample.pairs()) {
      if (!pair.agreed()) {
        out.println("  MISS " + pair.name() + " (" + pair.detail() + ")");
      }
    }
    out.printf(
        "  agreement %d/%d (%.0f%%), target 90%%%n",
        sample.agreed(), sample.pairs().size(), sample.agreement() * 100);
    out.printf(
        "  unsupported judged supported: %d of %d (target 0)%n",
        sample.falseSupported(), sample.unsupportedPairs());
    out.printf("  supported judged unsupported: %d (no target)%n", sample.falseUnsupported());
```

The early `return` above sits after the "skipped" early return, so nothing else in the method follows it.

`Harness.java`: after the trap-file precheck add

```java
    Path sampleFile = config.labeledSampleFile(); // null: use the sample bundled in the jar
    if (!skipCalibration && sampleFile != null && !Files.isReadable(sampleFile)) {
      out.println("ERROR: cannot read the labeled Groundedness sample at " + sampleFile);
      out.println("Restore the file, or pass --skip-calibration to run without it.");
      return 2;
    }
```

and replace the `calibration` construction with

```java
    SuiteReport.Calibration calibration = SuiteReport.Calibration.SKIPPED;
    if (!skipCalibration) {
      List<TrapPairs.TrapResult> trapResults =
          trapFile == null ? TrapPairs.runBundled(judge) : TrapPairs.run(trapFile, judge);
      LabeledSample.Result groundedness =
          sampleFile == null ? LabeledSample.runBundled(judge) : LabeledSample.run(sampleFile, judge);
      calibration = new SuiteReport.Calibration(true, trapResults, groundedness);
    }
```

(import `eval.calibration.LabeledSample`).

- [ ] **Step 7: Update `HarnessTest` and add its new tests**

Existing tests fake the judge with "YES" for everything, which would now miss the bundled sample's unsupported pairs. Every test that does not test the sample must run with an empty one:

- add a helper next to `trapFile`:

```java
  private void labeledSampleFile(String yaml) throws IOException {
    Files.createDirectories(root.resolve("calibration"));
    Files.writeString(root.resolve("calibration/labeled-sample.yaml"), yaml);
  }
```

- in `setUp` call `labeledSampleFile("[]\n");` before `config(...)`;
- in `config(String body)` extend the calibration block: `"calibration:\n  trapPairs: calibration/trap-pairs.yaml\n  labeledSample: calibration/labeled-sample.yaml\n"`.

Add:

```java
  @Test
  void anUnsupportedPairJudgedSupportedFailsTheRunAndIsInTheReport() throws Exception {
    cases(OOS);
    labeledSampleFile("- {name: u1, kind: unsupported, supported: false, passage: P, claim: C}\n");
    int[] code = new int[1];
    String output = out(code)[0]; // the fake judge says YES to everything
    assertEquals(1, code[0], output);
    assertTrue(output.contains("MISS u1 (labeled UNSUPPORTED, judge said SUPPORTED)"), output);
    assertTrue(output.contains("groundedness calibration: 1 of 1 unsupported pair(s) judged supported"), output);
    assertTrue(output.contains("PASS") && output.contains("oos-1"), output);
    var reportMatcher = java.util.regex.Pattern.compile("Report: (\\S+)").matcher(output);
    assertTrue(reportMatcher.find(), output);
    var json =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(root.resolve(reportMatcher.group(1)).toFile());
    assertEquals(1, json.get("calibration").get("groundedness").get("falseSupported").asInt());
    assertEquals("u1", json.get("calibration").get("groundedness").get("pairs").get(0).get("name").asText());
  }

  @Test
  void skipCalibrationMakesNoSampleJudgeCalls() throws Exception {
    cases(OOS);
    labeledSampleFile("- {name: u1, kind: unsupported, supported: false, passage: P, claim: C}\n");
    var judgeCalls = new int[1];
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[] {"--skip-calibration"},
            root,
            new PrintStream(buf),
            model ->
                (system, user) -> {
                  judgeCalls[0]++;
                  return "YES";
                });
    assertEquals(0, code, buf.toString());
    assertEquals(0, judgeCalls[0]);
  }

  @Test
  void missingSampleFileWithoutTheFlagExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    Files.delete(root.resolve("calibration/labeled-sample.yaml"));
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("labeled-sample.yaml") && output.contains("--skip-calibration"), output);
    assertEquals(0, requests.get());
  }
```

- [ ] **Step 8: Run the whole suite and fix what the bundled sample breaks**

Run: `mvn -B -q test`
Expected: PASS for everything above. Tests that use the default (bundled) calibration with a fake "YES" judge (for example `HarnessConfigFileTest`, which loads a config without a `calibration` block) now exit 1 because the fake judge calls every bundled unsupported pair supported. For each such test, keep its intent and add an empty `calibration.labeledSample` file to the test's config folder (`calibration:\n  labeledSample: empty-sample.yaml` plus a file containing `[]`), or pass `--skip-calibration` where the test is not about calibration. Do not weaken any production check.

- [ ] **Step 9: Commit**

```bash
git add eval/src/main/java eval/src/test/java
git commit -m "feat: run the Groundedness sample inside the harness command (unit 15)"
```

---

### Task 6: Measure it live, record it, document it

**Files:**
- Create: `calibration/calibration-notes.md`
- Modify: `eval/config.yaml`, `grilling-decisions.md`, `usercentrics-tech-stack-and-repo-structure.md`
- Possibly modify: `eval/src/main/java/eval/Judge.java` (`SUPPORTS_SYSTEM` wording only)

**Interfaces:** consumes everything above; produces the recorded numbers unit 15 requires.

- [ ] **Step 1: Ask the user to run the live harness** (needs their `OPENROUTER_API_KEY`; the implementer must not read or print it)

Two terminals from the repo root:

```bash
export OPENROUTER_API_KEY=...              # the user's own key
mvn -q -DskipTests package
java -jar assistant/target/assistant.jar    # terminal 1
java -jar eval/target/eval.jar              # terminal 2
```

Expected shape (numbers are the measurement):

```
Judge calibration
  ok   ... (7 trap pairs)
Groundedness calibration
  MISS <pair> (labeled UNSUPPORTED, judge said SUPPORTED)     # only if the judge misses
  agreement N/20 (P%), target 90%
  unsupported judged supported: K of 10 (target 0)
  supported judged unsupported: M (no target)
...
Pass rate: ...
```

Ask the user to paste the console output. Note also whether `ms-geo-tcf-and-consent-mode` passes; a FAIL there is a finding about the stub assistant, report it, do not tune the case to pass.

- [ ] **Step 2: If any unsupported pair was judged supported, or agreement is below 90%, fix the judge prompt, not the labels**

Edit only the wording of `SUPPORTS_SYSTEM` in `Judge.java` (for example name the exact miss: "an added exception", "'all' where the passage says 'new'"), rebuild, and ask the user to re-run. Repeat until 0 of 10 and ≥ 90%. Record every attempt in the notes (Step 3). If the judge still misses after three prompt revisions, stop and report which pairs, with the judge's replies, instead of loosening the target.

- [ ] **Step 3: Write `calibration/calibration-notes.md`**

Fill every value from the pasted run output, nothing estimated:

```markdown
# Judge calibration notes

Run <date>, judge model `anthropic/claude-opus-4.8`, reasoning effort low, temperature 0.

## Groundedness (calibrated, D16)

- Sample: 20 hand-labeled pairs in `eval/src/main/resources/calibration/labeled-sample.yaml` (10 subtly unsupported, 5 plain supported, 5 hard-supported).
- Overall agreement: <N>/20 (<P>%), target at least 90%.
- Unsupported pairs judged supported: <K> of 10, target 0.
- Supported pairs judged unsupported: <M> (counted, no target).
- Prompt changes made to reach the targets: <none | each revision: the pair that was missed, the wording changed, the result of the re-run>.

## Not calibrated in v1

- Coverage by judge (the `agree` and `covering` questions): only the 7 trap pairs in `eval/src/main/resources/calibration/trap-pairs.yaml` guard it. Calibrating it against labeled pairs is the first thing to add.
- Relevance (unit 17, not built yet).

## Limits

- The labels were drafted by the implementer from `docs/` and reviewed by <the user | pending review>.
- 20 pairs measure this prompt on this documentation. A new team should add pairs of its own (`calibration.labeledSample` in its config).
```

- [ ] **Step 4: Documentation edits**

- `eval/config.yaml`: change the `judgeModel` comment to `Judge model for Coverage and Groundedness (D15, D40). ...`; next to the existing `calibration.trapPairs` comment add: `#   labeledSample: calibration/labeled-sample.yaml   # the Groundedness sample; default: the 20 pairs bundled in the jar`.
- `grilling-decisions.md`, after D42 add (read the file's tail first and keep its numbering and tone):

  `43. **Groundedness, Source and the Groundedness sample as built (units 13-16):** Groundedness checks every claim in the answer, not only covering ones: a covering claim citing a gold chunk of its fact passes with no LLM call; every other claim goes to the judge, one claim and one cited chunk per call, and passes when any one cited chunk supports it. A claim with no existing cited chunk is skipped (Citation integrity owns that failure). Source is per covered fact and document-level: some covering claim must cite a chunk of a document that holds one of the fact's gold chunks. The 20-pair Groundedness sample uses inline passages so any team can supply its own; it runs in the single command with the trap pairs, and a miss (any of the 10 unsupported pairs judged supported, or agreement under 90%) fails the exit code. \`calibration.labeledSample\` overrides the bundled sample and \`--skip-calibration\` skips both.`
- `usercentrics-tech-stack-and-repo-structure.md` line 19: `--skip-calibration` skips "the judge trap pairs and the Groundedness sample that otherwise run first".

- [ ] **Step 5: Full suite, then format the touched files as a separate commit**

Run: `mvn -B -q test` (all green). Commit the notes and docs:

```bash
git add calibration/calibration-notes.md eval/config.yaml grilling-decisions.md usercentrics-tech-stack-and-repo-structure.md eval/src/main/java/eval/Judge.java
git commit -m "docs: record the Groundedness calibration and decision D43"
```

Then format every Java file this branch changed (`git diff --name-only origin/main -- '*.java'`) with google-java-format 1.25.2 (the jar is in the session scratchpad as `gjf.jar`), in its own commit so reviewers can skip it:

```bash
java --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
     -jar "$GJF_JAR" --replace $(git diff --name-only origin/main -- '*.java')
mvn -B -q test
git commit -am "style: google-java-format on the files changed in units 13-16"
```

(`SuiteReport.java` and `KnowledgeBase.java`-style 4-space files reformat whole-file; that is expected and is why this commit is separate.)

---

## Self-review

- **Spec coverage:** unit 13 (gold citation → no LLM call, detail "grounded (gold chunk)", real non-gold chunk passed on): Task 2 tests 1–2. Unit 14 (one claim plus chunk per call, unsupported fails with claim and chunk in detail, mismatch alone never fails, only existing IDs judged): Task 2 tests 2–5. Unit 15 (20 pairs 10/5/5, agreement, false-supported, false-unsupported counted, notes, uncalibrated named): Tasks 4–6. Unit 16 (document match, deterministic): Task 3. Single-command rule (D41) and config override (D42): Task 5.
- **Placeholder scan:** the only fill-ins are the measured numbers in `calibration-notes.md`, which cannot exist before the live run and are marked as run-derived in Task 6.
- **Type consistency:** `Judge.supports(String, String)`, `CheckResult.ok(String)`, `GroundednessCheck(KnowledgeBase, Judge)`, `SourceCheck()`, `LabeledSample.Result/PairResult`, `SuiteReport.Calibration(boolean, List<TrapResult>, LabeledSample.Result)` and `EvalConfig.labeledSampleFile()` are used identically across tasks; check names `"Groundedness"` and `"Source"` match `ChecksTest`.
- **Review Focus:** items 1–5 each map to a named test (Task 2 tests 5–8, Task 2 test 4, Task 1 and Task 5 tests, Task 3 tests, Task 5 Harness tests).

## Rulings to confirm (they change behavior beyond the build-order text)

1. **Groundedness checks every claim, not only covering ones** (the plan doc says "for each claim", the unit text says "for a covering claim"). More judge calls per case, but a wrong extra claim cannot slip through. Reverting means restricting the loop to `state.covering` claims.
2. **The Groundedness sample gates the exit code** and runs on every full run (20 more judge calls). `--skip-calibration` skips it.
3. **A new seed case, `ms-geo-tcf-and-consent-mode`,** joins the eval set (7 → 8 cases). If the stub answers it badly, the pass rate drops below the 0.90 floor: that is the harness working, and it is the first live evidence for Source.
4. **The 20 labels were drafted from the docs by the implementer:** the user should review them; the numbers mean nothing until they do.

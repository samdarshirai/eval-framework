# Eval Harness — Plan A: Citation integrity and Coverage (units 9–12) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two new gating checks (Citation integrity, Coverage) and the first LLM judge, so the harness rejects fabricated or missing citations and answers that do not state the expected facts.

**Architecture:** `eval/` gains a `Judge` (two narrow prompts over the `Llm` interface), a per-case `CaseState` that Coverage fills with *covering claims* for later checks (Groundedness and Source, plan B), and two `Check` classes added to the one registration list. `Check.run` takes the state as a third argument. `Harness` builds the judge from `judgeModel` in `eval/config.yaml`; tests inject a fake `Llm`. `OpenRouterLlm` gets an optional `reasoning.effort` for the "low effort" judge calls (D24).

**Tech Stack:** Java 21, Maven multi-module, JUnit 5, Jackson, SnakeYAML. No new dependencies; `eval` gains a dependency on the existing `llm` module.

**Spec:** `usercentrics-eval-harness-plan.md` (Step 4: Coverage, Citation integrity, Models, Judge calibration trap pairs), `build-order.md` units 9–12, `grilling-decisions.md` D5, D7, D14, D15, D20, D24, D26, D30, D39, D40, `CONTEXT.md`. Previous plan: `docs/superpowers/plans/2026-09-24-eval-harness-units-1-8.md` (built, merged in PR #1).

**Later plans (not in this one):** B = units 13–16 (Groundedness, 20-pair calibration, Source); C = 18–19 (baseline, re-run); D = 21–22 (28 cases, doc-hash warning); E = 17, 20 (Relevance, cost); F = 23–26 (PATTERN.md, SCALE-PLAN.md, README, live-change rehearsal).

## Global Constraints

- Java 21. No new dependencies. `eval/` stays plain Java and never depends on `assistant/` (D28, D37); it may depend on `kb` and (new) `llm`.
- Temperature 0 for every judge call (D14); `OpenRouterLlm` already sends it with `provider.require_parameters: true` (D39). Judge model is a separate config value from the assistant model (D15); judge is `anthropic/claude-opus-4.8` (D40).
- Coverage confirm uses the main judge, no third model (D24). A keyword hit never passes on its own (D24).
- Citation integrity is deterministic, makes no LLM call, and runs before Groundedness (D26). Refusal stays first in the list and makes no LLM call (D25).
- No free-text answer field in the contract (D1). Chunk IDs are `<doc>#<heading-slug>` (D6, D33).
- Every case is fully answerable or fully out-of-scope (D7). A new case is admitted only if it adds a distinct failure (D20).
- `eval/` tests run with the repo root as working directory (surefire `workingDirectory`). CI runs `mvn -B test` with no API key, so no test may call a real LLM.
- Commit messages are plain: no `Co-Authored-By` or `Claude-Session` trailer lines (user instruction, confirmed 2026-09-24, overrides the harness default).

## Review Focus

Inputs the spec implies but no unit's criteria pin down. Each has a test in the owning task.

1. **Judge output is malformed or the call fails** (prose instead of YES/NO, out-of-range index, HTTP error): that check fails with `check error: ...`, the case fails, the run continues and writes a report. (Task 4, Task 5)
2. **Refused answer or zero claims when facts are expected:** Coverage fails with zero LLM calls; Citation integrity passes vacuously. (Task 5)
3. **Claim with `citations` missing (null) or empty:** "no citation", never an NPE. (Task 3)
4. **One claim citing a real and a fabricated ID:** fails, naming the fabricated ID. (Task 3)
5. **Judge replies `Yes.`, `no`, `YES - the claim matches` or `**YES**`:** accepted (first word, case-insensitive, leading markdown/quotes and trailing punctuation ignored). Anything else is an error, not a silent NO. (Task 4)
6. **Missing `judgeModel` in config or missing `OPENROUTER_API_KEY`:** exit 2 with one clear message, before any assistant call. (Task 4)
7. **Keyword substring (`14` inside `141`):** still a candidate; the judge decides. Keywords are necessary, not sufficient (D24). (Task 5)
8. **A case keyword that appears in none of the fact's gold chunks:** the fact could never be covered by a faithful claim, so the shipped cases are tested for it. (Task 6)
9. **Reasoning tokens use up `max_tokens` and the judge reply comes back empty:** with an effort set, `max_tokens` is raised; an empty reply is a `check error`, never a NO. (Task 1, Task 7 Step 5)
10. **The keyword-less `covering` prompt fooled by negation, an exception, or a different value:** two covering trap pairs run against the real judge. (Task 7)
11. **A false-premise question whose wrong-order or negated answer contains every keyword:** a seed case where only the judge can tell the correction from the premise. (Task 6)

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `llm/src/main/java/llm/OpenRouterLlm.java` | modify | optional `reasoning.effort`, larger `max_tokens` when set |
| `llm/src/test/java/llm/OpenRouterLlmTest.java` | modify | effort in request body |
| `eval/src/main/java/eval/CaseState.java` | create | per-case scratch: covering claims per fact |
| `eval/src/main/java/eval/checks/Check.java` | modify | `run(c, a, state)` |
| `eval/src/main/java/eval/checks/RefusalCheck.java` | modify | new signature only |
| `eval/src/main/java/eval/checks/CitationIntegrityCheck.java` | create | unit 9 |
| `eval/pom.xml` | modify | depend on `llm` |
| `eval/src/main/java/eval/Judge.java` | create | `agree(fact, claim)`, `covering(fact, claims)` |
| `eval/src/main/java/eval/checks/CoverageCheck.java` | create | units 10–12 |
| `eval/src/main/java/eval/checks/Checks.java` | modify | `registered(kb)`, then `registered(kb, judge)`; Refusal, Citation integrity, Coverage |
| `eval/src/main/java/eval/Harness.java` | modify | keep the knowledge base, build judge, pass state, catch check errors |
| `eval/src/main/java/eval/calibration/TrapPairs.java` | create | runs `calibration/trap-pairs.yaml` (agree and covering pairs) against the judge |
| `calibration/trap-pairs.yaml` | create | 5 agree pairs (3 negation traps, 2 paraphrases) and 2 covering pairs |
| `eval/config.yaml` | modify | `judgeModel` |
| `eval/cases/single-source.yaml` | modify | 2 seed cases: a keyword-less fact, and a two-fact case with alternative gold chunks (Task 6) |
| `eval/cases/false-premise.yaml` | create | 1 negation-sensitive seed case (Task 6) |
| tests | create/modify | one per class above; `HarnessTest` gets a `config(...)` helper plus end-to-end cases (fabricated citation, negated claim, judge failure, missing key/model); `SeedCasesTest` loads the real cases against the real docs |

---

### Task 1: `OpenRouterLlm` accepts a reasoning effort (prerequisite for unit 11)

**Files:**
- Modify: `llm/src/main/java/llm/OpenRouterLlm.java`
- Test: `llm/src/test/java/llm/OpenRouterLlmTest.java`

**Interfaces:**
- Produces: `OpenRouterLlm(String model, String apiKey, String effort)` and `static OpenRouterLlm fromEnv(String model, String effort)`; `effort` may be `null` (no `reasoning` field, `max_tokens` 1024). The existing 2-arg constructor and `fromEnv(model)` keep working (effort `null`). Package-private `static String requestBody(String model, String system, String user, String effort)`; the 3-arg form delegates with `null`.

Verified 2026-09-24 via `GET /api/v1/models/anthropic/claude-opus-4.8/endpoints`: `azure/global` and `azure/us` list both `temperature` and `reasoning` as supported parameters, so `require_parameters: true` still routes to Azure with `reasoning` added.

Reasoning tokens count toward `max_tokens`. With a reasoning effort set, 1024 may be used up before the one-word answer appears, and the judge would see an empty reply. So the budget is 4096 when an effort is set. This is an assumption until the live run in Task 7 Step 5.

- [ ] **Step 1: Write the failing test** (append to `OpenRouterLlmTest`)

```java
@Test void effortIsSentAsReasoningEffortWhenSet() throws Exception {
    var n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(OpenRouterLlm.requestBody("m", "s", "u", "low"));
    assertEquals("low", n.get("reasoning").get("effort").asText());
    assertEquals(0, n.get("temperature").asInt());
    assertTrue(n.get("provider").get("require_parameters").asBoolean());
}

@Test void effortRaisesMaxTokensSoReasoningCannotEatTheAnswer() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    assertEquals(1024, mapper.readTree(OpenRouterLlm.requestBody("m", "s", "u")).get("max_tokens").asInt());
    assertEquals(4096, mapper.readTree(OpenRouterLlm.requestBody("m", "s", "u", "low")).get("max_tokens").asInt());
}

@Test void noReasoningFieldWhenEffortIsNull() throws Exception {
    var n = new com.fasterxml.jackson.databind.ObjectMapper().readTree(OpenRouterLlm.requestBody("m", "s", "u"));
    assertFalse(n.has("reasoning"));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -pl llm test -Dtest=OpenRouterLlmTest`
Expected: FAIL, compilation error (`requestBody` with 4 arguments does not exist).

- [ ] **Step 3: Implement**

In `OpenRouterLlm`: replace the `private final String model, apiKey;` line and constructors, and change `requestBody`.

```java
private final String model, apiKey, effort;

public OpenRouterLlm(String model, String apiKey) { this(model, apiKey, null); }
public OpenRouterLlm(String model, String apiKey, String effort) { this.model = model; this.apiKey = apiKey; this.effort = effort; }

public static OpenRouterLlm fromEnv(String model) { return fromEnv(model, null); }

/** {@code effort} is OpenRouter's reasoning effort ("low", "medium", "high"); null leaves it unset. */
public static OpenRouterLlm fromEnv(String model, String effort) {
    String key = System.getenv("OPENROUTER_API_KEY");
    if (key == null || key.isBlank())
        throw new IllegalStateException("OPENROUTER_API_KEY is not set. Run: export OPENROUTER_API_KEY=...");
    return new OpenRouterLlm(model, key, effort);
}
```

In `complete`, call `requestBody(model, system, user, effort)`. Replace `requestBody`:

```java
static String requestBody(String model, String system, String user) { return requestBody(model, system, user, null); }

static String requestBody(String model, String system, String user, String effort) {
    ObjectNode n = M.createObjectNode();
    n.put("model", model);
    // Reasoning tokens count toward max_tokens; leave room for the answer after them.
    n.put("max_tokens", effort == null ? 1024 : 4096);
    n.put("temperature", 0);
    n.putObject("provider").put("require_parameters", true);
    if (effort != null) n.putObject("reasoning").put("effort", effort);
    ArrayNode messages = n.putArray("messages");
    messages.addObject().put("role", "system").put("content", system);
    messages.addObject().put("role", "user").put("content", user);
    return n.toString();
}
```

Keep the existing comment above `require_parameters`.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -pl llm test`
Expected: PASS (all `OpenRouterLlmTest` tests).

- [ ] **Step 5: Commit**

```bash
git add llm
git commit -m "feat: let OpenRouterLlm send a reasoning effort"
```

---

### Task 2: `CaseState` and the three-argument `Check.run` (unit 11, "recorded for later checks")

Done first so every check and test written after this uses the final signature and nothing is rewritten later.

**Files:**
- Create: `eval/src/main/java/eval/CaseState.java`
- Modify: `eval/src/main/java/eval/checks/Check.java`, `RefusalCheck.java`, `eval/src/main/java/eval/Harness.java`
- Test: `eval/src/test/java/eval/CaseStateTest.java`, `eval/src/test/java/eval/checks/RefusalCheckTest.java` (updated)

**Interfaces:**
- Produces: `CaseState` with `void setCovering(ExpectedFact fact, List<Claim> claims)` and `List<Claim> covering(ExpectedFact fact)` (empty list when none recorded). `Check.run(EvalCase c, Answer a, CaseState state)`. Plan B's Groundedness and Source read `state.covering(fact)`.

- [ ] **Step 1: Write the failing test**

```java
package eval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CaseStateTest {
    private static final ExpectedFact F = new ExpectedFact("f", List.of("d#a"), List.of());
    @Test void unrecordedFactHasNoCoveringClaims() { assertEquals(List.of(), new CaseState().covering(F)); }
    @Test void recordedClaimsComeBack() {
        var s = new CaseState();
        var c = new Claim("x", List.of("d#a"));
        s.setCovering(F, List.of(c));
        assertEquals(List.of(c), s.covering(F));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -pl eval -am test -Dtest=CaseStateTest`
Expected: FAIL, `CaseState` does not exist.

- [ ] **Step 3: Implement**

```java
package eval;

import java.util.*;

/** Scratch space for one case: checks earlier in the list leave findings for later ones. Not part of the report. */
public final class CaseState {
    private final Map<ExpectedFact, List<Claim>> covering = new LinkedHashMap<>();

    public void setCovering(ExpectedFact fact, List<Claim> claims) { covering.put(fact, List.copyOf(claims)); }
    public List<Claim> covering(ExpectedFact fact) { return covering.getOrDefault(fact, List.of()); }
}
```

`Check.java`: `CheckResult run(EvalCase c, Answer a, CaseState state);` (import `eval.CaseState`). Add the parameter to `RefusalCheck.run` (unused). In `Harness.runCase`, create `CaseState state = new CaseState();` before the loop over `Checks.registered()` and call `r.check().run(c, answer, state)`. In `RefusalCheckTest`, add `private static final CaseState S = new CaseState();` and pass `S` as the third argument of every `check.run(...)` call (five of them).

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -pl eval -am test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add eval
git commit -m "feat: give checks a per-case state for covering claims"
```

---

### Task 3: Citation integrity check (unit 9)

Citation integrity is deterministic and is triggered by the *assistant* misbehaving, not by a case, so no eval case is written for it: a real LLM does not fabricate on demand. It is proven by the unit tests below and the end-to-end `HarnessTest`.

**Files:**
- Create: `eval/src/main/java/eval/checks/CitationIntegrityCheck.java`
- Modify: `eval/src/main/java/eval/checks/Checks.java`, `eval/src/main/java/eval/Harness.java`
- Test: `eval/src/test/java/eval/checks/CitationIntegrityCheckTest.java`, `eval/src/test/java/eval/checks/RefusalCheckTest.java` (registration test), `eval/src/test/java/eval/HarnessTest.java`

**Interfaces:**
- Consumes: `KnowledgeBase.has(String)`, `Answer`, `Claim`, `Check`, `CheckResult`, `CaseState` (existing).
- Produces: `new CitationIntegrityCheck(KnowledgeBase kb)`, name `"Citation integrity"`. `Checks.registered(KnowledgeBase kb)` (Task 4 widens it to `(kb, judge)`).

- [ ] **Step 1: Write the failing test**

```java
package eval.checks;

import eval.*;
import kb.Chunk;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CitationIntegrityCheckTest {
    private final KnowledgeBase kb = new KnowledgeBase(List.of(new Chunk("d#a", "d", "text"), new Chunk("d#b", "d", "text")));
    private final CitationIntegrityCheck check = new CitationIntegrityCheck(kb);
    private static final EvalCase CASE = new EvalCase("c", "q", "single-source", null, "answer", List.of(), null, null, null);

    private CheckResult run(Claim... claims) { return check.run(CASE, new Answer(false, List.of(claims)), new CaseState()); }

    @Test void validCitationsPass() { assertTrue(run(new Claim("x", List.of("d#a")), new Claim("y", List.of("d#a", "d#b"))).passed()); }

    @Test void claimWithoutCitationFails() {
        var r = run(new Claim("Safari 14 works", List.of()));
        assertFalse(r.passed());
        assertTrue(r.reason().contains("no citation") && r.reason().contains("Safari 14 works"), r.reason());
    }

    @Test void claimWithNullCitationsFailsWithoutCrashing() {
        var r = run(new Claim("x", null));
        assertFalse(r.passed());
        assertTrue(r.reason().contains("no citation"), r.reason());
    }

    @Test void fabricatedCitationFailsAndNamesTheId() {
        var r = run(new Claim("x", List.of("d#nope")));
        assertFalse(r.passed());
        assertTrue(r.reason().contains("fabricated citation: d#nope"), r.reason());
    }

    @Test void oneFabricatedIdAmongRealOnesStillFailsAndOnlyNamesTheFabricatedOne() {
        var r = run(new Claim("x", List.of("d#a", "d#nope")));
        assertFalse(r.passed());
        assertTrue(r.reason().contains("d#nope"), r.reason());
        assertFalse(r.reason().contains("d#a"), "the real ID must not be blamed: " + r.reason());
    }

    @Test void everyBadClaimIsListed() {
        var r = run(new Claim("first", List.of()), new Claim("second", List.of("d#nope")));
        assertTrue(r.reason().contains("first") && r.reason().contains("second"), r.reason());
    }

    @Test void refusalHasNoClaimsAndPasses() { assertTrue(check.run(CASE, new Answer(true, List.of()), new CaseState()).passed()); }

    @Test void nullClaimsListPasses() { assertTrue(check.run(CASE, new Answer(true, null), new CaseState()).passed()); }
}
```

Also add the end-to-end case to `HarnessTest`, so the check is proven through the real run loop, HTTP client and report and not only as a unit (uses the existing `cases`, `replyFor` and `out` helpers; the test docs contain chunk `d#a`, not `d#nope`):

```java
@Test void fabricatedCitationFailsTheCaseEndToEndAndTheReasonIsPrinted() throws Exception {
    cases("- id: c1\n  question: q\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#nope\"]}]}";
    int[] code = new int[1];
    String o = out(code)[0];
    assertEquals(1, code[0], o);
    assertTrue(o.contains("Citation integrity: fabricated citation: d#nope"), o);
}
```

(At this point Coverage is not registered yet, so no judge is involved. Task 4 changes `out` to inject a fake judge, and this test keeps working.)

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -pl eval -am test -Dtest=CitationIntegrityCheckTest`
Expected: FAIL, `CitationIntegrityCheck` does not exist. (`HarnessTest.fabricatedCitation...` also fails until Step 3 registers the check.)

- [ ] **Step 3: Implement**

```java
package eval.checks;

import eval.*;
import java.util.*;

/** Deterministic (D26): every claim has at least one citation and every cited chunk ID exists in the knowledge base. */
public final class CitationIntegrityCheck implements Check {
    private final KnowledgeBase kb;
    public CitationIntegrityCheck(KnowledgeBase kb) { this.kb = kb; }

    @Override public String name() { return "Citation integrity"; }

    @Override public CheckResult run(EvalCase c, Answer a, CaseState state) {
        List<String> problems = new ArrayList<>();
        for (Claim claim : a.claims() == null ? List.<Claim>of() : a.claims()) {
            if (claim.citations() == null || claim.citations().isEmpty()) {
                problems.add("no citation for claim: \"" + claim.claim() + "\"");
                continue;
            }
            for (String id : claim.citations())
                if (!kb.has(id)) problems.add("fabricated citation: " + id + " (claim: \"" + claim.claim() + "\")");
        }
        return problems.isEmpty() ? CheckResult.ok() : CheckResult.fail(String.join("; ", problems));
    }
}
```

`Checks.java`:

```java
public static List<Registered> registered(KnowledgeBase kb) {
    return List.of(
        new Registered(new RefusalCheck(), true),
        new Registered(new CitationIntegrityCheck(kb), true));
}
```
(add `import eval.KnowledgeBase;`; the no-argument `registered()` is removed)

`Harness.java`: keep the knowledge base in a variable and use it. The `KnowledgeSources.from` call can throw `IllegalArgumentException` for placeholder sources, so it stays inside the existing `try`; declare `KnowledgeBase kb;` above it and assign inside:

```java
KnowledgeBase kb;
List<EvalCase> cases;
try {
    kb = new KnowledgeBase(KnowledgeSources.from(cfg, root));
    cases = EvalCaseLoader.load(root.resolve("eval/cases"), kb, categories);
} catch (IllegalArgumentException e) {
    out.println("ERROR: " + e.getMessage());
    return 2;
}
List<Registered> checks = Checks.registered(kb);
...
caseResults.add(runCase(c, client, runId, checks));
SuiteReport report = new SuiteReport(runId, endpoint, floor, checkInfos(checks), caseResults);
```

`checkInfos(List<Registered> checks)` maps `checks` instead of calling `Checks.registered()`. `runCase(..., List<Registered> checks)` loops `checks` instead of `Checks.registered()`.

`RefusalCheckTest.refusalIsFirstAndGatingInTheRegistrationList`: call `Checks.registered(new KnowledgeBase(List.of()))`, and add:

```java
@Test void citationIntegrityIsSecondAndGating() {
    var second = Checks.registered(new KnowledgeBase(List.of())).get(1);
    assertEquals("Citation integrity", second.check().name());
    assertTrue(second.gating());
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -pl eval -am test`
Expected: PASS. If a `HarnessTest` now fails because a fixture claim cites an ID missing from its docs, fix the fixture (the harness is right); `d#a` exists in the test docs.

- [ ] **Step 5: Commit**

```bash
git add eval
git commit -m "feat: fail claims with no citation or a fabricated one"
```

---

### Task 4: `Judge`, `judgeModel` config, and harness wiring (units 11–12 infrastructure)

**Files:**
- Create: `eval/src/main/java/eval/Judge.java`
- Modify: `eval/pom.xml`, `eval/config.yaml`, `eval/src/main/java/eval/Harness.java`, `eval/src/main/java/eval/checks/Checks.java`
- Test: `eval/src/test/java/eval/JudgeTest.java`, `eval/src/test/java/eval/HarnessTest.java`, `eval/src/test/java/eval/checks/RefusalCheckTest.java`

**Interfaces:**
- Consumes: `llm.Llm` (`String complete(String system, String user)`), `Claim`.
- Produces:
  - `new Judge(Llm llm)`
  - `boolean agree(String fact, String claim)` — true if the claim states the fact. Throws `IllegalStateException` on unusable output.
  - `List<Integer> covering(String fact, List<Claim> claims)` — zero-based indices into `claims` of claims that state the fact; empty list means none. Throws `IllegalStateException("judge returned ...")` on unusable output.
  - `Checks.registered(KnowledgeBase kb, Judge judge)`.
  - `Harness.run(String[] args, Path root, PrintStream out, Function<String, Llm> judgeLlm)`; the 3-arg overload uses `model -> OpenRouterLlm.fromEnv(model, "low")`.
  - Check failures caused by exceptions are reported as `check error: <message>` and fail the case.

- [ ] **Step 1: Write the failing tests**

`JudgeTest`:

```java
package eval;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JudgeTest {
    private static Judge saying(String reply) { return new Judge((system, user) -> reply); }
    private static final List<Claim> CLAIMS = List.of(new Claim("a", List.of("d#a")), new Claim("b", List.of("d#a")), new Claim("c", List.of("d#a")));

    @Test void agreeAcceptsYesInAnyCaseWithPunctuation() {
        assertTrue(saying("YES").agree("f", "c"));
        assertTrue(saying("Yes.").agree("f", "c"));
        assertTrue(saying("YES - the claim matches").agree("f", "c"));
    }
    @Test void agreeAcceptsNo() { assertFalse(saying("no").agree("f", "c")); assertFalse(saying("NO.").agree("f", "c")); }
    @Test void agreeAcceptsMarkdownOrQuotesAroundTheWord() {
        assertTrue(saying("**YES**").agree("f", "c"));
        assertFalse(saying("`no`").agree("f", "c"));
        assertTrue(saying("\"Yes\"").agree("f", "c"));
    }
    @Test void agreeRejectsUnclearOutputInsteadOfGuessing() {
        assertThrows(IllegalStateException.class, () -> saying("I think it is probably fine").agree("f", "c"));
        assertThrows(IllegalStateException.class, () -> saying("Not sure").agree("f", "c"));
        assertThrows(IllegalStateException.class, () -> saying("").agree("f", "c"));
        assertThrows(IllegalStateException.class, () -> new Judge((s, u) -> null).agree("f", "c"));
    }

    @Test void agreePromptCarriesFactAndClaim() {
        var seen = new String[1];
        new Judge((s, u) -> { seen[0] = u; return "YES"; }).agree("Safari 14 is supported", "Safari 13 is supported");
        assertTrue(seen[0].contains("Safari 14 is supported") && seen[0].contains("Safari 13 is supported"), seen[0]);
    }

    @Test void coveringReturnsZeroBasedIndices() { assertEquals(List.of(0, 2), saying("[1, 3]").covering("f", CLAIMS)); }
    @Test void coveringEmptyListMeansNotCovered() { assertEquals(List.of(), saying("[]").covering("f", CLAIMS)); }
    @Test void coveringToleratesTextAroundTheArray() { assertEquals(List.of(1), saying("Claims: [2]").covering("f", CLAIMS)); }
    @Test void coveringRejectsOutOfRangeAndGarbage() {
        assertThrows(IllegalStateException.class, () -> saying("[4]").covering("f", CLAIMS));
        assertThrows(IllegalStateException.class, () -> saying("[0]").covering("f", CLAIMS));
        assertThrows(IllegalStateException.class, () -> saying("none of them").covering("f", CLAIMS));
        assertThrows(IllegalStateException.class, () -> saying("").covering("f", CLAIMS));
    }
    @Test void coveringPromptNumbersClaimsFromOne() {
        var seen = new String[1];
        new Judge((s, u) -> { seen[0] = u; return "[]"; }).covering("f", CLAIMS);
        assertTrue(seen[0].contains("1. a") && seen[0].contains("3. c"), seen[0]);
    }
}
```

`HarnessTest` changes. First add a config helper, so every test that rewrites `config.yaml` keeps a `judgeModel` (Task 4 makes it required):

```java
/** Writes eval/config.yaml with the given body plus a judgeModel, so tests that vary other keys stay valid. */
private void config(String body) throws IOException {
    Files.writeString(root.resolve("eval/config.yaml"), body + "judgeModel: test/judge\n");
}
```

- In `setUp`, replace the config write with `config("endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source, out-of-scope]\n");`.
- Replace the direct `Files.writeString(root.resolve("eval/config.yaml"), ...)` in `endpointFlagOverridesConfig`, `configWithoutPassFloorExitsTwoWithError`, `configWithoutOutOfScopeCategoryExitsTwoBecauseTheExitRuleNeedsIt`, `configWithoutCategoriesExitsTwo` and `unimplementedKnowledgeSourceExitsTwoBeforeAnyAssistantCall` with `config(...)` and the same body. Without this, `endpointFlagOverridesConfig` (expects exit 0) and the two tests that look for a specific error message would fail on the new `judgeModel` check.
- Change the `out` helper to `Harness.run(args, root, new PrintStream(buf), model -> (s, u) -> "YES")`.

Add:

```java
@Test void missingJudgeModelExitsTwoBeforeCallingTheAssistant() throws Exception {
    Files.writeString(root.resolve("eval/config.yaml"), "endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source, out-of-scope]\n");
    cases(OOS);
    int[] code = new int[1];
    String o = out(code)[0];
    assertEquals(2, code[0]);
    assertTrue(o.contains("judgeModel"), o);
    assertEquals(0, requests.get());
}

@Test void missingApiKeyExitsTwoWithTheExportHintBeforeCallingTheAssistant() throws Exception {
    cases(OOS);
    var buf = new ByteArrayOutputStream();
    int code = Harness.run(new String[0], root, new PrintStream(buf), model -> { throw new IllegalStateException("OPENROUTER_API_KEY is not set. Run: export OPENROUTER_API_KEY=..."); });
    assertEquals(2, code, buf.toString());
    assertTrue(buf.toString().contains("OPENROUTER_API_KEY"), buf.toString());
    assertFalse(buf.toString().contains("\tat "), buf.toString());
    assertEquals(0, requests.get());
}

@Test void judgeFailureFailsTheCaseWithCheckErrorAndTheRunStillWritesAReport() throws Exception {
    cases("- id: c1\n  question: q\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
    var buf = new ByteArrayOutputStream();
    int code = Harness.run(new String[0], root, new PrintStream(buf), model -> (s, u) -> { throw new IllegalStateException("boom"); });
    assertEquals(1, code, buf.toString());
    assertTrue(buf.toString().contains("check error: boom"), buf.toString());
    try (var s = Files.list(root.resolve("caseResults"))) { assertEquals(1, s.filter(p -> p.toString().endsWith(".json")).count()); }
}
```

The third test needs Task 5's Coverage to be registered; write it now, and expect it to pass only after Task 5. Mark it `@Disabled("enabled in Task 5")` and remove the annotation in Task 5 Step 1.

`RefusalCheckTest`: change its two registration tests to call `Checks.registered(new KnowledgeBase(List.of()), null)`.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -pl eval -am test -Dtest=JudgeTest`
Expected: FAIL, `Judge` does not exist.

- [ ] **Step 3: Implement**

`eval/pom.xml`: add inside `<dependencies>`:

```xml
<dependency>
  <groupId>com.usercentrics</groupId>
  <artifactId>llm</artifactId>
  <version>${project.version}</version>
</dependency>
```

(Copy the `groupId` from the existing `kb` dependency in the same file if it differs.)

`Judge.java`:

```java
package eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import llm.Llm;
import java.util.*;
import java.util.regex.*;

/** Two narrow judge questions used by Coverage (D24). The prompts are the only place judge wording lives. */
public final class Judge {
    private static final ObjectMapper M = new ObjectMapper();
    // Leading markdown or quotes are ignored ("**YES**"); the first word must still be yes or no.
    private static final Pattern FIRST_WORD = Pattern.compile("^[\\s*`\"']*(yes|no)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARRAY = Pattern.compile("\\[[\\d,\\s]*\\]");

    private static final String AGREE_SYSTEM = """
        You compare a documented fact with a claim made by an assistant.
        Answer YES if the claim states the fact. Extra correct detail is fine.
        Answer NO if the claim contradicts the fact, negates it, limits it with an exception, or gives a different value or version.
        Reply with exactly one word: YES or NO.""";

    private static final String COVERING_SYSTEM = """
        You are given a documented fact and a numbered list of claims made by an assistant.
        Return the numbers of the claims that each state the fact. Extra correct detail is fine.
        A claim that contradicts the fact, negates it, limits it with an exception, or gives a different value or version does not state it.
        Reply with a JSON array of numbers only, for example [1, 3]. Reply [] if no claim states the fact.""";

    private final Llm llm;
    public Judge(Llm llm) { this.llm = llm; }

    /** Do the fact and the claim agree? */
    public boolean agree(String fact, String claim) {
        String reply = llm.complete(AGREE_SYSTEM, "Fact: " + fact + "\nClaim: " + claim);
        Matcher m = FIRST_WORD.matcher(reply == null ? "" : reply);
        if (!m.find()) throw new IllegalStateException("judge returned neither YES nor NO: \"" + reply + "\"");
        return m.group(1).equalsIgnoreCase("yes");
    }

    /** Zero-based indices of the claims that state the fact; empty means none does. */
    public List<Integer> covering(String fact, List<Claim> claims) {
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < claims.size(); i++) numbered.append(i + 1).append(". ").append(claims.get(i).claim()).append('\n');
        String reply = llm.complete(COVERING_SYSTEM, "Fact: " + fact + "\nClaims:\n" + numbered);
        Matcher m = ARRAY.matcher(reply == null ? "" : reply);
        if (!m.find()) throw new IllegalStateException("judge returned no JSON array: \"" + reply + "\"");
        try {
            int[] numbers = M.readValue(m.group(), int[].class);
            List<Integer> indices = new ArrayList<>();
            for (int n : numbers) {
                if (n < 1 || n > claims.size()) throw new IllegalStateException("judge returned claim number " + n + " but there are " + claims.size() + " claims");
                if (!indices.contains(n - 1)) indices.add(n - 1);
            }
            return indices;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("judge returned an unreadable array: \"" + reply + "\"", e);
        }
    }
}
```

`eval/config.yaml`: add after `passFloor`:

```yaml
# Judge model for Coverage and, later, Groundedness (D15, D40). OpenRouter slug; stronger than assistant.model.
judgeModel: anthropic/claude-opus-4.8
```

`Checks.java`: signature `registered(KnowledgeBase kb, Judge judge)`; the list is unchanged in this task (the judge is used from Task 5).

`Harness.java`:
- Add `import llm.*; import java.util.function.Function;`.
- `run(args, root, out)` becomes:

```java
static int run(String[] args, Path root, PrintStream out) throws Exception {
    return run(args, root, out, model -> OpenRouterLlm.fromEnv(model, "low"));
}

static int run(String[] args, Path root, PrintStream out, Function<String, Llm> judgeLlm) throws Exception {
    try {
        return runInner(args, root, out, judgeLlm);
    } catch (IOException | RuntimeException e) {
        out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : e));
        return 2;
    }
}
```

- In `runInner`, **after the `try` that loads the cases and before `client.reachable()`**, replace `List<Registered> checks = Checks.registered(kb);` with:

```java
if (!(cfg.get("judgeModel") instanceof String judgeModel) || judgeModel.isBlank())
    throw new IllegalArgumentException("eval/config.yaml: 'judgeModel' is required (an OpenRouter model slug)");
Judge judge = new Judge(judgeLlm.apply(judgeModel));   // throws with the export hint if the API key is missing
List<Registered> checks = Checks.registered(kb, judge);
```

  It sits after the config, knowledge-source and case checks (they keep their own error messages) and before the first assistant request, so a missing model or key exits 2 (via `run`'s catch) with no assistant call. The catch in `run` already prints `ERROR: <message>` and returns 2.
- In `runCase`, wrap each check so one throwing check fails the case instead of ending the run:

```java
CheckResult res;
try {
    res = r.check().run(c, answer, state);
} catch (RuntimeException e) {
    res = CheckResult.fail("check error: " + e.getMessage());
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -pl eval -am test`
Expected: PASS (`JudgeTest`, updated `HarnessTest` minus the disabled test).

- [ ] **Step 5: Commit**

```bash
git add eval
git commit -m "feat: add the judge, judgeModel config and check error handling"
```

---

### Task 5: Coverage for facts with keywords (units 10 and 11)

**Files:**
- Create: `eval/src/main/java/eval/checks/CoverageCheck.java`
- Modify: `eval/src/main/java/eval/checks/Checks.java`, `eval/src/test/java/eval/HarnessTest.java` (remove `@Disabled` from `judgeFailureFails...`)
- Test: `eval/src/test/java/eval/checks/CoverageCheckTest.java`

**Interfaces:**
- Consumes: `Judge.agree`, `CaseState.setCovering`, `ExpectedFact`, `EvalCase.facts()`, `Answer.claims()`.
- Produces: `new CoverageCheck(Judge judge)`, name `"Coverage"`, registered third and gating. Postcondition: for every fact it finds covered, `state.covering(fact)` holds the confirmed claims (all confirmed candidates, not only the first).

Behaviour: for each fact with `keywords`, candidates are claims containing **all** keywords case-insensitively; no candidate means "not covered" with zero judge calls; each candidate goes to `judge.agree(fact.fact(), claim.claim())`; all confirmed candidates become covering claims (Groundedness and Source in plan B run on every one, and judging every candidate costs at most a call or two). Facts without keywords are handled in Task 6.

- [ ] **Step 1: Write the failing test**

```java
package eval.checks;

import eval.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CoverageCheckTest {
    private static final ExpectedFact SAFARI = new ExpectedFact("Safari 14 or later is supported", List.of("browser-support#browser-support"), List.of("Safari", "14"));
    private static EvalCase caseWith(ExpectedFact... facts) { return new EvalCase("c", "q", "single-source", null, "answer", List.of(facts), null, null, null); }
    private static Claim claim(String text) { return new Claim(text, List.of("browser-support#browser-support")); }

    /** Fake judge LLM: answers YES when the claim in the prompt contains the marker, and counts calls. */
    private static final class FakeLlm implements llm.Llm {
        int calls; final String yesWhenClaimContains;
        FakeLlm(String marker) { this.yesWhenClaimContains = marker; }
        @Override public String complete(String system, String user) {
            calls++;
            return user.substring(user.indexOf("Claim:")).contains(yesWhenClaimContains) ? "YES" : "NO";
        }
    }

    @Test void noClaimWithAllKeywordsMeansNotCoveredAndZeroJudgeCalls() {
        var llm = new FakeLlm("anything");
        var r = new CoverageCheck(new Judge(llm)).run(caseWith(SAFARI), new Answer(false, List.of(claim("Safari 13 is supported"))), new CaseState());
        assertFalse(r.passed());
        assertTrue(r.reason().contains("Safari 14 or later is supported"), r.reason());
        assertEquals(0, llm.calls);
    }

    @Test void keywordsMatchCaseInsensitively() {
        var llm = new FakeLlm("later");
        var r = new CoverageCheck(new Judge(llm)).run(caseWith(SAFARI), new Answer(false, List.of(claim("SAFARI 14 and later work"))), new CaseState());
        assertTrue(r.passed(), r.reason());
        assertEquals(1, llm.calls);
    }

    @Test void keywordHitAloneNeverPassesTheJudgeRejectsNegation() {
        var llm = new FakeLlm("NEVER-MATCHES");
        var r = new CoverageCheck(new Judge(llm)).run(caseWith(SAFARI), new Answer(false, List.of(claim("All Safari versions except 14 are supported"))), new CaseState());
        assertFalse(r.passed());
        assertEquals(1, llm.calls);
    }

    @Test void confirmedCandidatesAreRecordedAsCoveringClaims() {
        var good = claim("Safari 14 or later is supported");
        var bad = claim("Safari 14 is not supported on iOS");
        var other = claim("Unrelated statement");
        var state = new CaseState();
        var r = new CoverageCheck(new Judge(new FakeLlm("or later"))).run(caseWith(SAFARI), new Answer(false, List.of(bad, good, other)), state);
        assertTrue(r.passed(), r.reason());
        assertEquals(List.of(good), state.covering(SAFARI));
    }

    @Test void keywordSubstringStillOnlyMakesACandidateTheJudgeDecides() {
        var llm = new FakeLlm("NEVER-MATCHES");
        var r = new CoverageCheck(new Judge(llm)).run(caseWith(SAFARI), new Answer(false, List.of(claim("Safari 141 is supported"))), new CaseState());
        assertFalse(r.passed());
        assertEquals(1, llm.calls);
    }

    @Test void refusedAnswerFailsCoverageWithoutAnyJudgeCall() {
        var llm = new FakeLlm("x");
        var r = new CoverageCheck(new Judge(llm)).run(caseWith(SAFARI), new Answer(true, List.of()), new CaseState());
        assertFalse(r.passed());
        assertEquals(0, llm.calls);
    }

    @Test void nullClaimsListIsTreatedAsNoClaims() {
        var llm = new FakeLlm("x");
        var r = new CoverageCheck(new Judge(llm)).run(caseWith(SAFARI), new Answer(false, null), new CaseState());
        assertFalse(r.passed());
        assertEquals(0, llm.calls);
    }

    @Test void caseWithNoFactsPasses() {
        assertTrue(new CoverageCheck(new Judge((s, u) -> { throw new AssertionError("no call expected"); }))
            .run(new EvalCase("c", "q", "out-of-scope", "unrelated", "refuse", List.of(), null, null, null), new Answer(true, List.of()), new CaseState()).passed());
    }

    @Test void everyUncoveredFactIsListed() {
        var f2 = new ExpectedFact("Chrome 80 is supported", List.of("browser-support#browser-support"), List.of("Chrome", "80"));
        var r = new CoverageCheck(new Judge(new FakeLlm("x"))).run(caseWith(SAFARI, f2), new Answer(false, List.of(claim("nothing relevant"))), new CaseState());
        assertTrue(r.reason().contains("Safari 14 or later") && r.reason().contains("Chrome 80"), r.reason());
    }

    @Test void coverageIsThirdAndGatingInTheRegistrationList() {
        var third = Checks.registered(new KnowledgeBase(List.of()), new Judge((s, u) -> "YES")).get(2);
        assertEquals("Coverage", third.check().name());
        assertTrue(third.gating());
    }
}
```

Remove `@Disabled` from `HarnessTest.judgeFailureFailsTheCaseWithCheckErrorAndTheRunStillWritesAReport`, and add one more end-to-end test to `HarnessTest`: a claim that contains every keyword but negates the fact must fail the run when the judge says NO, and the judge must have been asked exactly once (the keyword filter passed it on, the judge stopped it):

```java
@Test void negatedClaimWithAllKeywordsFailsCoverageEndToEnd() throws Exception {
    cases("- id: c1\n  question: q\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"Everything in A except the body\",\"citations\":[\"d#a\"]}]}";
    var judgeCalls = new int[1];
    var buf = new ByteArrayOutputStream();
    int code = Harness.run(new String[0], root, new PrintStream(buf), model -> (s, u) -> { judgeCalls[0]++; return "NO"; });
    assertEquals(1, code, buf.toString());
    assertTrue(buf.toString().contains("Coverage: not covered"), buf.toString());
    assertEquals(1, judgeCalls[0]);
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -pl eval -am test -Dtest=CoverageCheckTest`
Expected: FAIL, `CoverageCheck` does not exist.

- [ ] **Step 3: Implement**

```java
package eval.checks;

import eval.*;
import java.util.*;

/**
 * Is each expected fact covered by a claim? (D24) A fact with keywords needs a claim containing all of them (no LLM call
 * when none does), and each such candidate is confirmed by the judge, because a keyword hit cannot see negation.
 * Covered facts leave their confirmed claims in the CaseState for Groundedness and Source.
 */
public final class CoverageCheck implements Check {
    private final Judge judge;
    public CoverageCheck(Judge judge) { this.judge = judge; }

    @Override public String name() { return "Coverage"; }

    @Override public CheckResult run(EvalCase c, Answer a, CaseState state) {
        List<Claim> claims = a.claims() == null ? List.of() : a.claims();
        List<String> uncovered = new ArrayList<>();
        for (ExpectedFact fact : c.facts()) {
            List<Claim> covering = coveringClaims(fact, claims);
            if (covering.isEmpty()) uncovered.add(fact.fact());
            else state.setCovering(fact, covering);
        }
        if (uncovered.isEmpty()) return CheckResult.ok();
        return CheckResult.fail("not covered: " + String.join("; ", uncovered.stream().map(f -> "\"" + f + "\"").toList()));
    }

    private List<Claim> coveringClaims(ExpectedFact fact, List<Claim> claims) {
        if (claims.isEmpty()) return List.of();
        List<Claim> covering = new ArrayList<>();
        for (Claim candidate : claims)
            if (containsAll(candidate.claim(), fact.keywords()) && judge.agree(fact.fact(), candidate.claim()))
                covering.add(candidate);
        return covering;
    }

    private static boolean containsAll(String text, List<String> keywords) {
        String lower = text.toLowerCase();
        return keywords.stream().allMatch(k -> lower.contains(k.toLowerCase()));
    }
}
```

This task's `coveringClaims` treats a fact with no keywords as "every claim is a candidate", which would cost one call per claim. Task 6 replaces that branch; write the test for it first there. `Checks.java` list becomes Refusal, Citation integrity, `new Registered(new CoverageCheck(judge), true)`.

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -pl eval -am test`
Expected: PASS, including the re-enabled `judgeFailureFails...` test (the fake judge throws `IllegalStateException("boom")`, the harness reports `check error: boom`).

- [ ] **Step 5: Commit**

```bash
git add eval
git commit -m "feat: Coverage check with keyword filter and judge confirmation"
```

---

### Task 6: Coverage for facts without keywords (unit 12), plus seed cases for Coverage

**Files:**
- Modify: `eval/src/main/java/eval/checks/CoverageCheck.java`, `eval/cases/single-source.yaml`
- Create: `eval/cases/false-premise.yaml`
- Test: `eval/src/test/java/eval/checks/CoverageCheckTest.java`, `eval/src/test/java/eval/SeedCasesTest.java`

Same pattern as units 1–8: each check ships with the eval cases that exercise it. Today's seeds (`ss-safari-bundle`, `ss-zonejs`) only have keyworded single facts with one gold chunk each. This task adds cases for what they never touch:

| Case | What it exercises |
|---|---|
| `ss-ab-internal-split` | a fact with no keywords: the one-call judge path (unit 12) |
| `ss-ab-third-party-events` | two facts in one case (all must be covered), one keyworded and one keyword-less, and a fact with two alternative gold chunks (D5 any-of) |
| `fp-ab-script-after-cmp` | a false-premise question (D3) whose wrong answer ("put it after the CMP") and correct answer both talk about `head` and `before`: only the judge can tell them apart |

Each adds a distinct failure (D20), is fully answerable (D7), and takes its facts from `docs/ab-test.md`. The full 28-case set is unit 21 (plan D); these are seeds. Multi-source, exact-value and multi-ask cases arrive there.

**Interfaces:**
- Consumes: `Judge.covering(String fact, List<Claim> claims)` from Task 4.

- [ ] **Step 1: Write the failing tests** (append to `CoverageCheckTest`)

```java
private static final ExpectedFact NO_KEYWORDS = new ExpectedFact("The A/B test split is not always even", List.of("ab-test#ab-test"), List.of());

@Test void factWithoutKeywordsMakesOneJudgeCallAndUsesTheReturnedIndices() {
    var calls = new int[1];
    var first = claim("Traffic can be split unevenly"); var second = claim("Something else");
    var state = new CaseState();
    var check = new CoverageCheck(new Judge((s, u) -> { calls[0]++; return "[1]"; }));
    var r = check.run(caseWith(NO_KEYWORDS), new Answer(false, List.of(first, second)), state);
    assertTrue(r.passed(), r.reason());
    assertEquals(1, calls[0]);
    assertEquals(List.of(first), state.covering(NO_KEYWORDS));
}

@Test void emptyIndexListMeansNotCovered() {
    var r = new CoverageCheck(new Judge((s, u) -> "[]")).run(caseWith(NO_KEYWORDS), new Answer(false, List.of(claim("x"))), new CaseState());
    assertFalse(r.passed());
    assertTrue(r.reason().contains("The A/B test split is not always even"), r.reason());
}

@Test void keywordlessFactWithNoClaimsMakesNoJudgeCall() {
    var r = new CoverageCheck(new Judge((s, u) -> { throw new AssertionError("no call expected"); })).run(caseWith(NO_KEYWORDS), new Answer(true, List.of()), new CaseState());
    assertFalse(r.passed());
}

@Test void unusableJudgeReplyThrowsSoTheHarnessReportsACheckError() {
    var check = new CoverageCheck(new Judge((s, u) -> "the first one"));
    assertThrows(IllegalStateException.class, () -> check.run(caseWith(NO_KEYWORDS), new Answer(false, List.of(claim("x"))), new CaseState()));
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -pl eval -am test -Dtest=CoverageCheckTest`
Expected: FAIL (`factWithoutKeywords...` counts one call per claim and the fake returns `[1]` for each, so the recorded claims and call count differ).

- [ ] **Step 3: Implement** — replace `coveringClaims` in `CoverageCheck`:

```java
private List<Claim> coveringClaims(ExpectedFact fact, List<Claim> claims) {
    if (claims.isEmpty()) return List.of();
    if (fact.keywords().isEmpty())
        return judge.covering(fact.fact(), claims).stream().map(claims::get).toList();
    List<Claim> covering = new ArrayList<>();
    for (Claim candidate : claims)
        if (containsAll(candidate.claim(), fact.keywords()) && judge.agree(fact.fact(), candidate.claim()))
            covering.add(candidate);
    return covering;
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -pl eval -am test`
Expected: PASS.

- [ ] **Step 5: Write the failing test for the seed cases** (`SeedCasesTest`)

It loads the real case files against the real docs, so a typo in a gold chunk ID or a keyword fails here and not in a live run, and it pins that the seeds cover every Coverage path.

```java
package eval;

import kb.Chunker;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SeedCasesTest {
    private final KnowledgeBase knowledge;
    private final List<EvalCase> cases;

    SeedCasesTest() throws Exception {
        Map<String, Object> cfg = new Yaml().load(Files.readString(Path.of("eval/config.yaml")));
        List<String> categories = ((List<?>) cfg.get("categories")).stream().map(String::valueOf).toList();
        knowledge = new KnowledgeBase(Chunker.chunkDir(Path.of("docs")));
        cases = EvalCaseLoader.load(Path.of("eval/cases"), knowledge, categories);   // throws on any bad gold chunk
    }

    @Test void seedsExerciseEveryCoveragePath() {
        var facts = cases.stream().flatMap(c -> c.facts().stream()).toList();
        assertTrue(facts.stream().anyMatch(f -> f.keywords().isEmpty()), "need a fact without keywords (one judge call over all claims)");
        assertTrue(facts.stream().anyMatch(f -> !f.keywords().isEmpty()), "need a fact with keywords (keyword filter, then judge confirm)");
        assertTrue(cases.stream().anyMatch(c -> c.facts().size() > 1), "need a case with two facts (every fact must be covered)");
        assertTrue(facts.stream().anyMatch(f -> f.chunks().size() > 1), "need a fact with alternative gold chunks (D5 any-of)");
        assertTrue(cases.stream().anyMatch(c -> c.category().equals("false-premise") && c.expectedBehavior().equals("answer")),
            "need a false-premise case that expects an answer (D3)");
    }

    /** A keyword found in none of the fact's gold chunks is almost certainly a typo, and makes the fact impossible to cover. */
    @Test void everyKeywordAppearsInAGoldChunkOfItsFact() {
        for (EvalCase c : cases)
            for (ExpectedFact f : c.facts())
                for (String keyword : f.keywords())
                    assertTrue(f.chunks().stream().anyMatch(id -> knowledge.get(id).text().toLowerCase().contains(keyword.toLowerCase())),
                        c.id() + ": keyword '" + keyword + "' is in none of the gold chunks " + f.chunks());
    }
}
```

Run: `mvn -q -pl eval -am test -Dtest=SeedCasesTest`
Expected: FAIL, `need a fact without keywords`.

- [ ] **Step 6: Add the seed cases.** Append to `eval/cases/single-source.yaml`:

```yaml

- id: ss-ab-internal-split
  question: "When Usercentrics runs the A/B test itself, how are visitors split between the variants?"
  category: single-source
  expected_behavior: answer
  facts:
    # no keywords on purpose: exercises the one-call judge path (unit 12)
    - fact: "With Usercentrics internal A/B testing the variants are always evenly distributed, for example 50:50 for two variants"
      chunks: [ab-test#usercentrics-internal-a-b-testing]
  source: authored
  owner: platform
  added: "2026-09-24"

- id: ss-ab-third-party-events
  question: "I use a third-party A/B testing tool with Usercentrics. Which variable must be set before the CMP loads, and which custom event can the tool listen to in order to track how users interact with the variants?"
  category: single-source
  expected_behavior: answer
  facts:
    - fact: "The UC_AB_VARIANT variable must be set before the Usercentrics CMP is loaded"
      chunks: [ab-test#a-b-testing-with-third-party-tool]
      keywords: ["UC_AB_VARIANT"]
    # keyword-less, with two alternative gold chunks (D5 any-of: citing either one is enough)
    - fact: "The UC_UI_CMP_EVENT custom event is triggered by user interactions with the CMP (for example ACCEPT_ALL or SAVE), so the tool can track them"
      chunks: [ab-test#a-b-testing-with-third-party-tool, ab-test#available-ui-events]
  source: authored
  owner: platform
  added: "2026-09-24"
```

Create `eval/cases/false-premise.yaml` (`false-premise` is already in `eval/config.yaml`'s `categories`):

```yaml
- id: fp-ab-script-after-cmp
  question: "I want my A/B testing tool to load after the Usercentrics CMP. Where do I add the tool's script?"
  category: false-premise
  expected_behavior: answer
  facts:
    # The correction is the fact. "put it in head, after the CMP" (going along with the premise) can still contain
    # "head", and "not before the CMP" contains both keywords: keywords alone cannot tell them apart, the judge does.
    - fact: "The A/B testing tool script must be added in the head section before the Usercentrics CMP script, so the tool is loaded before the CMP and can split properly"
      chunks: [ab-test#a-b-testing-with-third-party-tool]
      keywords: ["head", "before"]
  source: authored
  owner: platform
  added: "2026-09-24"
```

- [ ] **Step 7: Run to verify it passes**

Run: `mvn -q -pl eval -am test`
Expected: PASS. If `SeedCasesTest` reports `gold chunk '...' does not exist`, print the real IDs with `java -cp kb/target/classes kb.Chunker | grep ab-test` and fix the YAML; the IDs above follow `Chunker.slug` (non-alphanumerics become `-`, so `A/B` is `a-b`). If it reports a keyword missing from the gold chunks, fix the keyword or the chunk, not the test.

- [ ] **Step 8: Commit**

```bash
git add eval
git commit -m "feat: Coverage asks the judge which claims state a fact without keywords, with seed cases"
```

---

### Task 7: Trap pairs and their runner (unit 11's calibration half; **Cut 4** for the run)

The trap file has two kinds of pair. An **agree pair** has a `claim` and tests `Judge.agree` (the keyword-confirm prompt). A **covering pair** has a `claims` list (each `text` with `states: true|false`) and tests `Judge.covering` (the keyword-less prompt). Unit 11 names ~5 pairs for the confirm step; the two covering pairs are added because the keyword-less path has the same negation risk (recorded as a deviation in D41).

**Files:**
- Create: `calibration/trap-pairs.yaml`, `eval/src/main/java/eval/calibration/TrapPairs.java`
- Test: `eval/src/test/java/eval/calibration/TrapPairsTest.java`

**Interfaces:**
- Consumes: `Judge.agree`, `Judge.covering`, `Claim`.
- Produces: `TrapPairs.run(Path file, Judge judge, PrintStream out)` returning the number of misses; a judge that throws `IllegalStateException` on a pair counts as a miss (`MISS <name> (error: ...)`) and the run continues. `main` reads `judgeModel` from `eval/config.yaml`, prints each pair as `ok`/`MISS`, and exits 1 on any miss. Plan B adds the Groundedness runner next to it.

Cut 4: if time runs short, keep `trap-pairs.yaml` as documentation and skip Steps 4–5's live run.

- [ ] **Step 1: Write the failing test**

```java
package eval.calibration;

import eval.Judge;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class TrapPairsTest {
    @TempDir Path dir;
    private Path file() throws IOException {
        return Files.writeString(dir.resolve("t.yaml"), """
            - {name: negated, fact: "Safari 14 or later is supported", claim: "All Safari versions except 14", agree: false}
            - {name: paraphrase, fact: "Safari 14 or later is supported", claim: "Safari 14 and newer work", agree: true}
            - name: covering
              fact: "The split is always even"
              claims:
                - {text: "The split is not even", states: false}
                - {text: "Traffic is split evenly", states: true}
            """);
    }
    private static boolean isCoveringCall(String system) { return system.contains("JSON array"); }

    @Test void countsAJudgeThatAgreesWithEverythingAsMissesOnBothKinds() throws Exception {
        var out = new ByteArrayOutputStream();
        var judge = new Judge((s, u) -> isCoveringCall(s) ? "[1, 2]" : "YES");
        assertEquals(2, TrapPairs.run(file(), judge, new PrintStream(out)));
        assertTrue(out.toString().contains("MISS negated"), out.toString());
        assertTrue(out.toString().contains("MISS covering"), out.toString());
    }

    @Test void aJudgeThatIsRightOnAllHasNoMisses() throws Exception {
        var judge = new Judge((s, u) -> isCoveringCall(s) ? "[2]" : (u.contains("except") ? "NO" : "YES"));
        assertEquals(0, TrapPairs.run(file(), judge, new PrintStream(new ByteArrayOutputStream())));
    }

    @Test void unusableJudgeReplyIsAMissNotACrashAndTheRunContinues() throws Exception {
        var out = new ByteArrayOutputStream();
        assertEquals(3, TrapPairs.run(file(), new Judge((s, u) -> "the first one"), new PrintStream(out)));
        assertTrue(out.toString().contains("(error:"), out.toString());
        assertTrue(out.toString().contains("3 miss(es) out of 3"), out.toString());
    }

    @Test void theShippedTrapFileHasFiveAgreePairsAndTwoCoveringPairs() throws Exception {
        var out = new ByteArrayOutputStream();
        // A judge that agrees with everything and returns every claim number misses all 3 agree traps and both covering traps.
        TrapPairs.run(Path.of("calibration/trap-pairs.yaml"), new Judge((s, u) -> isCoveringCall(s) ? "[1, 2]" : "YES"), new PrintStream(out));
        assertEquals(7, out.toString().lines().filter(l -> l.startsWith("ok ") || l.startsWith("MISS ")).count());
        assertEquals(5, out.toString().lines().filter(l -> l.startsWith("MISS ")).count());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -pl eval -am test -Dtest=TrapPairsTest`
Expected: FAIL, `TrapPairs` does not exist.

- [ ] **Step 3: Implement**

`calibration/trap-pairs.yaml`:

```yaml
# Traps for the Coverage judge (D24). The judge must not be fooled by a claim that a keyword filter would let through.
#
# Agree pairs (keyword-confirm prompt): does the claim state the fact?
#   agree: true  = a correct paraphrase, the judge must say YES
#   agree: false = a trap, the judge must say NO
- {name: all-except-14,           fact: "Safari 14 or later is supported", claim: "All Safari versions except 14 are supported",            agree: false}
- {name: safari-13-not-14,        fact: "Safari 14 or later is supported", claim: "Safari 13 is supported, but not Safari 14",             agree: false}
- {name: safari-14-not-supported, fact: "Safari 14 or later is supported", claim: "Safari 14 is not supported",                            agree: false}
- {name: paraphrase-onward,       fact: "Safari 14 or later is supported", claim: "The script works on Safari 14 and any newer version",  agree: true}
- {name: paraphrase-from,         fact: "Safari 14 or later is supported", claim: "Support for Safari starts at version 14",               agree: true}

# Covering pairs (keyword-less prompt): given the numbered claims, the judge must return exactly the claims marked states: true.
- name: covering-negated-and-different-value
  fact: "With Usercentrics internal A/B testing the variants are always evenly distributed, for example 50:50 for two variants"
  claims:
    - {text: "The variants are not evenly distributed; you can choose any ratio", states: false}
    - {text: "Internal A/B testing shows the variants in a 70:30 split", states: false}
- name: covering-picks-only-the-real-one
  fact: "With Usercentrics internal A/B testing the variants are always evenly distributed, for example 50:50 for two variants"
  claims:
    - {text: "Two variants are always shown to half of the visitors each", states: true}
    - {text: "The variants are evenly distributed except when the first layer is a wall", states: false}
```

The first covering pair catches a judge that names any claim mentioning "distributed". The second catches both an always-empty and an always-all judge.

`TrapPairs.java`:

```java
package eval.calibration;

import eval.Claim;
import eval.Judge;
import llm.OpenRouterLlm;
import org.yaml.snakeyaml.Yaml;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;

/** Runs the negation traps against the judge and reports every miss (D24). Run: java -cp eval/target/eval.jar eval.calibration.TrapPairs */
public final class TrapPairs {
    public static void main(String[] args) throws Exception {
        Map<String, Object> cfg = new Yaml().load(Files.readString(Path.of("eval/config.yaml")));
        Judge judge = new Judge(OpenRouterLlm.fromEnv((String) cfg.get("judgeModel"), "low"));
        System.exit(run(Path.of("calibration/trap-pairs.yaml"), judge, System.out) == 0 ? 0 : 1);
    }

    /** Returns the number of pairs the judge got wrong; a judge that throws on a pair counts as wrong on it. */
    public static int run(Path file, Judge judge, PrintStream out) throws java.io.IOException {
        List<Map<String, Object>> pairs = new Yaml().load(Files.readString(file));
        int misses = 0;
        for (Map<String, Object> p : pairs) {
            String problem;   // null means the judge got it right
            try {
                problem = p.containsKey("claims") ? checkCovering(p, judge) : checkAgree(p, judge);
            } catch (IllegalStateException e) {
                problem = "error: " + e.getMessage();
            }
            if (problem != null) misses++;
            out.println(problem == null ? "ok   " + p.get("name") : "MISS " + p.get("name") + " (" + problem + ")");
        }
        out.println(misses + " miss(es) out of " + pairs.size());
        return misses;
    }

    private static String checkAgree(Map<String, Object> p, Judge judge) {
        boolean expected = (Boolean) p.get("agree");
        boolean actual = judge.agree((String) p.get("fact"), (String) p.get("claim"));
        return expected == actual ? null : "expected " + (expected ? "YES" : "NO");
    }

    @SuppressWarnings("unchecked")
    private static String checkCovering(Map<String, Object> p, Judge judge) {
        List<Map<String, Object>> raw = (List<Map<String, Object>>) p.get("claims");
        List<Claim> claims = raw.stream().map(c -> new Claim((String) c.get("text"), List.of("x#y"))).toList();   // citations are unused by the judge
        Set<Integer> expected = new TreeSet<>();
        for (int i = 0; i < raw.size(); i++) if ((Boolean) raw.get(i).get("states")) expected.add(i + 1);
        Set<Integer> actual = new TreeSet<>();
        for (int index : judge.covering((String) p.get("fact"), claims)) actual.add(index + 1);
        return expected.equals(actual) ? null : "expected claims " + expected + ", judge said " + actual;
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q -pl eval -am test`
Expected: PASS.

- [ ] **Step 5: Live run against the real judge (needs `OPENROUTER_API_KEY`; costs cents)**

```bash
export OPENROUTER_API_KEY=...
mvn -q -DskipTests package
java -cp eval/target/eval.jar eval.calibration.TrapPairs
```

Expected: `0 miss(es) out of 7`. Outcomes:
- HTTP error mentioning `reasoning` or `temperature` (Azure route refuses the combination): stop, remove the `"low"` argument in `Harness` and `TrapPairs`, and note it in the D41 entry below. Do not silently run without `require_parameters`.
- Every pair shows `MISS ... (error: judge returned neither YES nor NO: "")`: reasoning used up the token budget. Raise the `4096` in `OpenRouterLlm.requestBody` (with a test) before touching the effort level; note it in D41.
- Any other MISS: per the plan, raise the effort for the Coverage call (`"medium"`) and re-run; record which effort passed in D41. A miss on a covering pair means the covering prompt needs tightening, so add the missed input as a `JudgeTest` case first.
- Commit the outcome even when it required a change.

- [ ] **Step 6: Commit**

```bash
git add calibration eval llm
git commit -m "feat: negation trap pairs for both Coverage judge prompts and a runner"
```

---

### Task 8: End-to-end run, decision record, docs

**Files:**
- Modify: `grilling-decisions.md`, `docs/superpowers/plans/2026-09-24-eval-harness-units-1-8.md` (one pointer line), `build-order.md` (only if a criterion changed), `eval/config.yaml` comments if needed

- [ ] **Step 1: Run the whole thing**

```bash
export OPENROUTER_API_KEY=...
mvn -q -DskipTests package
java -jar assistant/target/assistant.jar &        # second terminal is fine
java -jar eval/target/eval.jar
```

Expected: five answer seeds (`ss-safari-bundle`, `ss-zonejs`, `ss-ab-internal-split`, `ss-ab-third-party-events`, `fp-ab-script-after-cmp`) and two out-of-scope cases print PASS/FAIL per case with `Citation integrity` and `Coverage` reasons where they fail; `caseResults/<runId>.json` lists three checks at the top (`Refusal`, `Citation integrity`, `Coverage`, all gating). Read the failing reasons and confirm each is a real assistant or case problem, not a judge parsing problem. If a case fails on `check error`, fix the prompt parsing in `Judge` with a test first.

Author-time check for the three new seeds (D7): open the report, read the stub's actual claims for each, and confirm the case is fair, i.e. the question is fully answerable from the docs and a correct answer would have passed. If a case fails because the case is unfair or a keyword is too narrow, fix the case (and keep `SeedCasesTest` green). Do not loosen the judge to make a case pass. A genuinely wrong stub answer is the harness working; leave the case failing and note it in D41.

- [ ] **Step 2: Record decisions.** Append to `grilling-decisions.md`:

```markdown
41. **Coverage details** (plan A). The judge is called with `reasoning.effort: low` (OpenRouter `reasoning` parameter; `azure/global` and `azure/us` list it with `temperature`, checked 2026-09-24), and `max_tokens` is raised to 4096 when an effort is set so reasoning cannot use up the reply. All keyword candidates are judged, not just the first, so every confirmed claim reaches Groundedness and Source. A judge reply that is not clearly YES/NO (or a JSON index array) throws and shows as `check error: ...` on the case; it is never read as NO. Trap pairs: 5 agree pairs (unit 11's ~5) plus 2 covering pairs for the keyword-less prompt, which has the same negation risk (deviation from ~5, deliberate). Result of the live run and the effort that passed: <fill in from Task 7 Step 5>. Seed cases added for Coverage: `ss-ab-internal-split`, `ss-ab-third-party-events`, `fp-ab-script-after-cmp`; `SeedCasesTest` rejects a keyword that appears in none of its fact's gold chunks.
```

Fill in the last placeholder from Task 7 Step 5 and any case outcomes from Step 1.

- [ ] **Step 3: Point readers of the earlier plan here.** Add one line under the header of `docs/superpowers/plans/2026-09-24-eval-harness-units-1-8.md`: `> Continued in 2026-09-24-eval-harness-plan-a-units-9-12.md (units 9–12); plans B–F follow.`

- [ ] **Step 4: Full test run and commit**

Run: `mvn -B test`
Expected: PASS with no API key set (CI has none).

```bash
git add grilling-decisions.md docs/superpowers/plans
git commit -m "docs: add plan A for units 9-12 and record Coverage judge decisions (D41)"
```

## Self-Review

- **Spec coverage:** unit 9 → Task 3 (integrity, deterministic, per-claim reason in detail, runs before later checks in list order). Unit 10 → Task 5 (all keywords, case-insensitive, zero LLM calls). Unit 11 → Tasks 1, 2, 4, 5, 7 (judge per candidate, negation not covered, separate `judgeModel`, temperature 0 via `OpenRouterLlm`, low effort, covering claims recorded in `CaseState`, trap pairs and runner). Unit 12 → Task 6 (one call, indices, empty means not covered, same covering claims), also covered by two covering trap pairs in Task 7. "Failing claim and reason appear in the per-case detail" is satisfied by the check reason the harness already prints and writes.
- **Eval cases:** every Coverage path has a seed (keyword, keyword-less, two facts, alternative gold chunks, false premise); `SeedCasesTest` checks case shape and that keywords occur in gold chunk text. Citation integrity has no case by design (Task 3 intro).
- **Placeholders:** none; the D41 result sentence is filled from Task 7's live run by design.
- **Type consistency:** `Check.run(EvalCase, Answer, CaseState)` is defined in Task 2 and used by every later test. `Checks.registered(kb)` in Task 3 becomes `(kb, judge)` in Task 4, and Task 4 lists the test updates (`RefusalCheckTest`, `HarnessTest` `config(...)` helper). `Judge` method names (`agree`, `covering`) are identical in Tasks 4, 5, 6, 7.
- **Known ordering hazards:** Task 4's `judgeFailureFails...` test is disabled until Task 5. Task 4 requires `judgeModel` in every `HarnessTest` config, hence the `config(...)` helper. The `4096` `max_tokens` is an assumption until Task 7 Step 5.

## Verification (whole plan)

1. `mvn -B test` passes with no API key.
2. `java -cp eval/target/eval.jar eval.calibration.TrapPairs` reports `0 miss(es) out of 7`.
3. A full harness run against the stub prints the three checks per case, writes the report, and exits 0/1 by the existing floor and out-of-scope rules.
4. Manual negative checks: edit one case's gold chunk to a fake ID (hard error, no assistant call, existing behaviour); unset `OPENROUTER_API_KEY` (exit 2 with the export hint, no assistant call); remove `judgeModel` (exit 2 naming the key); change a seed keyword to a word not in its gold chunk (`SeedCasesTest` fails naming the case and keyword).

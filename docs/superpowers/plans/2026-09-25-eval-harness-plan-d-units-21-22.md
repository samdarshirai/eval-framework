# Eval Harness — Plan D: The 28-case set and the doc-hash warning (units 21–22) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The eval set grows from 8 to 28 cases (8 single-source, 6 multi-source, 5 out-of-scope, 6 false-premise, 3 edge cases), the report rolls up pass rates by category and out-of-scope subtype, and a case whose gold chunks changed since it was confirmed produces a warning that names it.

**Architecture:** Mostly data. Twenty new YAML cases go into the existing files plus one new `edge-case.yaml`; `SeedCasesTest` gains tests that pin the documented shape of the set. Two small code changes: `SuiteReport` gets `byCategory()` and `outOfScopeBySubtype()` (shown by `ConsoleReport` and written to the JSON), and a new `CaseHash` fingerprints each case's gold chunks. `EvalCase` gets an optional `confirmedHash`; `StaleCases` compares it with the current chunks and `Harness` prints one `WARNING:` line per stale case before any assistant call. `StampCaseHashes` writes the hashes into the YAML so authors never compute one by hand.

**Tech Stack:** Java 21, Maven multi-module, JUnit 5, SnakeYAML, Jackson. No new dependencies.

**Spec:** `usercentrics-eval-harness-plan.md` (Step 3: case format, categories, growth rules), `build-order.md` units 21–22, `grilling-decisions.md` D2–D8, D18–D21, D30, D35, D46, D47, `CONTEXT.md` (Gold chunk, False premise, Out-of-scope). Previous plans: `docs/superpowers/plans/2026-09-24-eval-harness-units-1-8.md`, `…plan-a-units-9-12.md`, `…plan-b-units-13-16.md`, `2026-09-25-eval-harness-plan-c-units-18-19.md`.

**Later plans (not in this one):** E = units 17, 20 (Relevance, measured cost); F = units 23–26 (live-change rehearsal, `PATTERN.md`, `SCALE-PLAN.md`, README). Unit 22 is Cut 1 in D30; it is in this plan because it is small and shares the case schema with unit 21. If time is short, drop Tasks 8–10 and nothing else changes.

## Global Constraints

- Java 21. No new dependencies. `eval/` stays plain Java and never depends on `assistant/` (D28, D37).
- The set is exactly **8 single-source, 6 multi-source, 5 out-of-scope, 6 false-premise, 3 edge cases** (D2–D4, D8). Out-of-scope is **2 `unrelated` and 3 `plausible-nonexistent`** (D4).
- **False-premise** cases have `expected_behavior: answer` and their expected fact is the correction itself (D3). **Out-of-scope** cases have `expected_behavior: refuse` and no facts (D2, D4).
- **Authoring rule (D7):** every case is fully answerable or fully out-of-scope. No case asks about something the docs answer only in part.
- **Growth rules (D18–D20):** every case records `source`, `owner`, `added`; a case is in only if it adds a distinct failure; the per-category cap is an authoring rule and the loader does not enforce it (D35). The shape test added here checks *this repo's* set, not other teams' sets.
- Every keyword appears in at least one gold chunk of its fact (`SeedCasesTest` already enforces this) and is a *necessary* filter only (D24).
- A missing gold chunk stays a hard error (D6, unit 8). A changed gold chunk is only a **warning** and the case still runs (D21).
- `eval/` tests run with the repo root as working directory. CI runs `mvn -B test` with no API key, so no test may call a real LLM.
- Code style (user preferences): every `if`/`else`/`for`/`while`/`try`/`catch` body in braces on its own lines, never a one-line body; descriptive identifiers (no `a`, `c`, `e2`); google-java-format 1.25.2 applied once at the end on the Java files this plan touches (Task 11).
- Commit messages are plain: no `Co-Authored-By` or `Claude-Session` trailer lines (user instruction, as in plans A–C, overrides the harness default). Never print or log `OPENROUTER_API_KEY`.

## Setup (before Task 1)

Units 18–19 are merged to `main` (PR #7), so branch from it:

```bash
git status --short                                # expect only ?? .superpowers/ and ?? eval/testing-config-param.md
git checkout -b feat/eval-harness-units-21-22     # from main
mvn -B -q test                                    # all green before any change
```

`eval/testing-config-param.md` and `.superpowers/` are untracked and not ours: leave them alone, never `git add -A`.

## Review Focus

Inputs the spec implies but no unit's criteria pin down. Each has a test in the owning task.

1. **A doc edit changes a gold chunk after a case was confirmed, and an edit to an unrelated chunk in the same doc:** the first produces a warning naming the case and the run continues; the second produces nothing. A per-document hash would warn on every case for every edit and teach people to ignore the warning, so the hash covers only the case's own gold chunks. (Tasks 8, 9)
2. **An out-of-scope case (no facts, so no gold chunks):** it has no hash, is never stale, and the stamp tool leaves it untouched. It must not crash `CaseHash` or the loader. (Tasks 8, 10)
3. **A case with no `confirmed_hash` (an older set, another team's set):** loads, runs, no warning. The field is optional (D18 spirit), so `--config` users are not forced to stamp. (Tasks 8, 9)
4. **Running the stamp tool twice, and on a file with comments:** the second run changes nothing, an old hash is replaced not duplicated, and comments and quoting elsewhere in the file survive. The tool edits text lines, it does not re-serialize the YAML. (Task 10)
5. **An out-of-scope case with no `subtype`, and a category with no cases:** the subtype rollup shows the case as `untagged` (it is not dropped, and there is no null pointer); a category with no cases does not appear in the rollup. (Task 1)

## File Structure

| File | Change | Responsibility |
|---|---|---|
| `eval/cases/single-source.yaml`, `multi-source.yaml`, `out-of-scope.yaml`, `false-premise.yaml` | modify | append the new cases |
| `eval/cases/edge-case.yaml` | create | the three edge cases |
| `eval/src/test/java/eval/SeedCasesTest.java` | modify | shape of the set, no stale seed case |
| `eval/src/main/java/eval/SuiteReport.java` | modify | `Rollup`, `byCategory()`, `outOfScopeBySubtype()` |
| `eval/src/main/java/eval/ConsoleReport.java` | modify | print the two rollups |
| `eval/src/main/java/eval/EvalCase.java` | modify | optional `confirmedHash` component |
| `eval/src/main/java/eval/EvalCaseLoader.java` | modify | read `confirmed_hash` |
| `eval/src/main/java/eval/CaseHash.java` | create | fingerprint of a case's gold chunks |
| `eval/src/main/java/eval/StaleCases.java` | create | which cases are stale, as warning lines |
| `eval/src/main/java/eval/Harness.java` | modify | print the warnings after loading |
| `eval/src/main/java/eval/StampCaseHashes.java` | create | write `confirmed_hash` into the case files |
| `grilling-decisions.md`, `scope.md`, `usercentrics-tech-stack-and-repo-structure.md` | modify | D48, scope tables, hash line |
| tests | create/modify | `SuiteReportTest`, `ConsoleReportTest`, `SeedCasesTest`, `CaseHashTest`, `StaleCasesTest`, `StampCaseHashesTest`, `EvalCaseLoaderTest`, `HarnessTest` |

---

### Task 1: Roll up the pass rate by category and out-of-scope subtype

**Files:**
- Modify: `eval/src/main/java/eval/SuiteReport.java`
- Modify: `eval/src/main/java/eval/ConsoleReport.java`
- Test: `eval/src/test/java/eval/SuiteReportTest.java` (modify), `eval/src/test/java/eval/ConsoleReportTest.java` (create)

**Interfaces:**
- Consumes: `CaseResult.category()`, `CaseResult.subtype()`, `CaseResult.passed()`, `SuiteReport.OUT_OF_SCOPE` (all exist).
- Produces: `record SuiteReport.Rollup(int passed, int total)`; `Map<String, Rollup> SuiteReport.byCategory()` and `Map<String, Rollup> SuiteReport.outOfScopeBySubtype()`, both in first-seen order, both written to the JSON report as `byCategory` and `outOfScopeBySubtype`. An out-of-scope case with no subtype is counted under the key `untagged`.

- [ ] **Step 1: Write the failing tests**

Add to `SuiteReportTest` (helpers first, then the tests):

```java
  private static CaseResult result(String id, String category, String subtype, boolean passed) {
    return new CaseResult(
        id,
        "q",
        category,
        subtype,
        new Expected(false, List.of()),
        passed,
        null,
        null,
        List.of());
  }

  private static SuiteReport reportOf(CaseResult... results) {
    return new SuiteReport(
        "r", "http://x", 0.90, List.of(), SuiteReport.Calibration.SKIPPED, List.of(results));
  }

  @Test
  void rollsUpPassesByCategoryInFirstSeenOrder() {
    SuiteReport report =
        reportOf(
            result("a", "single-source", null, true),
            result("b", "out-of-scope", "unrelated", true),
            result("c", "single-source", null, false));
    assertEquals(List.of("single-source", "out-of-scope"), List.copyOf(report.byCategory().keySet()));
    assertEquals(new SuiteReport.Rollup(1, 2), report.byCategory().get("single-source"));
    assertEquals(new SuiteReport.Rollup(1, 1), report.byCategory().get("out-of-scope"));
  }

  @Test
  void rollsUpOutOfScopeBySubtypeAndKeepsACaseWithNoSubtype() {
    SuiteReport report =
        reportOf(
            result("a", "out-of-scope", "unrelated", true),
            result("b", "out-of-scope", "plausible-nonexistent", false),
            result("c", "out-of-scope", null, true),
            result("d", "single-source", null, true));
    assertEquals(new SuiteReport.Rollup(1, 1), report.outOfScopeBySubtype().get("unrelated"));
    assertEquals(
        new SuiteReport.Rollup(0, 1), report.outOfScopeBySubtype().get("plausible-nonexistent"));
    assertEquals(new SuiteReport.Rollup(1, 1), report.outOfScopeBySubtype().get("untagged"));
    assertEquals(3, report.outOfScopeBySubtype().size(), "the single-source case is not counted");
  }

  @Test
  void aCategoryWithNoCasesDoesNotAppear() {
    SuiteReport report = reportOf(result("a", "single-source", null, true));
    assertEquals(List.of("single-source"), List.copyOf(report.byCategory().keySet()));
    assertTrue(report.outOfScopeBySubtype().isEmpty());
  }
```

Create `eval/src/test/java/eval/ConsoleReportTest.java`:

```java
package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleReportTest {
  private static CaseResult result(String id, String category, String subtype, boolean passed) {
    return new CaseResult(
        id,
        "q",
        category,
        subtype,
        new Expected(false, List.of()),
        passed,
        null,
        null,
        List.of());
  }

  private static String print(CaseResult... results) {
    SuiteReport report =
        new SuiteReport(
            "r", "http://x", 0.90, List.of(), SuiteReport.Calibration.SKIPPED, List.of(results));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ConsoleReport.print(report, new PrintStream(buffer));
    return buffer.toString();
  }

  @Test
  void printsTheCategoryAndSubtypeRollupsBeforeThePassRate() {
    String output =
        print(
            result("a", "single-source", null, true),
            result("b", "single-source", null, false),
            result("c", "out-of-scope", "unrelated", true));
    assertTrue(output.contains("By category"), output);
    assertTrue(output.matches("(?s).*single-source\\s+1/2.*"), output);
    assertTrue(output.contains("Out-of-scope by subtype"), output);
    assertTrue(output.matches("(?s).*unrelated\\s+1/1.*"), output);
    assertTrue(output.indexOf("By category") < output.indexOf("Pass rate:"), output);
  }

  @Test
  void leavesOutTheSubtypeSectionWhenThereIsNoOutOfScopeCase() {
    String output = print(result("a", "single-source", null, true));
    assertTrue(output.contains("By category"), output);
    assertFalse(output.contains("Out-of-scope by subtype"), output);
  }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl eval -am test -Dtest='SuiteReportTest,ConsoleReportTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile error, `cannot find symbol: method byCategory()` / `Rollup`.

- [ ] **Step 3: Write the implementation**

In `SuiteReport.java` add `import java.util.function.Function;` and, next to `passRate()`:

```java
  /** Passed and total for one group of cases. */
  public record Rollup(int passed, int total) {}

  /** Pass counts per category, in the order the categories first appear. */
  @JsonProperty("byCategory")
  public Map<String, Rollup> byCategory() {
    return rollup(cases, CaseResult::category);
  }

  /**
   * Pass counts for the out-of-scope cases per subtype (D4), so the report shows which kind of bait
   * the assistant falls for. A case with no subtype is counted as {@code untagged}.
   */
  @JsonProperty("outOfScopeBySubtype")
  public Map<String, Rollup> outOfScopeBySubtype() {
    List<CaseResult> outOfScopeCases =
        cases.stream().filter(caseResult -> OUT_OF_SCOPE.equals(caseResult.category())).toList();
    return rollup(
        outOfScopeCases,
        caseResult -> caseResult.subtype() == null ? "untagged" : caseResult.subtype());
  }

  private static Map<String, Rollup> rollup(
      List<CaseResult> results, Function<CaseResult, String> groupOf) {
    Map<String, int[]> passedAndTotal = new LinkedHashMap<>();
    for (CaseResult result : results) {
      int[] counts = passedAndTotal.computeIfAbsent(groupOf.apply(result), group -> new int[2]);
      if (result.passed()) {
        counts[0]++;
      }
      counts[1]++;
    }
    Map<String, Rollup> rollups = new LinkedHashMap<>();
    passedAndTotal.forEach((group, counts) -> rollups.put(group, new Rollup(counts[0], counts[1])));
    return rollups;
  }
```

In `ConsoleReport.print`, call `printRollups(report, out);` right after `printBaseline(report.baseline(), out);` and add:

```java
  private static void printRollups(SuiteReport report, PrintStream out) {
    out.println("By category");
    report
        .byCategory()
        .forEach(
            (category, rollup) ->
                out.printf("  %-24s %d/%d%n", category, rollup.passed(), rollup.total()));
    if (report.outOfScopeBySubtype().isEmpty()) {
      return;
    }
    out.println("Out-of-scope by subtype");
    report
        .outOfScopeBySubtype()
        .forEach(
            (subtype, rollup) ->
                out.printf("  %-24s %d/%d%n", subtype, rollup.passed(), rollup.total()));
  }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl eval -am test`
Expected: PASS (all of `eval`, no other test reads console output that this changes; if one does, it asserts `contains`, so extra lines are fine).

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/java/eval/SuiteReport.java eval/src/main/java/eval/ConsoleReport.java \
        eval/src/test/java/eval/SuiteReportTest.java eval/src/test/java/eval/ConsoleReportTest.java
git commit -m "feat: report the pass rate by category and out-of-scope subtype (unit 21)"
```

---

### Task 2: Pin the shape of the set with tests (red until the cases exist)

**Files:**
- Modify: `eval/src/test/java/eval/SeedCasesTest.java`

**Interfaces:**
- Consumes: `EvalCaseLoader.load(Path, KnowledgeBase, List<String>)`, `EvalCase.category()/subtype()/expectedBehavior()/facts()/source()/owner()/added()` (all exist).
- Produces: nothing for later code. Tasks 3–7 each turn one or two of these tests green.

- [ ] **Step 1: Add the tests**

Add these methods to `SeedCasesTest` (the constructor already loads `cases` and `knowledge`):

```java
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
```

- [ ] **Step 2: Run the tests to verify the right ones fail**

Run: `mvn -q -pl eval -am test -Dtest=SeedCasesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL: `singleSourceHasEightCases` (expected 8 but was 4), `multiSourceHasSixCases` (1), `falsePremiseHasSixCases` (1), `outOfScopeHasFiveCases…` (2), `edgeCaseHasThreeCases` (0). The four rule tests (`falsePremise…ExpectAnAnswer…`, `everyCaseRecordsSourceOwnerAndAdded`, `everySingleSource…`, `everyMultiSource…`) pass already.

- [ ] **Step 3: Commit**

```bash
git add eval/src/test/java/eval/SeedCasesTest.java
git commit -m "test: pin the documented shape of the 28-case set (unit 21)"
```

---

### Task 3: Four more single-source cases (4 → 8)

**Files:**
- Modify: `eval/cases/single-source.yaml` (append)

**Interfaces:**
- Consumes: chunk IDs `browser-support#browser-support`, `geolocation-rules#create-rulesets`, `tcf2#layout`, `consent-mode#google-consent-mode` (all exist; list them with `java -cp kb/target/classes kb.Chunker`).
- Produces: cases `ss-loader-chrome`, `ss-geo-global-fallback`, `ss-tcf-privacy-button`, `ss-consent-mode-v2-bits`. Each adds a distinct failure: a near-miss version from the wrong script, a "why" answer with no keywords (one judge call over all claims), an exact position pair, and an exact-token pair.

- [ ] **Step 1: Append the cases**

Append to `eval/cases/single-source.yaml` (leave one blank line after the last existing case):

```yaml

- id: ss-loader-chrome
  question: "What is the minimum Chrome version that Loader.js supports on desktop?"
  category: single-source
  expected_behavior: answer
  facts:
    # bundle.js needs Chrome 69 and Loader.js needs 43: a claim about the wrong script has the right words and the wrong number
    - fact: "Loader.js supports Chrome starting from version 43"
      chunks: [browser-support#browser-support]
      keywords: ["Chrome", "43"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: ss-geo-global-fallback
  question: "Why do I have to pick a global configuration when I create a geolocation ruleset?"
  category: single-source
  expected_behavior: answer
  facts:
    # a reason, not a value: no keywords, so the judge picks the covering claim
    - fact: "The global configuration is mandatory because it acts as the fallback rule when no other rule is specified for a region"
      chunks: [geolocation-rules#create-rulesets]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: ss-tcf-privacy-button
  question: "Where can the Privacy Button be rendered in the TCF layout options?"
  category: single-source
  expected_behavior: answer
  facts:
    - fact: "The Privacy Button can be rendered at the Bottom Left or the Bottom Right"
      chunks: [tcf2#layout]
      keywords: ["Bottom Left", "Bottom Right"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: ss-consent-mode-v2-bits
  question: "Which two extra signals did Google Consent Mode v2 add?"
  category: single-source
  expected_behavior: answer
  facts:
    - fact: "Consent Mode v2 introduces two additional bits, ad_user_data and ad_personalization"
      chunks: [consent-mode#google-consent-mode]
      keywords: ["ad_user_data", "ad_personalization"]
  source: authored
  owner: platform
  added: "2026-09-25"
```

- [ ] **Step 2: Run the tests**

Run: `mvn -q -pl eval -am test -Dtest=SeedCasesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `singleSourceHasEightCases` and `everySingleSourceCaseStaysInOneDocument` PASS; the loader accepted every gold chunk and `everyKeywordAppearsInAGoldChunkOfItsFact` PASS. The other count tests still fail. A failure here naming one of the four ids means a typo in a chunk ID or keyword: fix the YAML, not the test.

- [ ] **Step 3: Commit**

```bash
git add eval/cases/single-source.yaml
git commit -m "feat: four more single-source cases (unit 21)"
```

---

### Task 4: Five more multi-source cases (1 → 6)

**Files:**
- Modify: `eval/cases/multi-source.yaml` (append)

**Interfaces:**
- Consumes: chunk IDs `tcf2#general-information`, `consent-mode#general-information`, `geolocation-rules#constraints`, `consent-mode#step-1-adjust-the-existing-google-tag-manager-code`, `geolocation-rules#create-rulesets`, `tcf2#integrate-the-tcf-cmp-into-your-website`, `ab-test#a-b-testing-with-third-party-tool` (all exist).
- Produces: cases `ms-tcf-version-and-consent-default`, `ms-geo-delete-and-consent-default`, `ms-ruleset-and-tcf-attributes`, `ms-ab-and-consent-script-order`, `ms-tcstring-and-google-vendor`. Pairings follow Step 1 of the spec: Geolocation Rules + Consent Mode, TCF + Consent Mode, plus A/B Test + Consent Mode and Geolocation Rules + TCF. Every case has two facts in two different documents, so the Source check has something to prove.

- [ ] **Step 1: Append the cases**

Append to `eval/cases/multi-source.yaml` (leave one blank line after the last existing case):

```yaml

- id: ms-tcf-version-and-consent-default
  question: "I am adding TCF to a site that already uses Google Consent Mode. Which CMP version does TCF require, and should I still set the Consent Mode default state?"
  category: multi-source
  expected_behavior: answer
  facts:
    - fact: "To support TCF you must be using the Usercentrics CMP v2"
      chunks: [tcf2#general-information]
      keywords: ["v2"]
    - fact: "Implementing the Consent Mode default state is also recommended when you use a TCF implementation"
      chunks: [consent-mode#general-information]
      keywords: ["default"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: ms-geo-delete-and-consent-default
  question: "Can I delete a company that still has geolocation rulesets, and what does the Consent Mode default snippet set ad_storage to?"
  category: multi-source
  expected_behavior: answer
  facts:
    - fact: "A company that has rulesets cannot be deleted: the ruleset has to be deleted first"
      chunks: [geolocation-rules#constraints]
      keywords: ["ruleset", "delete"]
    - fact: "The Consent Mode default snippet sets ad_storage to denied"
      chunks: [consent-mode#step-1-adjust-the-existing-google-tag-manager-code]
      keywords: ["ad_storage", "denied"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: ms-ruleset-and-tcf-attributes
  question: "I am rolling out a TCF banner with Geolocation Rules. Which attribute does the script tag generated for a ruleset contain, and which one enables TCF?"
  category: multi-source
  expected_behavior: answer
  facts:
    - fact: "The script tag generated for a ruleset carries the data-ruleset-id attribute"
      chunks: [geolocation-rules#create-rulesets]
      keywords: ["data-ruleset-id"]
    - fact: "The TCF script tag carries the data-tcf-enabled attribute"
      chunks: [tcf2#integrate-the-tcf-cmp-into-your-website]
      keywords: ["data-tcf-enabled"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: ms-ab-and-consent-script-order
  question: "Where should the Google Consent Mode default script go in the head, and where does my A/B testing tool script go relative to the CMP?"
  category: multi-source
  expected_behavior: answer
  facts:
    - fact: "The Consent Mode scripts must be put at the very top of the head, in the order shown"
      chunks: [consent-mode#step-1-adjust-the-existing-google-tag-manager-code]
      keywords: ["top", "head"]
    - fact: "The A/B testing tool script goes in the head section before the Usercentrics CMP"
      chunks: [ab-test#a-b-testing-with-third-party-tool]
      keywords: ["head", "before"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: ms-tcstring-and-google-vendor
  question: "What does the tcString encode, and under which vendor ID is Google registered on the IAB Global Vendor List?"
  category: multi-source
  expected_behavior: answer
  facts:
    - fact: "The tcString encodes the consent information in a machine readable format"
      chunks: [tcf2#general-information]
      keywords: ["tcString"]
    - fact: "Google is registered as a vendor with ID 755 on the IAB Global Vendor List"
      chunks: [consent-mode#general-information]
      keywords: ["755"]
  source: authored
  owner: platform
  added: "2026-09-25"
```

- [ ] **Step 2: Run the tests**

Run: `mvn -q -pl eval -am test -Dtest=SeedCasesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `multiSourceHasSixCases` and `everyMultiSourceCaseNeedsMoreThanOneDocument` PASS, keywords and gold chunks all resolve.

- [ ] **Step 3: Commit**

```bash
git add eval/cases/multi-source.yaml
git commit -m "feat: five more multi-source cases (unit 21)"
```

---

### Task 5: Three more out-of-scope cases (2 → 5)

**Files:**
- Modify: `eval/cases/out-of-scope.yaml` (append)

**Interfaces:**
- Consumes: nothing; out-of-scope cases have no facts and no gold chunks.
- Produces: `oos-gdpr-fine` (`unrelated`), `oos-ab-significance` and `oos-tcf-vendor-limit` (both `plausible-nonexistent`). Together with `oos-pricing` and `oos-geo-language` that is 2 unrelated and 3 plausible-nonexistent (D4). Each question was checked against the five docs: none mentions fines, statistical significance for A/B tests, or a vendor limit, and each asks about a topic the docs sit next to, so the assistant is tempted to answer from its own knowledge (the hallucination bait).

- [ ] **Step 1: Append the cases**

Append to `eval/cases/out-of-scope.yaml` (leave one blank line after the last existing case):

```yaml

- id: oos-gdpr-fine
  question: "What is the maximum fine for running a website without a consent banner under the GDPR?"
  category: out-of-scope
  subtype: unrelated
  expected_behavior: refuse
  source: authored
  owner: platform
  added: "2026-09-25"

- id: oos-ab-significance
  question: "How many visitors per variant does Usercentrics recommend before an A/B test result is statistically significant?"
  category: out-of-scope
  subtype: plausible-nonexistent
  expected_behavior: refuse
  source: authored
  owner: platform
  added: "2026-09-25"

- id: oos-tcf-vendor-limit
  question: "What is the maximum number of IAB vendors I can activate in one TCF configuration?"
  category: out-of-scope
  subtype: plausible-nonexistent
  expected_behavior: refuse
  source: authored
  owner: platform
  added: "2026-09-25"
```

- [ ] **Step 2: Confirm the docs really do not answer them**

Run:

```bash
grep -niE "statistical|significan(t|ce) (for|of|result)|sample size|maximum number|vendor limit|GDPR fine|penalt" docs/*.md
```
Expected: no line that answers any of the three questions. (`significant number of options` in `tcf2.md` is about styling and is not an answer.) If a doc does answer one, replace that question, do not keep it.

- [ ] **Step 3: Run the tests**

Run: `mvn -q -pl eval -am test -Dtest=SeedCasesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `outOfScopeHasFiveCasesTwoUnrelatedAndThreePlausibleNonexistent` and `falsePremiseCasesExpectAnAnswerAndOutOfScopeCasesExpectARefusalWithNoFacts` PASS.

- [ ] **Step 4: Commit**

```bash
git add eval/cases/out-of-scope.yaml
git commit -m "feat: three more out-of-scope cases (unit 21)"
```

---

### Task 6: Five more false-premise cases (1 → 6)

**Files:**
- Modify: `eval/cases/false-premise.yaml` (append)

**Interfaces:**
- Consumes: chunk IDs `tcf2#iab-vendors`, `consent-mode#general-information`, `geolocation-rules#prerequisites`, `tcf2#tcf-2-0-vs-tcf-2-2`, `ab-test#defining-the-variants`, `ab-test#available-properties-for-a-b-testing` (all exist).
- Produces: cases `fp-tcf-gettcdata`, `fp-consent-mode-blocking`, `fp-geo-regional-settings`, `fp-tcf20-still-valid`, `fp-ab-tcf-properties`. Each question states something the docs contradict, the expected fact is the correction, and `expected_behavior` is `answer` (D3). A claim that goes along with the premise can contain the same keywords as the correction, which is exactly why keywords are only a filter and the judge decides (D24).

- [ ] **Step 1: Append the cases**

Append to `eval/cases/false-premise.yaml` (leave one blank line after the last existing case):

```yaml

- id: fp-tcf-gettcdata
  question: "How do I call getTCData through __tcfapi in TCF 2.2 to read the consent state as soon as the CMP has loaded?"
  category: false-premise
  expected_behavior: answer
  facts:
    # the correction is the fact; "call getTCData like this" contains the keyword too, the judge tells them apart
    - fact: "The getTCData command is not available anymore in TCF 2.2"
      chunks: [tcf2#iab-vendors]
      keywords: ["getTCData"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: fp-consent-mode-blocking
  question: "I use Google Consent Mode. I should still block Google Analytics with the Google Tag Manager blocking setup so it does not fire before consent, right?"
  category: false-premise
  expected_behavior: answer
  facts:
    # no keywords on purpose: nothing checkable, so the judge reads the whole answer
    - fact: "With Google Consent Mode the supported Google tags should not be adjusted as in the Google Tag Manager blocking guide, because Google adjusts the tag behaviour from the consent signal instead of the tag being blocked"
      chunks: [consent-mode#general-information]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: fp-geo-regional-settings
  question: "For Geolocation Rules I set each configuration's regional setting to show the CMP only to EU visitors. Is that the right way to combine them?"
  category: false-premise
  expected_behavior: answer
  facts:
    - fact: "No regional settings may be in place in configurations used with Geolocation Rules: they must be set to Display CMP to all users (default)"
      chunks: [geolocation-rules#prerequisites]
      keywords: ["all users"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: fp-tcf20-still-valid
  question: "My CMP still runs TCF 2.0 and I never migrated. TC strings from 2.0 stay valid, so there is nothing to do, right?"
  category: false-premise
  expected_behavior: answer
  facts:
    - fact: "TC strings obtained under TCF v2.0 after 20 November 2023 are considered invalid, so the CMP has to be migrated to TCF 2.2"
      chunks: [tcf2#tcf-2-0-vs-tcf-2-2]
      keywords: ["invalid"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: fp-ab-tcf-properties
  question: "I can A/B test any property from the general properties table in my TCF 2.2 CMP, right? I want to change the first-layer button colours."
  category: false-premise
  expected_behavior: answer
  facts:
    # the same note appears in two chunks, so either one is a gold chunk (D5 any-of)
    - fact: "For a TCF 2.2 CMP only the specific TCF 2.2 properties can be used for A/B testing, not every property"
      chunks: [ab-test#defining-the-variants, ab-test#available-properties-for-a-b-testing]
      keywords: ["TCF"]
  source: authored
  owner: platform
  added: "2026-09-25"
```

- [ ] **Step 2: Run the tests**

Run: `mvn -q -pl eval -am test -Dtest=SeedCasesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: `falsePremiseHasSixCases` PASS; loader and keyword tests still PASS.

- [ ] **Step 3: Commit**

```bash
git add eval/cases/false-premise.yaml
git commit -m "feat: five more false-premise cases (unit 21)"
```

---

### Task 7: The three edge cases

**Files:**
- Create: `eval/cases/edge-case.yaml`

**Interfaces:**
- Consumes: chunk IDs `browser-support#browser-support`, `browser-support#libraries-support`, `ab-test#available-ui-events`, `ab-test#a-b-testing-with-third-party-tool`, `tcf2#publisher-restrictions` (all exist). `edge-case` is already in `categories` in `eval/config.yaml`.
- Produces: `edge-exact-value-bundle-edge` (exact-value precision), `edge-multi-ask` (two unrelated answerable parts), `edge-light-paraphrase` (worded differently, still shares a distinctive doc term). Each aims at a different part of the pipeline (D8). Hard vocabulary mismatch is deliberately not tested.

- [ ] **Step 1: Create the file**

```yaml
- id: edge-exact-value-bundle-edge
  question: "Which is the first Edge version that bundle.js supports?"
  category: edge-case
  expected_behavior: answer
  facts:
    # exact-value precision: Loader.js and bundle_legacy.js say Edge 77, bundle.js says 79. A near-miss is a confident wrong answer.
    - fact: "bundle.js supports Edge starting from version 79"
      chunks: [browser-support#browser-support]
      keywords: ["Edge", "79"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: edge-multi-ask
  question: "Two things: from which zone.js version do the script tags work, and which custom event lets an A/B testing tool track how users interact with the CMP?"
  category: edge-case
  expected_behavior: answer
  facts:
    # multi-ask: two unrelated answerable parts, every fact must be covered
    - fact: "The script tags support zone.js starting from version 0.11.4"
      chunks: [browser-support#libraries-support]
      keywords: ["zone.js", "0.11.4"]
    - fact: "The UC_UI_CMP_EVENT custom event is triggered by user interactions with the CMP, so the tool can track them"
      chunks: [ab-test#available-ui-events, ab-test#a-b-testing-with-third-party-tool]
      keywords: ["UC_UI_CMP_EVENT"]
  source: authored
  owner: platform
  added: "2026-09-25"

- id: edge-light-paraphrase
  question: "How can I stop my TCF banner from using legitimate interest as the legal basis?"
  category: edge-case
  expected_behavior: answer
  facts:
    # light paraphrase: the docs say "disable legitimate interest ... restrict all purposes to consent"; the question shares "legitimate interest" and "TCF" and no other wording
    - fact: "To disable legitimate interest for the TCF CMP, restrict all purposes to consent in the Admin Interface"
      chunks: [tcf2#publisher-restrictions]
      keywords: ["consent"]
  source: authored
  owner: platform
  added: "2026-09-25"
```

- [ ] **Step 2: Run the whole suite**

Run: `mvn -q -pl eval -am test`
Expected: PASS, including every test in `SeedCasesTest` (all five counts are now right, so the set has 28 cases).

- [ ] **Step 3: Print the set once as a sanity check**

Run: `grep -h "^- id:" eval/cases/*.yaml | wc -l`
Expected: `28`.

- [ ] **Step 4: Commit**

```bash
git add eval/cases/edge-case.yaml
git commit -m "feat: the three edge cases, the 28-case set is complete (unit 21)"
```

---

### Task 8: Fingerprint a case's gold chunks and read it from the YAML

**Files:**
- Create: `eval/src/main/java/eval/CaseHash.java`
- Modify: `eval/src/main/java/eval/EvalCase.java`
- Modify: `eval/src/main/java/eval/EvalCaseLoader.java`
- Test: `eval/src/test/java/eval/CaseHashTest.java` (create), `eval/src/test/java/eval/EvalCaseLoaderTest.java` (modify)

**Interfaces:**
- Consumes: `KnowledgeBase.get(String)` returning `kb.Chunk` (`text()`), `ExpectedFact.chunks()`.
- Produces: `static String CaseHash.of(EvalCase, KnowledgeBase)`: 12 lowercase hex characters (SHA-256 over the sorted, de-duplicated gold chunks' id and text), or `null` when the case has no gold chunks. `EvalCase` gains a tenth component `String confirmedHash` (nullable) and keeps its existing nine-argument constructor, so the eleven existing `new EvalCase(...)` calls in tests keep compiling. The loader reads the optional YAML key `confirmed_hash`.

The hash covers only the case's own gold chunks, not the whole document (D21 says "the docs it was last confirmed against"; this is the narrower reading, chosen so that an edit elsewhere in a document does not warn on every case that cites it: Review Focus 1). It is recorded in D48 in Task 11.

- [ ] **Step 1: Write the failing tests**

Create `eval/src/test/java/eval/CaseHashTest.java`:

```java
package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import kb.Chunk;
import org.junit.jupiter.api.Test;

class CaseHashTest {
  private static EvalCase caseWith(List<ExpectedFact> facts) {
    return new EvalCase("c1", "q", "single-source", null, "answer", facts, "authored", "me", "2026-09-25");
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
```

Add to `EvalCaseLoaderTest` (uses its `write`, `META`, `CATEGORIES`, `kb` helpers):

```java
    @Test void readsTheOptionalConfirmedHash() throws Exception {
        write("a.yaml", "- id: c1\n  question: Q?\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: F, chunks: [d#a]}\n" + META + "  confirmed_hash: \"a1b2c3d4e5f6\"\n");
        assertEquals("a1b2c3d4e5f6", EvalCaseLoader.load(dir, kb, CATEGORIES).get(0).confirmedHash());
    }

    @Test void aCaseWithoutAConfirmedHashLoadsWithNull() throws Exception {
        write("a.yaml", "- id: c1\n  question: Q?\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: F, chunks: [d#a]}\n" + META);
        assertNull(EvalCaseLoader.load(dir, kb, CATEGORIES).get(0).confirmedHash());
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl eval -am test -Dtest='CaseHashTest,EvalCaseLoaderTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile error, `cannot find symbol: CaseHash` / `confirmedHash()`.

- [ ] **Step 3: Write the implementation**

Replace `eval/src/main/java/eval/EvalCase.java`:

```java
package eval;

import java.util.List;

/**
 * One eval case. {@code confirmedHash} is the optional fingerprint of the gold chunks the case was
 * last confirmed against (see {@link CaseHash}); null when the case was never stamped.
 */
public record EvalCase(
    String id,
    String question,
    String category,
    String subtype,
    String expectedBehavior,
    List<ExpectedFact> facts,
    String source,
    String owner,
    String added,
    String confirmedHash) {

  public EvalCase(
      String id,
      String question,
      String category,
      String subtype,
      String expectedBehavior,
      List<ExpectedFact> facts,
      String source,
      String owner,
      String added) {
    this(id, question, category, subtype, expectedBehavior, facts, source, owner, added, null);
  }
}
```

Create `eval/src/main/java/eval/CaseHash.java`:

```java
package eval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/**
 * A short fingerprint of the gold chunks a case was confirmed against (D21, unit 22). It covers
 * only the case's own gold chunks, so an edit elsewhere in a document does not make every case that
 * cites the document look stale.
 */
final class CaseHash {
  private CaseHash() {}

  /**
   * 12 hex characters of SHA-256 over the id and text of every gold chunk of the case, sorted by
   * id and without repeats, or null when the case has no gold chunks (a refuse case).
   */
  static String of(EvalCase evalCase, KnowledgeBase knowledgeBase) {
    TreeSet<String> goldChunkIds = new TreeSet<>();
    for (ExpectedFact fact : evalCase.facts()) {
      goldChunkIds.addAll(fact.chunks());
    }
    if (goldChunkIds.isEmpty()) {
      return null;
    }
    StringBuilder material = new StringBuilder();
    for (String goldChunkId : goldChunkIds) {
      material
          .append(goldChunkId)
          .append('\n')
          .append(knowledgeBase.get(goldChunkId).text())
          .append('\n');
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(material.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 12);
    } catch (NoSuchAlgorithmException missingAlgorithm) {
      throw new IllegalStateException(missingAlgorithm);
    }
  }
}
```

In `EvalCaseLoader.parseCase`, change the final `return new EvalCase(...)` so it passes the tenth argument (read it the same way as `source`):

```java
        return new EvalCase(id, str(caseMap, "question", file, id, true), category, str(caseMap, "subtype", file, id, false), expectedBehavior, facts, str(caseMap, "source", file, id, false), str(caseMap, "owner", file, id, false), added, str(caseMap, "confirmed_hash", file, id, false));
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl eval -am test`
Expected: PASS (the nine-argument constructor keeps every older test compiling).

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/java/eval/CaseHash.java eval/src/main/java/eval/EvalCase.java \
        eval/src/main/java/eval/EvalCaseLoader.java eval/src/test/java/eval/CaseHashTest.java \
        eval/src/test/java/eval/EvalCaseLoaderTest.java
git commit -m "feat: fingerprint a case's gold chunks and read confirmed_hash (unit 22)"
```

---

### Task 9: Warn about a stale case before any assistant call

**Files:**
- Create: `eval/src/main/java/eval/StaleCases.java`
- Modify: `eval/src/main/java/eval/Harness.java`
- Test: `eval/src/test/java/eval/StaleCasesTest.java` (create), `eval/src/test/java/eval/HarnessTest.java` (modify)

**Interfaces:**
- Consumes: `CaseHash.of(EvalCase, KnowledgeBase)`, `EvalCase.confirmedHash()`, `EvalCase.id()` (Task 8).
- Produces: `static List<String> StaleCases.warnings(List<EvalCase>, KnowledgeBase)`: one line per stale case, in case order, each starting `case '<id>':`. A case is stale when it has a `confirmedHash` that differs from the current `CaseHash.of`. `Harness` prints each line as `WARNING: <line>` after the cases load and before the reachability check; the run continues (D21).

- [ ] **Step 1: Write the failing tests**

Create `eval/src/test/java/eval/StaleCasesTest.java`:

```java
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
```

Add to `HarnessTest` (after `refusedOutOfScopePassesAndWritesTimestampedReport`):

```java
  @Test
  void aStaleCaseWarnsByNameAndStillRuns() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: What is A?\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n"
            + "  confirmed_hash: \"000000000000\"\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("WARNING: case 'c1':"), output);
    assertTrue(output.contains("PASS") && output.contains("c1"), output);
    assertTrue(requests.get() > 0, "the stale case must still run");
  }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl eval -am test -Dtest='StaleCasesTest,HarnessTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile error, `cannot find symbol: StaleCases`.

- [ ] **Step 3: Write the implementation**

Create `eval/src/main/java/eval/StaleCases.java`:

```java
package eval;

import java.util.ArrayList;
import java.util.List;

/** Finds cases whose gold chunks changed since they were confirmed (D21, unit 22). */
final class StaleCases {
  private StaleCases() {}

  /**
   * One warning line per stale case, in case order. A case is stale when it has a confirmed hash
   * that differs from the current fingerprint of its gold chunks. A case with no hash, or with no
   * gold chunks, is never stale.
   */
  static List<String> warnings(List<EvalCase> cases, KnowledgeBase knowledgeBase) {
    List<String> warnings = new ArrayList<>();
    for (EvalCase evalCase : cases) {
      String confirmedHash = evalCase.confirmedHash();
      String currentHash = CaseHash.of(evalCase, knowledgeBase);
      if (confirmedHash == null || currentHash == null || confirmedHash.equals(currentHash)) {
        continue;
      }
      warnings.add(
          "case '"
              + evalCase.id()
              + "': its gold chunks changed since it was confirmed (confirmed_hash "
              + confirmedHash
              + ", now "
              + currentHash
              + "). Check the expected facts still hold, then re-stamp with"
              + " eval.StampCaseHashes.");
    }
    return warnings;
  }
}
```

In `Harness.runInner`, right after `List<EvalCase> cases = EvalCaseLoader.load(...)` and the baseline check, add:

```java
    for (String warning : StaleCases.warnings(cases, kb)) {
      out.println("WARNING: " + warning);
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -q -pl eval -am test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add eval/src/main/java/eval/StaleCases.java eval/src/main/java/eval/Harness.java \
        eval/src/test/java/eval/StaleCasesTest.java eval/src/test/java/eval/HarnessTest.java
git commit -m "feat: warn by name about a case whose gold chunks changed (unit 22)"
```

---

### Task 10: Stamp the hashes into the case files and guard the seed set

**Files:**
- Create: `eval/src/main/java/eval/StampCaseHashes.java`
- Modify: `eval/cases/*.yaml` (the stamp tool writes `confirmed_hash:` into every case that has facts)
- Modify: `eval/src/test/java/eval/SeedCasesTest.java`
- Test: `eval/src/test/java/eval/StampCaseHashesTest.java` (create)

**Interfaces:**
- Consumes: `EvalConfig.from(Path, CliArgs)`, `KnowledgeBase.from(EvalConfig)`, `EvalCaseLoader.load(...)`, `CaseHash.of(...)`, `CliArgs.parse(String[])`, `EvalConfig.casesDir()`, `EvalConfig.categories()` (all in package `eval`).
- Produces: `static String StampCaseHashes.stamp(String yaml, Map<String, String> hashByCaseId)`: returns the file text with one `  confirmed_hash: "<hash>"` line at the end of each listed case, replacing an existing one; cases not in the map and every other line stay as they are. `public static void main(String[])` stamps every case file in the config's cases directory (`--config <file>` works as for the harness) and prints `stamped <n> case(s) in <m> file(s)`. Case files must list each case as `- id: <id>` at column 0 (the layout of every file in this repo).

- [ ] **Step 1: Write the failing tests**

Create `eval/src/test/java/eval/StampCaseHashesTest.java`:

```java
package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StampCaseHashesTest {
  private static final String TWO_CASES =
      "# a comment that must survive\n"
          + "- id: c1\n"
          + "  question: \"Q one?\"\n"
          + "  added: \"2026-09-25\"\n"
          + "\n"
          + "- id: oos\n"
          + "  question: \"Q two?\"\n"
          + "  added: \"2026-09-25\"\n";

  @Test
  void addsTheHashAtTheEndOfTheCaseAndLeavesTheOtherCasesAlone() {
    String stamped = StampCaseHashes.stamp(TWO_CASES, Map.of("c1", "a1b2c3d4e5f6"));
    assertEquals(
        "# a comment that must survive\n"
            + "- id: c1\n"
            + "  question: \"Q one?\"\n"
            + "  added: \"2026-09-25\"\n"
            + "  confirmed_hash: \"a1b2c3d4e5f6\"\n"
            + "\n"
            + "- id: oos\n"
            + "  question: \"Q two?\"\n"
            + "  added: \"2026-09-25\"\n",
        stamped);
  }

  @Test
  void replacesAnOldHashInsteadOfAddingASecondOne() {
    String once = StampCaseHashes.stamp(TWO_CASES, Map.of("c1", "111111111111"));
    String twice = StampCaseHashes.stamp(once, Map.of("c1", "222222222222"));
    assertEquals(1, twice.split("confirmed_hash", -1).length - 1);
    assertTrue(twice.contains("confirmed_hash: \"222222222222\""), twice);
  }

  @Test
  void stampingTwiceWithTheSameHashChangesNothing() {
    String once = StampCaseHashes.stamp(TWO_CASES, Map.of("c1", "a1b2c3d4e5f6"));
    assertEquals(once, StampCaseHashes.stamp(once, Map.of("c1", "a1b2c3d4e5f6")));
  }

  @Test
  void stampsEveryListedCaseInOneFile() {
    String stamped =
        StampCaseHashes.stamp(TWO_CASES, Map.of("c1", "aaaaaaaaaaaa", "oos", "bbbbbbbbbbbb"));
    assertTrue(stamped.contains("confirmed_hash: \"aaaaaaaaaaaa\""), stamped);
    assertTrue(stamped.contains("confirmed_hash: \"bbbbbbbbbbbb\""), stamped);
  }

  @Test
  void aQuotedIdIsMatched() {
    String stamped =
        StampCaseHashes.stamp("- id: \"c1\"\n  added: \"x\"\n", Map.of("c1", "a1b2c3d4e5f6"));
    assertTrue(stamped.contains("confirmed_hash: \"a1b2c3d4e5f6\""), stamped);
  }
}
```

Add to `SeedCasesTest`:

```java
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
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `mvn -q -pl eval -am test -Dtest='StampCaseHashesTest,SeedCasesTest' -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compile error, `cannot find symbol: StampCaseHashes`.

- [ ] **Step 3: Write the implementation**

Create `eval/src/main/java/eval/StampCaseHashes.java`:

```java
package eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes {@code confirmed_hash} into the case files so nobody computes a hash by hand (D21, unit
 * 22). Run it after checking that each case still holds against the docs:
 *
 * <pre>java -cp eval/target/eval.jar eval.StampCaseHashes [--config team-config.yaml]</pre>
 *
 * It edits text lines and does not re-serialize the YAML, so comments and quoting survive.
 */
public final class StampCaseHashes {
  private static final String CASE_START = "- id:";
  private static final String HASH_LINE = "  confirmed_hash:";

  private StampCaseHashes() {}

  public static void main(String[] args) throws Exception {
    EvalConfig config = EvalConfig.from(Path.of("."), CliArgs.parse(args));
    KnowledgeBase knowledgeBase = KnowledgeBase.from(config);
    List<EvalCase> cases =
        EvalCaseLoader.load(config.casesDir(), knowledgeBase, config.categories());
    Map<String, String> hashByCaseId = new HashMap<>();
    for (EvalCase evalCase : cases) {
      String hash = CaseHash.of(evalCase, knowledgeBase);
      if (hash != null) {
        hashByCaseId.put(evalCase.id(), hash);
      }
    }
    int files = 0;
    try (var caseFiles = Files.list(config.casesDir())) {
      for (Path caseFile :
          caseFiles.filter(file -> file.toString().endsWith(".yaml")).sorted().toList()) {
        String before = Files.readString(caseFile);
        String after = stamp(before, hashByCaseId);
        if (!after.equals(before)) {
          Files.writeString(caseFile, after);
          files++;
        }
      }
    }
    System.out.println("stamped " + hashByCaseId.size() + " case(s), " + files + " file(s) changed");
  }

  /**
   * Returns the file text with {@code confirmed_hash: "<hash>"} as the last line of each case named
   * in {@code hashByCaseId}, replacing an existing one. Other cases and lines are unchanged.
   */
  static String stamp(String yaml, Map<String, String> hashByCaseId) {
    List<String> lines = new ArrayList<>(yaml.lines().toList());
    List<Integer> caseStarts = new ArrayList<>();
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      if (lines.get(lineIndex).startsWith(CASE_START)) {
        caseStarts.add(lineIndex);
      }
    }
    // Last case first, so an insertion never shifts a case start that is still to be processed.
    for (int caseIndex = caseStarts.size() - 1; caseIndex >= 0; caseIndex--) {
      int start = caseStarts.get(caseIndex);
      int end = caseIndex + 1 < caseStarts.size() ? caseStarts.get(caseIndex + 1) : lines.size();
      String caseId =
          lines
              .get(start)
              .substring(CASE_START.length())
              .trim()
              .replaceAll("^[\"']|[\"']$", "");
      String hash = hashByCaseId.get(caseId);
      if (hash == null) {
        continue;
      }
      List<String> block = lines.subList(start, end);
      block.removeIf(line -> line.startsWith(HASH_LINE));
      int lastContentLine = block.size() - 1;
      while (lastContentLine > 0 && block.get(lastContentLine).isBlank()) {
        lastContentLine--;
      }
      block.add(lastContentLine + 1, HASH_LINE + " \"" + hash + "\"");
    }
    return String.join("\n", lines) + "\n";
  }
}
```

(`IOException` import is unused if the compiler warns; remove it if `mvn` flags it.)

- [ ] **Step 4: Run the unit tests to verify `stamp` passes**

Run: `mvn -q -pl eval -am test -Dtest=StampCaseHashesTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS. `SeedCasesTest.everySeedCaseWithFactsIsStampedAndNotStale` still fails: no case is stamped yet.

- [ ] **Step 5: Stamp the repo's 23 cases that have facts**

```bash
mvn -q -DskipTests package
java -cp eval/target/eval.jar eval.StampCaseHashes
git diff --stat eval/cases
```
Expected: `stamped 23 case(s), 4 file(s) changed` (28 cases minus the 5 out-of-scope ones, spread over `single-source.yaml`, `multi-source.yaml`, `false-premise.yaml` and `edge-case.yaml`; `out-of-scope.yaml` is unchanged). Each stamped case ends with one `confirmed_hash` line, no other line moved. Run the command a second time: expected `0 file(s) changed`.

- [ ] **Step 6: Run the whole suite**

Run: `mvn -q -pl eval -am test`
Expected: PASS, including `everySeedCaseWithFactsIsStampedAndNotStale`.

- [ ] **Step 7: Commit**

```bash
git add eval/src/main/java/eval/StampCaseHashes.java eval/src/test/java/eval/StampCaseHashesTest.java \
        eval/src/test/java/eval/SeedCasesTest.java eval/cases
git commit -m "feat: stamp confirmed_hash into the case files and fail CI on a stale seed case (unit 22)"
```

---

### Task 11: Run the full set, record the decision, update the docs, format

**Files:**
- Modify: `grilling-decisions.md` (add D48 after D47, above `## Open`)
- Modify: `scope.md` (case counts, planned table, staleness sentence, known open item)
- Modify: `usercentrics-tech-stack-and-repo-structure.md:111` (doc-hash line)
- Modify: `caseResults/baseline.json` (promote the 28-case run, after review)

**Interfaces:** none (docs, a manual run and formatting only).

- [ ] **Step 1: Run the 28 cases against the stub (needs `OPENROUTER_API_KEY`, done by the user, not CI)**

Roughly 10 judge calls per case (D17), so expect a few minutes and a few dollars on the Opus judge.

```bash
export OPENROUTER_API_KEY=...
mvn -q -DskipTests package
java -jar assistant/target/assistant.jar &           # separate terminal is fine
java -jar eval/target/eval.jar --skip-calibration    # calibration ran in earlier plans; drop the flag for the full command
kill %1
```

Expected: no `WARNING:` line (every case was just stamped), 28 cases listed, the `By category` and `Out-of-scope by subtype` blocks, and a `Report: caseResults/<run>.json` line. **The exit code may be 1.** That is the harness working: the stub is deliberately weak (D36) and `ms-geo-tcf-and-consent-mode` already failed on retrieval. For every failing case decide which it is, and write the answer next to the case in the D48 text below:
- *Case mistake* (the expected fact is wrong, a keyword the docs phrase differently, a question that is partly answerable): fix the YAML, re-stamp (`eval.StampCaseHashes`), re-run that case.
- *Real stub weakness* (retrieval miss, hallucinated answer to an out-of-scope question, uncited claim): leave the case and the stub alone. It is evidence. Never tune the stub to pass (D36).

- [ ] **Step 2: Add D48 to `grilling-decisions.md`**

Insert after D47, above `## Open` (same one-paragraph style). Replace the three bracketed results with what Step 1 printed; do not leave the brackets:

```markdown
48. **The 28-case set and the doc-hash warning as built (units 21-22).** The set is 8 single-source, 6 multi-source, 5 out-of-scope (2 `unrelated`, 3 `plausible-nonexistent`), 6 false-premise and 3 edge cases, every case authored from the five docs with its gold chunks and a keyword only where a token can be checked; `SeedCasesTest` pins the counts, the answer/refuse rules per category, that single-source facts live in one document and multi-source facts in more than one, and that every seed case has source, owner and added. Changing a count there is the deliberate act D19 asks for; the loader still does not enforce a cap (D35). Each out-of-scope question was checked against the docs with `grep`, none is answered even in part. The report rolls up pass rate by category and by out-of-scope subtype (`byCategory`, `outOfScopeBySubtype` in the JSON; an out-of-scope case with no subtype shows as `untagged`). **Doc-hash warning:** a case may carry `confirmed_hash`, 12 hex characters of SHA-256 over the id and text of its own gold chunks (sorted, de-duplicated); a differing current hash prints `WARNING: case '<id>': ...` before any assistant call and the case still runs. The hash covers the gold chunks, not the whole document (D21 says "the docs it was last confirmed against"; the narrower reading keeps an edit elsewhere in a document from warning on every case that cites it). A case with no hash, and a refuse case with no gold chunks, are never stale, so other teams' sets need no stamping. `java -cp eval/target/eval.jar eval.StampCaseHashes [--config file]` writes the hashes as text lines, so comments and quoting survive; it is not a harness setting, so D47 does not apply. `SeedCasesTest` fails when a seed case's hash is missing or stale, so a doc edit makes CI ask for a re-check. **First full run (2026-09-25, run [run id]):** [n]/28 pass; [list each failing case with "case mistake, fixed" or "stub weakness, left as evidence"]. Not built: an automatic re-check of the fact text against the new chunk text (a warning is a prompt for a human), and a hash of the whole document.
```

- [ ] **Step 3: Update `scope.md`**

1. In the table under "What the cases cover", set the last column to `8 / 8`, `6 / 6`, `6 / 6`, `5 / 5`, `3 / 3` (or drop "/ planned" and write the counts), and replace `Planned total is 28 (unit 21); 8 exist today.` with `The set has 28 cases (unit 21).`
2. In "Staleness is caught by the loader", replace `(D21, warning still to be built)` with `(D21, D48)`.
3. In "In scope, planned and not built yet", delete the rows `Full 28-case set` and `Doc-hash staleness warning`; in "In scope, built" add:

```markdown
| **28-case eval set** (8 / 6 / 5 / 6 / 3) with a per-category and per-subtype rollup | Deliverable 2, and the worked example for `PATTERN.md` (D2-D8, D48) |
| **Doc-hash warning**: a case whose gold chunks changed since it was confirmed warns by name and still runs | The staleness rule of the growth plan (D21, D48) |
```

4. In "Known open item", rewrite the paragraph to match Step 1's real result (which cases fail today and why; keep "evidence, not tuned to pass"). If Step 1 gave an exit code of 0, delete the section.
5. In the cut-order sentence, remove "doc-hash warning" from the still-to-cut list (it is built).

- [ ] **Step 4: Update `usercentrics-tech-stack-and-repo-structure.md:111`**

Replace `The doc-hash staleness warning is built only if time allows.` with `The doc-hash staleness warning is built (D48): cases carry an optional confirmed_hash, written by eval.StampCaseHashes.`

- [ ] **Step 5: Promote the 28-case run as the baseline (only after the user agrees)**

The last commit that touched `baseline.json` promoted a 7/8 run and said "re-promote once the 28-case set exists". Show the user the pass rate and the failing cases from Step 1 and ask before copying:

```bash
cp caseResults/<run>.json caseResults/baseline.json
java -jar eval/target/eval.jar --skip-calibration --baseline caseResults/baseline.json
```
Expected on that second run: `Baseline: baseline.json`, `no case that passed there failed now` (or a re-run note for a flaky case). Cases that fail in the baseline are not regressions (D46). If the user declines, skip the copy and leave `baseline.json` as it is.

- [ ] **Step 6: Format the Java files this plan changed** with google-java-format 1.25.2 in its own commit. Get the jar once into the session scratchpad and set `GJF_JAR`:

```bash
curl -L -o "$SCRATCH/gjf.jar" https://github.com/google/google-java-format/releases/download/v1.25.2/google-java-format-1.25.2-all-deps.jar
export GJF_JAR="$SCRATCH/gjf.jar"
git add grilling-decisions.md scope.md usercentrics-tech-stack-and-repo-structure.md
git add caseResults/baseline.json 2>/dev/null   # only if Step 5 was done
git commit -m "docs: record the 28-case set and the doc-hash warning (D48)"
java --add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED \
     --add-exports jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED \
     -jar "$GJF_JAR" --replace \
     eval/src/main/java/eval/SuiteReport.java eval/src/main/java/eval/ConsoleReport.java \
     eval/src/main/java/eval/EvalCase.java eval/src/main/java/eval/CaseHash.java \
     eval/src/main/java/eval/StaleCases.java eval/src/main/java/eval/StampCaseHashes.java \
     eval/src/main/java/eval/Harness.java \
     eval/src/test/java/eval/SuiteReportTest.java eval/src/test/java/eval/ConsoleReportTest.java \
     eval/src/test/java/eval/SeedCasesTest.java eval/src/test/java/eval/CaseHashTest.java \
     eval/src/test/java/eval/StaleCasesTest.java eval/src/test/java/eval/StampCaseHashesTest.java \
     eval/src/test/java/eval/HarnessTest.java
mvn -B -q test
git commit -am "style: google-java-format on the files changed in units 21-22"
```

(`EvalCaseLoader.java` and `EvalCaseLoaderTest.java` use the older 4-space style and are left unformatted on purpose: a whole-file reformat would bury the one-line change in this plan.)

---

## Self-review

- **Spec coverage:** Unit 21: `eval/cases/` holds 8/6/5/6/3 (Tasks 3–7, pinned by `SeedCasesTest` in Task 2); false-premise cases expect `answer` with the correction as the fact (Task 6, rule test in Task 2); out-of-scope cases `refuse`, tagged `unrelated`/`plausible-nonexistent` 2 and 3 (Task 5, Task 2); every case records `source`, `owner`, `added` (all YAML, Task 2 test); no partially answerable case (each question is answered fully by its listed chunks, out-of-scope ones checked by `grep` in Task 5 Step 2); gold chunks resolve (loader in `SeedCasesTest`'s constructor, D6); summary by category and out-of-scope by subtype (Task 1); the cap is an authoring rule and the loader does not enforce it (nothing added to the loader). Unit 22: each case records the hash of the docs it was confirmed against (Task 8, stamped in Task 10; narrower than a whole-document hash, recorded as D48); a changed hash warns by case name and the case still runs (Task 9); a missing gold chunk stays a hard error (unchanged loader path, Review Focus 2 and 3 cover the no-hash and no-chunk cases).
- **Placeholder scan:** the only bracketed items are the run id, pass count and failing-case list in D48 (Task 11 Step 2), which come from a live run needing an API key, and the step says to replace them before committing. No `TODO`/"handle edge cases".
- **Type consistency:** `SuiteReport.Rollup(int passed, int total)`, `byCategory()`, `outOfScopeBySubtype()` are spelled the same in Task 1 code, tests and D48. `EvalCase` has ten components with `confirmedHash` last; the nine-argument constructor is kept, and `StaleCasesTest` uses the ten-argument one. `CaseHash.of(EvalCase, KnowledgeBase)` returns `String` or `null` in Tasks 8, 9, 10. `StaleCases.warnings(List<EvalCase>, KnowledgeBase)` returns `List<String>` in Tasks 9. `StampCaseHashes.stamp(String, Map<String,String>)` matches its test. YAML key `confirmed_hash` is the same in the loader, the stamp constant and the tests; the Java accessor is `confirmedHash()`.
- **Review Focus:** 1 → `CaseHashTest.ignoresAChangeToAChunkThatIsNotGold`, `changesWhenAGoldChunkTextChanges`, `StaleCasesTest.aChangedGoldChunkWarnsAndNamesTheCase`, `HarnessTest.aStaleCaseWarnsByNameAndStillRuns`; 2 → `CaseHashTest.isNullForACaseWithNoGoldChunks`, `StaleCasesTest.aRefuseCaseHasNothingToGoStale`, stamp map skips null hashes; 3 → `EvalCaseLoaderTest.aCaseWithoutAConfirmedHashLoadsWithNull`, `StaleCasesTest.aCaseWithNoConfirmedHashNeverWarns`; 4 → `StampCaseHashesTest.replacesAnOldHashInsteadOfAddingASecondOne`, `stampingTwiceWithTheSameHashChangesNothing`, the comment line in `TWO_CASES`, and the second `0 file(s) changed` run in Task 10 Step 5; 5 → `SuiteReportTest.rollsUpOutOfScopeBySubtypeAndKeepsACaseWithNoSubtype`, `aCategoryWithNoCasesDoesNotAppear`.

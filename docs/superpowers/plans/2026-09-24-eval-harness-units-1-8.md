# Eval Harness — Units 1–8 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> **Revised after PR #1 review.** The code was restructured into Maven modules and the stub moved to Spring Boot. The header, Global Constraints, File Structure and Verification below describe the current layout. **The code blocks inside Tasks 1-8 are the original single-module code as first executed**; read "Revision after PR review" (right after this header) before using them, because paths, packages and a few signatures differ.

**Goal:** A working harness that runs a case file against an HTTP assistant, reports pass/fail per case, and fails the run when an out-of-scope question gets a confident answer. This is build-order checkpoint 1 (units 1–8).

**Architecture:** Four Maven modules; the harness and the app only talk over HTTP. `assistant/` is the app under test (Chunker → BM25 → LLM → `{refused, claims}` behind `POST /answer`, a Spring Boot service). `eval/` is the harness (YAML cases → `AssistantClient` → registered checks → report + exit code). `kb/` holds `Chunk` and `Chunker`, the only code both sides share. `llm/` holds the one LLM interface (`Llm`, `AnthropicLlm`). `eval/` depends on `kb` and never on `assistant`. Only Refusal exists as a check; units 9+ add the rest.

**Tech Stack:** Java 21, Maven multi-module (Spring Boot 3.3.4 BOM), Spring Boot web for the stub only, Jackson (`jackson-databind`), SnakeYAML, JUnit 5, JDK `HttpClient`. Anthropic Messages API over raw HTTP (no SDK).

**Spec:** `usercentrics-eval-harness-plan.md` (design), `build-order.md` (units 1–8), `grilling-decisions.md` (D-numbers win over plan text), `CONTEXT.md` (vocabulary), `usercentrics-tech-stack-and-repo-structure.md` (layout).

## Revision after PR review

Commits `a2f6d58`, `d4dc566`, `58dbb22` changed the code after the tasks below were executed. The task text is kept as the record of what was built first; apply this table and list when reading it.

**Path and package mapping**

| In the tasks below | Now |
|---|---|
| `src/main/java/assistant/{Chunk,Chunker}.java`, package `assistant` | `kb/src/main/java/kb/`, package `kb` (`import kb.Chunk;`, `import kb.Chunker;`) |
| `src/main/java/assistant/{BM25Index,Claim,AssistantResponse,Assistant}.java` | `assistant/src/main/java/assistant/` (unchanged package) |
| `src/main/java/llm/` | `llm/src/main/java/llm/` |
| `src/main/java/eval/` | `eval/src/main/java/eval/` |
| `src/test/java/<pkg>/` | `<module>/src/test/java/<pkg>/` (`ChunkerTest` is in `kb`) |
| `config/assistant.yaml` (`model`, `port`, `topK`) | `config/application.yaml` (`server.port`, `server.address: 127.0.0.1`, `assistant.model`, `assistant.topK`, `assistant.docs`) |
| single `pom.xml` | parent `pom.xml` plus `kb/`, `llm/`, `assistant/`, `eval/` POMs |

**Behaviour and signature changes**
- **Task 5, stub server:** `StubServer` is a Spring Boot `@SpringBootApplication` with beans for `BM25Index`, `Llm` and `Assistant`; `AnswerController` serves `POST /answer` (400 blank or non-JSON, 405 non-POST, 500 `{"error":...}` on `IllegalArgumentException`/`IllegalStateException`). The `StubServer.create(port, assistant)` factory is gone. `StubServerTest` is a `@SpringBootTest` with `@MockBean Llm`. Loopback binding is `server.address: 127.0.0.1`.
- **Task 6, loader:** `EvalCaseLoader.load(Path casesDir, KnowledgeBase kb, List<String> allowedCategories)`. Categories come from `eval/config.yaml`; `Harness` rejects a config whose `categories` is missing or lacks `out-of-scope`. `source`, `owner` and `added` are optional. Internals were refactored (`readCaseEntries`, `parseCase`, descriptive names, inline comments).
- **Task 8, harness:** `SuiteReport.OUT_OF_SCOPE` holds the category name; `--endpoint`, unknown-argument and setup errors exit 2. `ArchitectureTest` was removed.
- **Running:** there is no `exec:java`. Build with `mvn -q -DskipTests package`, then `java -jar assistant/target/assistant.jar` and `java -jar eval/target/eval.jar` from the repo root. Tests run with the repo root as working directory (surefire `workingDirectory`).
- Decisions recorded: D37 (modules and Spring Boot) and D18 amended, in `grilling-decisions.md`.
- **Task 8, report folder:** reports go to `caseResults/<runId>.json`, not `results/` (`.gitignore` and `.gitkeep` moved with it). Each case has `expected` and `actual` in the response shape plus its check outcomes; the checks that ran (name, gating) are listed once at the top of the report.
- **Task 4, LLM client:** `AnthropicLlm` / `ANTHROPIC_API_KEY` are superseded by `OpenRouterLlm` / `OPENROUTER_API_KEY` (D39): chat-completions wire format, system prompt as the first message, model slugs like `anthropic/claude-haiku-4.5`. Every `ANTHROPIC_API_KEY` in the task text below now reads `OPENROUTER_API_KEY`.

---

## Global Constraints

- Java 21. Deps: Jackson, SnakeYAML, JUnit 5 (test scope), and Spring Boot web in `assistant/` only (D37). `eval/` stays plain Java. No LLM SDK, no Lucene.
- Chunk IDs are `<doc>#<heading-slug>`, never positional; duplicate headings get `-1`, `-2` suffixes counting from 1, single headings get none (D6, D33).
- The response has **no free-text answer field**: exactly `{refused, claims:[{claim, citations}]}` (D1). `refused: true` never comes with claims (D1, D25).
- Temperature 0 for assistant and judges (D14). Assistant model and judge model are separate config values, judge stronger (D15).
- `eval/` does not depend on `assistant/` at all; it shares only `kb.Chunk` and `kb.Chunker` (D28, D37), enforced by the module graph. The harness reaches the app only via HTTP with an `X-Eval-Run` header on each request (D32).
- BM25 always returns the top-k chunks; there is no retrieval score cutoff (D36).
- Pass floor is 0.90, read from `eval/config.yaml` (D34). Allowed case categories are also read from `eval/config.yaml` and must include `out-of-scope` (D13).
- Every commit message ends with the trailer `Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>`.

## Review Focus

Inputs the spec implies but no unit's criteria pin down. Each gets a test in the owning task.

1. **Model output is not clean JSON** (markdown-fenced, or prose): stub strips ``` fences; anything else is an HTTP 500, never a hang or a fake answer. (Task 5)
2. **Assistant call fails or times out mid-run** (HTTP 500, timeout, garbage body): that case fails with the error text; the run continues and still writes a report. (Task 8)
3. **Bad case files**: empty dir, malformed YAML, unknown `expected_behavior`, duplicate case id, an `answer` case with no facts, unquoted `added: 2026-09-24` date. All must give one clear error naming file/case, and never a stack trace. (Task 6)
4. **`#` inside a fenced code block, `####` sub-headings, headings with empty bodies** in docs: must not create bogus or empty chunks, and empty-body headings still count for duplicate numbering so IDs stay stable. (Task 2)
5. **Pass-rate boundary**: 9/10 = 90.0% meets a 0.90 floor (no float off-by-one); 0 cases is an error, not a vacuous pass. (Tasks 6, 8)

---

## File Structure

```
pom.xml                               # parent: modules kb, llm, assistant, eval; imports the Spring Boot BOM
config/application.yaml               # assistant config: server.port/address, assistant.model/topK/docs (Spring reads ./config)
docs/{browser-support,ab-test,geolocation-rules,consent-mode,tcf2}.md
results/.gitkeep
kb/pom.xml
kb/src/main/java/kb/{Chunk,Chunker}.java
llm/pom.xml
llm/src/main/java/llm/{Llm,AnthropicLlm}.java
assistant/pom.xml                     # spring-boot-starter-web, kb, llm; repackages assistant/target/assistant.jar
assistant/src/main/java/assistant/{BM25Index,Claim,AssistantResponse,Assistant,StubServer,AnswerController}.java
eval/pom.xml                          # kb, jackson, snakeyaml; shades eval/target/eval.jar (Main-Class eval.Harness)
eval/config.yaml                      # endpoint, passFloor, categories (harness-side config)
eval/cases/single-source.yaml         # 2 seed cases (unit 21 grows to 8)
eval/cases/out-of-scope.yaml          # 2 seed cases (unit 21 grows to 5)
eval/src/main/java/eval/{Answer,Claim,EvalCase,ExpectedFact,KnowledgeBase,EvalCaseLoader,AssistantClient,CaseResult,CheckOutcome,SuiteReport,Harness}.java
eval/src/main/java/eval/checks/{Check,CheckResult,Registered,Checks,RefusalCheck}.java
<module>/src/test/java/...            # one test class per main class above
```

Deviations from `usercentrics-tech-stack-and-repo-structure.md` (flag at review):
- `llm/` module: `LlmClient` was listed under `eval/`, but the stub needs it too and `assistant` must not depend on `eval`. It is an interface `Llm` plus `AnthropicLlm`; tests use a lambda as the fake. Call/token recording (unit 20) wraps `Llm` later.
- `kb/` module (PR review): `Chunk` and `Chunker` moved out of `assistant` so `eval` can use them without depending on Spring or on the app. `ArchitectureTest` is gone; the module graph enforces the boundary.
- Spring Boot stub (PR review): replaces the JDK `HttpServer` chosen in the tech-stack doc. Only `assistant/` carries Spring.
- `eval/` has its own `Answer`/`Claim` records instead of importing `assistant.AssistantResponse`. The contract is JSON; another-language app shares no classes. This is what keeps D28 true.
- `config/application.yaml` (was `config/assistant.yaml`) is separate from `eval/config.yaml` so the app does not read harness config.
- `EvalCase` gets an `id` field (spec is silent) and optional `subtype`. Needed for reporting and, later, regressions. `source`, `owner` and `added` are optional (D18, amended in review).
- JUnit 5 added (user choice) although the tech-stack doc says no framework. Harness itself remains a plain `main()`.

---

### Task 1: Project scaffold and docs snapshot (unit 1, part 1)

**Files:**
- Create: `pom.xml`, `config/assistant.yaml`, `eval/config.yaml`, `results/.gitkeep`, `docs/*.md` (5 files)
- Modify: `.gitignore`

**Interfaces:**
- Produces: `docs/<name>.md` files starting with `---\nsource: <url>\n---\n` frontmatter. Doc names: `browser-support`, `ab-test`, `geolocation-rules`, `consent-mode`, `tcf2`.

Facts learned while planning: `https://docs.usercentrics.com/<page>.md` serves raw markdown. Browser Support is not its own page: it is the `## Browser Support` and `## Libraries Support` sections at the end of `browser-cmp.md`. `tcf2.md` has `### Non-IAB Vendors` twice, a real duplicate-heading case. The docsify site itself returns compressed HTML, so use `--compressed` and the `.md` URLs.

- [ ] **Step 1: Fix `.gitignore`**

The last lines are `.DS_Store.idea/` merged onto one line, so neither is ignored. Replace that line with two, and add results handling:

```bash
sed -i '' 's|^\.DS_Store\.idea/$|.DS_Store\n.idea/|' .gitignore
printf '\n### Eval results ###\nresults/*.json\n!results/baseline.json\n' >> .gitignore
git status --short
```
Expected: `.DS_Store` and `.idea/` no longer listed as untracked.

- [ ] **Step 2: Write `pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.usercentrics</groupId>
  <artifactId>eval-harness</artifactId>
  <version>0.1.0</version>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.17.2</version>
    </dependency>
    <dependency>
      <groupId>org.yaml</groupId>
      <artifactId>snakeyaml</artifactId>
      <version>2.2</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.3</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 3: Write config files**

`config/assistant.yaml`:
```yaml
model: claude-haiku-4-5-20251001
port: 8080
topK: 3
```
`eval/config.yaml`:
```yaml
endpoint: http://localhost:8080/answer
passFloor: 0.90
```
Then `mkdir -p results src/main/java src/test/java && touch results/.gitkeep`.

- [ ] **Step 4: Fetch the five docs**

```bash
mkdir -p docs
base=https://docs.usercentrics.com
fetch() { # <page> <docname>
  { printf -- '---\nsource: %s/#/%s\n---\n' "$base" "$1"; curl -sfL --compressed "$base/$1.md"; } > "docs/$2.md"
}
fetch ab-test ab-test
fetch geolocation-rules geolocation-rules
fetch consent-mode consent-mode
fetch tcf2 tcf2
{ printf -- '---\nsource: %s/#/browser-cmp?id=browser-support\n---\n# Web CMP v2 - Browser Support\n' "$base"
  curl -sfL --compressed "$base/browser-cmp.md" | sed -n '/^## Browser Support/,$p'; } > docs/browser-support.md
```

- [ ] **Step 5: Verify docs**

```bash
head -4 docs/tcf2.md
grep -c '^## ' docs/browser-support.md      # expect 2
grep -n 'Safari: 14' docs/browser-support.md # expect 1 hit (bundle.js)
grep -c '^### Non-IAB Vendors' docs/tcf2.md  # expect 2
wc -c docs/*.md                              # none empty
```

- [ ] **Step 6: Verify Maven builds**

Run: `mvn -q -DskipTests package`
Expected: BUILD SUCCESS (no sources yet, dependencies resolve). If a version is not found, bump to the nearest available one.

- [ ] **Step 7: Commit**

```bash
git add .gitignore pom.xml config eval/config.yaml results/.gitkeep docs
git commit -m "chore: scaffold maven project and snapshot five docs" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: Chunker with heading-based IDs (unit 1, part 2)

**Files:**
- Create: `src/main/java/assistant/Chunk.java`, `src/main/java/assistant/Chunker.java`
- Test: `src/test/java/assistant/ChunkerTest.java`

**Interfaces:**
- Produces: `record Chunk(String id, String sourceDoc, String text)`; `List<Chunk> Chunker.chunk(String doc, String markdown)`; `List<Chunk> Chunker.chunkDir(Path dir)`; `Chunker.main` prints every chunk ID. `text` is the heading line plus body.
- Rules: a chunk starts at every heading of level 1–3 outside code fences; `####`+ folds into its parent; frontmatter is stripped; a section with a blank body yields no chunk but still counts toward duplicate numbering.

- [ ] **Step 1: Write the failing test**

```java
package assistant;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChunkerTest {
    private static List<String> ids(String md) {
        return Chunker.chunk("d", md).stream().map(Chunk::id).toList();
    }

    @Test void idsAreDocPlusHeadingSlug() {
        assertEquals(List.of("d#t", "d#default-consent-states", "d#sub-one"),
            ids("# T\nintro\n## Default Consent States\nx\n### Sub One\ny"));
    }

    @Test void duplicateHeadingsGetSuffixFromOneAndSingletonsGetNone() {
        assertEquals(List.of("d#overview-1", "d#other", "d#overview-2"),
            ids("## Overview\na\n## Other\nb\n## Overview\nc"));
    }

    @Test void emptyBodyHeadingSkippedButStillCountsForSuffix() {
        assertEquals(List.of("d#overview-2"), ids("## Overview\n## Overview\nc"));
    }

    @Test void reorderingOrAddingSectionsLeavesOtherIdsUnchanged() {
        Set<String> base = new HashSet<>(ids("## A\na\n## B\nb\n## C\nc"));
        assertEquals(base, new HashSet<>(ids("## C\nc\n## A\na\n## B\nb")));
        Set<String> plusD = new HashSet<>(ids("## A\na\n## D\nd\n## B\nb\n## C\nc"));
        assertTrue(plusD.containsAll(base));
    }

    @Test void hashInsideCodeFenceIsNotAHeading() {
        assertEquals(List.of("d#a"), ids("## A\n```\n# not a heading\n```\ntext"));
    }

    @Test void deeperHeadingsFoldIntoParent() {
        var chunks = Chunker.chunk("d", "## A\n#### Deep\nz");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().contains("Deep"));
    }

    @Test void frontmatterIsStripped() {
        var chunks = Chunker.chunk("d", "---\nsource: http://x\n---\n## A\nbody");
        assertEquals(List.of("d#a"), chunks.stream().map(Chunk::id).toList());
        assertFalse(chunks.get(0).text().contains("source:"));
    }

    @Test void realDocsHaveUniqueIdsAndTheExpectedAnchors() throws Exception {
        List<String> all = Chunker.chunkDir(Path.of("docs")).stream().map(Chunk::id).toList();
        assertEquals(all.size(), new HashSet<>(all).size(), "chunk IDs must be unique");
        assertTrue(all.contains("browser-support#browser-support"));
        assertTrue(all.contains("browser-support#libraries-support"));
        assertTrue(all.contains("tcf2#non-iab-vendors-1"));
        assertTrue(all.contains("tcf2#non-iab-vendors-2"));
        for (String doc : List.of("browser-support", "ab-test", "geolocation-rules", "consent-mode", "tcf2"))
            assertTrue(all.stream().anyMatch(i -> i.startsWith(doc + "#")), "no chunks for " + doc);
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=ChunkerTest`
Expected: compilation FAIL (`Chunker` / `Chunk` cannot be resolved).

- [ ] **Step 3: Implement**

`Chunk.java`:
```java
package assistant;

public record Chunk(String id, String sourceDoc, String text) {}
```
`Chunker.java`:
```java
package assistant;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class Chunker {
    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)\\s*$");

    private record Section(String heading, StringBuilder body) {}

    public static List<Chunk> chunkDir(Path dir) throws IOException {
        List<Chunk> out = new ArrayList<>();
        List<Path> files;
        try (var s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }
        for (Path p : files) {
            String doc = p.getFileName().toString().replaceFirst("\\.md$", "");
            out.addAll(chunk(doc, Files.readString(p)));
        }
        return out;
    }

    public static List<Chunk> chunk(String doc, String markdown) {
        List<Section> sections = new ArrayList<>();
        boolean inFence = false;
        for (String line : stripFrontmatter(markdown).split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) inFence = !inFence;
            Matcher m = inFence ? null : HEADING.matcher(line);
            if (m != null && m.matches()) {
                sections.add(new Section(m.group(2), new StringBuilder()));
            } else if (!sections.isEmpty()) {
                sections.get(sections.size() - 1).body().append(line).append('\n');
            }
        }
        Map<String, Integer> total = new HashMap<>();
        for (Section s : sections) total.merge(slug(s.heading()), 1, Integer::sum);
        Map<String, Integer> seen = new HashMap<>();
        List<Chunk> out = new ArrayList<>();
        for (Section s : sections) {
            String slug = slug(s.heading());
            int n = seen.merge(slug, 1, Integer::sum);
            if (s.body().toString().isBlank()) continue;
            String id = doc + "#" + slug + (total.get(slug) > 1 ? "-" + n : "");
            out.add(new Chunk(id, doc, s.heading() + "\n" + s.body().toString().strip()));
        }
        return out;
    }

    static String slug(String heading) {
        String s = heading.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? "section" : s;
    }

    private static String stripFrontmatter(String md) {
        if (!md.startsWith("---\n")) return md;
        int end = md.indexOf("\n---\n", 3);
        return end < 0 ? md : md.substring(end + 5);
    }

    public static void main(String[] args) throws IOException {
        chunkDir(Path.of("docs")).forEach(c -> System.out.println(c.id()));
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=ChunkerTest`
Expected: PASS. If `realDocsHaveUnique…` reports a collision, that is a real duplicate heading the suffix rule should have handled; debug the counting, do not weaken the test.

- [ ] **Step 5: Print the IDs (unit 1 criterion)**

Run: `mvn -q compile exec:java -Dexec.mainClass=assistant.Chunker`
Expected: one ID per section across all five docs, e.g. `browser-support#browser-support`, `tcf2#non-iab-vendors-1`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/assistant/Chunk.java src/main/java/assistant/Chunker.java src/test/java/assistant/ChunkerTest.java
git commit -m "feat: chunker with heading-based IDs and duplicate suffixes" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: BM25 retrieval (unit 2, part 1)

**Files:**
- Create: `src/main/java/assistant/BM25Index.java`
- Test: `src/test/java/assistant/BM25IndexTest.java`

**Interfaces:**
- Consumes: `Chunk` (Task 2).
- Produces: `new BM25Index(List<Chunk>)`; `List<Chunk> search(String query, int k)` returns exactly `min(k, N)` chunks, best first, ties by original order. Never filters by score (D36).

- [ ] **Step 1: Write the failing test**

```java
package assistant;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BM25IndexTest {
    private final List<Chunk> chunks = List.of(
        new Chunk("d#safari", "d", "Safari 14 browsers supported by bundle.js"),
        new Chunk("d#consent", "d", "Google Consent Mode default consent states"),
        new Chunk("d#geo", "d", "Geolocation rulesets regional settings"));

    @Test void rankedByTermMatch() {
        var top = new BM25Index(chunks).search("which browsers does bundle.js support", 2);
        assertEquals("d#safari", top.get(0).id());
    }

    @Test void alwaysReturnsKChunksEvenWithNoTermOverlap() {
        var top = new BM25Index(chunks).search("pricing subscription", 3);
        assertEquals(3, top.size());
    }

    @Test void kLargerThanCorpusReturnsAll() {
        assertEquals(3, new BM25Index(chunks).search("consent", 10).size());
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=BM25IndexTest`
Expected: compilation FAIL (`BM25Index` not found).

- [ ] **Step 3: Implement**

```java
package assistant;

import java.util.*;

public final class BM25Index {
    private static final double K1 = 1.5, B = 0.75;
    private final List<Chunk> chunks;
    private final List<Map<String, Integer>> tf = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();
    private final int[] len;
    private final double avgLen;

    public BM25Index(List<Chunk> chunks) {
        this.chunks = chunks;
        this.len = new int[chunks.size()];
        int total = 0;
        for (int i = 0; i < chunks.size(); i++) {
            List<String> toks = tokenize(chunks.get(i).text());
            len[i] = toks.size();
            total += toks.size();
            Map<String, Integer> counts = new HashMap<>();
            toks.forEach(t -> counts.merge(t, 1, Integer::sum));
            counts.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
            tf.add(counts);
        }
        this.avgLen = chunks.isEmpty() ? 0 : (double) total / chunks.size();
    }

    public List<Chunk> search(String query, int k) {
        List<String> q = tokenize(query);
        double[] score = new double[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            for (String t : q) {
                int f = tf.get(i).getOrDefault(t, 0);
                if (f == 0) continue;
                double idf = Math.log(1 + (chunks.size() - df.get(t) + 0.5) / (df.get(t) + 0.5));
                score[i] += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * len[i] / avgLen));
            }
        }
        return java.util.stream.IntStream.range(0, chunks.size()).boxed()
            .sorted(Comparator.<Integer>comparingDouble(i -> -score[i]).thenComparingInt(i -> i))
            .limit(k).map(chunks::get).toList();
    }

    static List<String> tokenize(String s) {
        return Arrays.stream(s.toLowerCase().split("[^a-z0-9]+")).filter(t -> !t.isEmpty()).toList();
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=BM25IndexTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/assistant/BM25Index.java src/test/java/assistant/BM25IndexTest.java
git commit -m "feat: hand-rolled BM25 retrieval" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: LLM interface and Anthropic client (units 2, 3 dependency)

**Files:**
- Create: `src/main/java/llm/Llm.java`, `src/main/java/llm/AnthropicLlm.java`
- Test: `src/test/java/llm/AnthropicLlmTest.java`

**Interfaces:**
- Produces: `interface Llm { String complete(String system, String user); }`; `AnthropicLlm.fromEnv(String model)` (reads `ANTHROPIC_API_KEY`, throws `IllegalStateException` with a clear message if unset); package-private `static String requestBody(String model, String system, String user)` and `static String parseText(String responseJson)` for testing without network.

- [ ] **Step 1: Write the failing test**

```java
package llm;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnthropicLlmTest {
    private static final ObjectMapper M = new ObjectMapper();

    @Test void requestBodyPinsTemperatureZeroAndCarriesModelAndPrompts() throws Exception {
        JsonNode n = M.readTree(AnthropicLlm.requestBody("m-1", "sys", "hi"));
        assertEquals("m-1", n.get("model").asText());
        assertEquals(0, n.get("temperature").asInt());
        assertEquals("sys", n.get("system").asText());
        assertEquals("hi", n.get("messages").get(0).get("content").asText());
    }

    @Test void parseTextReturnsFirstTextBlock() {
        assertEquals("hello", AnthropicLlm.parseText("{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}"));
    }

    @Test void fromEnvWithoutKeyExplainsWhatToDo() {
        // only meaningful when the key is unset; skip otherwise
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("ANTHROPIC_API_KEY") == null);
        var e = assertThrows(IllegalStateException.class, () -> AnthropicLlm.fromEnv("m"));
        assertTrue(e.getMessage().contains("ANTHROPIC_API_KEY"));
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=AnthropicLlmTest`
Expected: compilation FAIL.

- [ ] **Step 3: Implement**

`Llm.java`:
```java
package llm;

public interface Llm {
    String complete(String system, String user);
}
```
`AnthropicLlm.java`:
```java
package llm;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class AnthropicLlm implements Llm {
    private static final ObjectMapper M = new ObjectMapper();
    private static final URI URL = URI.create("https://api.anthropic.com/v1/messages");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String model, apiKey;

    public AnthropicLlm(String model, String apiKey) { this.model = model; this.apiKey = apiKey; }

    public static AnthropicLlm fromEnv(String model) {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank())
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set. Run: export ANTHROPIC_API_KEY=...");
        return new AnthropicLlm(model, key);
    }

    @Override public String complete(String system, String user) {
        try {
            var req = HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(model, system, user))).build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200)
                throw new IllegalStateException("Anthropic API " + res.statusCode() + ": " + res.body());
            return parseText(res.body());
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException("Anthropic API call failed: " + e.getMessage(), e);
        }
    }

    static String requestBody(String model, String system, String user) {
        ObjectNode n = M.createObjectNode();
        n.put("model", model);
        n.put("max_tokens", 1024);
        n.put("temperature", 0);
        n.put("system", system);
        n.putArray("messages").addObject().put("role", "user").put("content", user);
        return n.toString();
    }

    static String parseText(String json) {
        try {
            return M.readTree(json).get("content").get(0).get("text").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Anthropic response: " + json, e);
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `mvn -q test -Dtest=AnthropicLlmTest`
Expected: PASS.

- [ ] **Step 5: Live smoke check (needs `export ANTHROPIC_API_KEY=...`; skip if unset)**

Confirms `temperature: 0` is accepted by the configured model. If the API returns a 400 mentioning `temperature`, remove that field only for the affected model, and record the deviation from D14 in `grilling-decisions.md` (use the `claude-api` skill for current model parameter rules). Quick check via `jshell --class-path target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)`: `llm.AnthropicLlm.fromEnv("claude-haiku-4-5-20251001").complete("Reply with one word.", "Say ok")`. Expected: `ok`.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/llm src/test/java/llm
git commit -m "feat: Llm interface and Anthropic client at temperature 0" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: Assistant and StubServer (units 2 and 3)

**Files:**
- Create: `src/main/java/assistant/{Claim,AssistantResponse,Assistant,StubServer}.java`
- Test: `src/test/java/assistant/AssistantTest.java`, `src/test/java/assistant/StubServerTest.java`

**Interfaces:**
- Consumes: `Chunk`, `BM25Index.search` (Task 3), `Llm.complete` (Task 4).
- Produces: `record Claim(String claim, List<String> citations)`; `record AssistantResponse(boolean refused, List<Claim> claims)`; `new Assistant(BM25Index, Llm, int topK)`; `AssistantResponse answer(String question)`; `static AssistantResponse Assistant.parse(String raw)` (throws `IllegalArgumentException` on unparseable output); `static HttpServer StubServer.create(int port, Assistant a)` (port 0 = ephemeral); `StubServer.main` reads `config/assistant.yaml`.
- Contract normalisation: `refused=true` forces `claims=[]`; `null` claims become `[]`.

- [ ] **Step 1: Write the failing tests**

`AssistantTest.java`:
```java
package assistant;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssistantTest {
    private final List<Chunk> chunks = List.of(
        new Chunk("bs#browser-support", "bs", "Browser Support Safari 14 bundle.js"),
        new Chunk("cm#modes", "cm", "Consent Mode default consent states"));

    private Assistant with(String modelOutput, List<String> seenUserPrompts) {
        return new Assistant(new BM25Index(chunks), (sys, user) -> { seenUserPrompts.add(user); return modelOutput; }, 2);
    }

    @Test void answersWithCitedClaims() {
        var prompts = new ArrayList<String>();
        var r = with("{\"refused\":false,\"claims\":[{\"claim\":\"bundle.js supports Safari 14\",\"citations\":[\"bs#browser-support\"]}]}", prompts)
            .answer("Which browsers does bundle.js support?");
        assertFalse(r.refused());
        assertEquals(List.of("bs#browser-support"), r.claims().get(0).citations());
        assertTrue(prompts.get(0).contains("[bs#browser-support]"), "prompt must expose chunk ids to the model");
    }

    @Test void refusalHasNoClaims() {
        var r = with("{\"refused\":true,\"claims\":[]}", new ArrayList<>()).answer("How much does it cost?");
        assertTrue(r.refused());
        assertTrue(r.claims().isEmpty());
    }

    @Test void refusedWithClaimsIsNormalisedToNoClaims() {
        var r = Assistant.parse("{\"refused\":true,\"claims\":[{\"claim\":\"x\",\"citations\":[]}]}");
        assertTrue(r.claims().isEmpty());
    }

    @Test void markdownFencedJsonIsAccepted() {
        var r = Assistant.parse("```json\n{\"refused\":true,\"claims\":[]}\n```");
        assertTrue(r.refused());
    }

    @Test void proseInsteadOfJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Assistant.parse("Sorry, I cannot help with that."));
    }
}
```
`StubServerTest.java`:
```java
package assistant;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StubServerTest {
    private HttpServer server;
    private String base;

    @BeforeEach void start() throws Exception {
        var idx = new BM25Index(List.of(new Chunk("bs#a", "bs", "Safari 14")));
        var a = new Assistant(idx, (s, u) -> "{\"refused\":false,\"claims\":[{\"claim\":\"c\",\"citations\":[\"bs#a\"]}]}", 1);
        server = StubServer.create(0, a);
        server.start();
        base = "http://localhost:" + server.getAddress().getPort() + "/answer";
    }
    @AfterEach void stop() { server.stop(0); }

    private HttpResponse<String> send(String method, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base)).header("content-type", "application/json");
        b = method.equals("POST") ? b.POST(HttpRequest.BodyPublishers.ofString(body)) : b.GET();
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void postReturnsExactlyRefusedAndClaims() throws Exception {
        var res = send("POST", "{\"question\":\"Which Safari?\"}");
        assertEquals(200, res.statusCode());
        JsonNode n = new ObjectMapper().readTree(res.body());
        assertEquals(java.util.Set.of("refused", "claims"), new java.util.HashSet<>(java.util.stream.StreamSupport
            .stream(java.util.Spliterators.spliteratorUnknownSize(n.fieldNames(), 0), false).toList()));
        assertEquals("bs#a", n.get("claims").get(0).get("citations").get(0).asText());
    }

    @Test void blankQuestionIs400() throws Exception { assertEquals(400, send("POST", "{\"question\":\" \"}").statusCode()); }
    @Test void getIs405() throws Exception { assertEquals(405, send("GET", null).statusCode()); }

    @Test void unparseableModelOutputIs500NotAHang() throws Exception {
        server.stop(0);
        var a = new Assistant(new BM25Index(List.of(new Chunk("bs#a", "bs", "x"))), (s, u) -> "not json", 1);
        server = StubServer.create(0, a); server.start();
        base = "http://localhost:" + server.getAddress().getPort() + "/answer";
        assertEquals(500, send("POST", "{\"question\":\"q\"}").statusCode());
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -q test -Dtest='AssistantTest,StubServerTest'`
Expected: compilation FAIL.

- [ ] **Step 3: Implement**

`Claim.java`:
```java
package assistant;

import java.util.List;

public record Claim(String claim, List<String> citations) {}
```
`AssistantResponse.java`:
```java
package assistant;

import java.util.List;

public record AssistantResponse(boolean refused, List<Claim> claims) {}
```
`Assistant.java`:
```java
package assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import llm.Llm;
import java.util.List;

public final class Assistant {
    private static final ObjectMapper M = new ObjectMapper();
    static final String SYSTEM = """
        You answer questions about Usercentrics Web CMP v2 using ONLY the documentation chunks provided.
        Reply with a single JSON object and nothing else:
        {"refused": boolean, "claims": [{"claim": string, "citations": [chunkId, ...]}]}
        Rules:
        - Each claim is one atomic factual statement. Cite the id(s) of the chunk(s) that support it.
        - If the chunks do not contain the answer, reply {"refused": true, "claims": []}. Never answer from outside knowledge.
        - Output no other text.
        """;

    private final BM25Index index;
    private final Llm llm;
    private final int topK;

    public Assistant(BM25Index index, Llm llm, int topK) { this.index = index; this.llm = llm; this.topK = topK; }

    public AssistantResponse answer(String question) {
        StringBuilder ctx = new StringBuilder();
        for (Chunk c : index.search(question, topK))
            ctx.append('[').append(c.id()).append("]\n").append(c.text()).append("\n\n");
        return parse(llm.complete(SYSTEM, ctx + "Question: " + question));
    }

    static AssistantResponse parse(String raw) {
        String s = raw.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        try {
            AssistantResponse r = M.readValue(s, AssistantResponse.class);
            List<Claim> claims = r.refused() || r.claims() == null ? List.of() : r.claims();
            return new AssistantResponse(r.refused(), claims);
        } catch (Exception e) {
            throw new IllegalArgumentException("Model output is not the expected JSON: " + raw, e);
        }
    }
}
```
`StubServer.java`:
```java
package assistant;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import llm.AnthropicLlm;
import org.yaml.snakeyaml.Yaml;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public final class StubServer {
    private static final ObjectMapper M = new ObjectMapper();

    public static HttpServer create(int port, Assistant assistant) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/answer", ex -> {
            int status; byte[] body;
            try {
                if (!ex.getRequestMethod().equals("POST")) { status = 405; body = msg("POST only"); }
                else {
                    String q = M.readTree(ex.getRequestBody()).path("question").asText("");
                    if (q.isBlank()) { status = 400; body = msg("question is required"); }
                    else { status = 200; body = M.writeValueAsBytes(assistant.answer(q)); }
                }
            } catch (Exception e) { status = 500; body = msg(e.getMessage()); }
            ex.getResponseHeaders().set("content-type", "application/json");
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        return server;
    }

    private static byte[] msg(String m) { return ("{\"error\":" + M.valueToTree(String.valueOf(m)) + "}").getBytes(StandardCharsets.UTF_8); }

    public static void main(String[] args) throws Exception {
        Map<String, Object> cfg = new Yaml().load(Files.readString(Path.of("config/assistant.yaml")));
        var chunks = Chunker.chunkDir(Path.of("docs"));
        var assistant = new Assistant(new BM25Index(chunks), AnthropicLlm.fromEnv((String) cfg.get("model")), ((Number) cfg.get("topK")).intValue());
        int port = ((Number) cfg.get("port")).intValue();
        create(port, assistant).start();
        System.out.println("Stub assistant listening on http://localhost:" + port + "/answer (" + chunks.size() + " chunks)");
    }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -q test -Dtest='AssistantTest,StubServerTest'`
Expected: PASS.

- [ ] **Step 5: Live demo (needs API key)**

Terminal A: `export ANTHROPIC_API_KEY=...; mvn -q compile exec:java -Dexec.mainClass=assistant.StubServer`
Terminal B:
```bash
curl -s localhost:8080/answer -H 'content-type: application/json' -d '{"question":"Which browsers does the CMP support?"}'
curl -s localhost:8080/answer -H 'content-type: application/json' -d '{"question":"How much does Usercentrics cost per month?"}'
```
Expected: first returns `refused:false` with claims citing `browser-support#…`; second returns `{"refused":true,"claims":[]}`. If the second answers instead, tighten `Assistant.SYSTEM` (prompt only, per D36); do not add a score cutoff.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/assistant src/test/java/assistant
git commit -m "feat: stub assistant returning cited claims or a refusal over HTTP" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: Cases, knowledge base and loader with gold-chunk validation (unit 8, loader half of unit 4)

**Files:**
- Create: `src/main/java/eval/{EvalCase,ExpectedFact,KnowledgeBase,EvalCaseLoader}.java`, `eval/cases/single-source.yaml`, `eval/cases/out-of-scope.yaml`
- Test: `src/test/java/eval/EvalCaseLoaderTest.java`

**Interfaces:**
- Consumes: `assistant.Chunk`, `assistant.Chunker.chunkDir` (the only allowed `assistant` imports in `eval/`).
- Produces:
  - `record ExpectedFact(String fact, List<String> chunks, List<String> keywords)` (keywords `[]` when absent).
  - `record EvalCase(String id, String question, String category, String subtype, String expectedBehavior, List<ExpectedFact> facts, String source, String owner, String added)`; `subtype` may be null; `expectedBehavior` is `"answer"` or `"refuse"`.
  - `KnowledgeBase(Path docsDir)` and `KnowledgeBase(List<Chunk>)`; `boolean has(String id)`; `Chunk get(String id)` (null if absent); `Set<String> ids()`.
  - `EvalCaseLoader.load(Path casesDir, KnowledgeBase kb)` returns `List<EvalCase>`; throws `IllegalArgumentException` with a message naming file and case/chunk on any problem.

- [ ] **Step 1: Write the failing test**

```java
package eval;

import assistant.Chunk;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EvalCaseLoaderTest {
    @TempDir Path dir;
    private final KnowledgeBase kb = new KnowledgeBase(List.of(new Chunk("d#a", "d", "x"), new Chunk("d#b", "d", "y")));

    private void write(String name, String yaml) throws Exception { Files.writeString(dir.resolve(name), yaml); }
    private String err(Runnable r) { return assertThrows(IllegalArgumentException.class, r::run).getMessage(); }
    private static final String META = "  source: authored\n  owner: platform\n  added: \"2026-09-24\"\n";

    @Test void loadsAnswerCaseWithGoldChunksAndOptionalKeywords() throws Exception {
        write("a.yaml", """
            - id: c1
              question: Q?
              category: single-source
              expected_behavior: answer
              facts:
                - fact: F
                  chunks: [d#a, d#b]
                  keywords: [Safari, "14"]
            """ + META);
        var c = EvalCaseLoader.load(dir, kb).get(0);
        assertEquals(List.of("d#a", "d#b"), c.facts().get(0).chunks());
        assertEquals(List.of("Safari", "14"), c.facts().get(0).keywords());
    }

    @Test void refuseCaseNeedsNoFacts() throws Exception {
        write("a.yaml", "- id: c1\n  question: Q?\n  category: out-of-scope\n  subtype: unrelated\n  expected_behavior: refuse\n" + META);
        var c = EvalCaseLoader.load(dir, kb).get(0);
        assertTrue(c.facts().isEmpty());
        assertEquals("unrelated", c.subtype());
    }

    @Test void missingGoldChunkIsHardErrorNamingCaseAndChunk() throws Exception {
        write("a.yaml", "- id: c1\n  question: Q?\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: F, chunks: [d#nope]}\n" + META);
        String m = err(() -> { try { EvalCaseLoader.load(dir, kb); } catch (java.io.IOException e) { throw new RuntimeException(e); } });
        assertTrue(m.contains("c1") && m.contains("d#nope"), m);
    }

    @Test void unquotedYamlDateIsAccepted() throws Exception {
        write("a.yaml", "- id: c1\n  question: Q?\n  category: out-of-scope\n  expected_behavior: refuse\n  source: authored\n  owner: platform\n  added: 2026-09-24\n");
        assertEquals("2026-09-24", EvalCaseLoader.load(dir, kb).get(0).added());
    }

    @Test void badInputsGiveOneClearError() throws Exception {
        String base = "- id: c1\n  question: Q?\n  category: x\n";
        write("a.yaml", base + "  expected_behavior: maybe\n" + META);
        assertTrue(err(() -> load()).contains("expected_behavior"));
        write("a.yaml", base + "  expected_behavior: answer\n" + META);          // answer with no facts
        assertTrue(err(() -> load()).contains("c1"));
        write("a.yaml", "- id: c1\n  question: Q?\n  category: x\n  expected_behavior: refuse\n" + META
            + "- id: c1\n  question: Q2?\n  category: x\n  expected_behavior: refuse\n" + META);
        assertTrue(err(() -> load()).contains("duplicate"));
        write("a.yaml", "- id: [unclosed\n");
        assertTrue(err(() -> load()).contains("a.yaml"));
        Files.delete(dir.resolve("a.yaml"));
        assertTrue(err(() -> load()).contains("no cases"));
    }

    private void load() { try { EvalCaseLoader.load(dir, kb); } catch (java.io.IOException e) { throw new RuntimeException(e); } }
}
```
Note: the `META` appended to a top-level list item must be indented as list-item fields, which the two-space indent in `META` matches.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q test -Dtest=EvalCaseLoaderTest`
Expected: compilation FAIL.

- [ ] **Step 3: Implement**

`ExpectedFact.java` / `EvalCase.java`:
```java
package eval;

import java.util.List;

public record ExpectedFact(String fact, List<String> chunks, List<String> keywords) {}
```
```java
package eval;

import java.util.List;

public record EvalCase(String id, String question, String category, String subtype, String expectedBehavior,
                       List<ExpectedFact> facts, String source, String owner, String added) {}
```
`KnowledgeBase.java`:
```java
package eval;

import assistant.Chunk;
import assistant.Chunker;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public final class KnowledgeBase {
    private final Map<String, Chunk> byId = new LinkedHashMap<>();

    public KnowledgeBase(Path docsDir) throws IOException { this(Chunker.chunkDir(docsDir)); }
    public KnowledgeBase(List<Chunk> chunks) { chunks.forEach(c -> byId.put(c.id(), c)); }

    public boolean has(String id) { return byId.containsKey(id); }
    public Chunk get(String id) { return byId.get(id); }
    public Set<String> ids() { return byId.keySet(); }
}
```
`EvalCaseLoader.java`:
```java
package eval;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import java.io.IOException;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.util.*;

public final class EvalCaseLoader {
    public static List<EvalCase> load(Path dir, KnowledgeBase kb) throws IOException {
        List<Path> files;
        try (var s = Files.list(dir)) { files = s.filter(p -> p.toString().endsWith(".yaml")).sorted().toList(); }
        List<EvalCase> cases = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path f : files) {
            Object root;
            try { root = new Yaml().load(Files.readString(f)); }
            catch (YAMLException e) { throw new IllegalArgumentException(f.getFileName() + ": invalid YAML: " + e.getMessage().lines().findFirst().orElse("")); }
            if (!(root instanceof List<?> list)) throw new IllegalArgumentException(f.getFileName() + ": expected a list of cases");
            for (Object o : list) {
                EvalCase c = parse(f.getFileName().toString(), o, kb);
                if (!ids.add(c.id())) throw new IllegalArgumentException(f.getFileName() + ": duplicate case id '" + c.id() + "'");
                cases.add(c);
            }
        }
        if (cases.isEmpty()) throw new IllegalArgumentException("no cases found in " + dir);
        return cases;
    }

    private static EvalCase parse(String file, Object o, KnowledgeBase kb) {
        if (!(o instanceof Map<?, ?> m)) throw new IllegalArgumentException(file + ": each case must be a map");
        String id = str(m, "id", file, "?", true);
        String where = file + " case '" + id + "'";
        String behavior = str(m, "expected_behavior", file, id, true);
        if (!behavior.equals("answer") && !behavior.equals("refuse"))
            throw new IllegalArgumentException(where + ": expected_behavior must be 'answer' or 'refuse', got '" + behavior + "'");
        List<ExpectedFact> facts = new ArrayList<>();
        if (m.get("facts") instanceof List<?> fl) {
            for (Object fo : fl) {
                Map<?, ?> fm = (Map<?, ?>) fo;
                List<String> chunks = strList(fm.get("chunks"));
                if (chunks.isEmpty()) throw new IllegalArgumentException(where + ": fact needs at least one gold chunk");
                for (String ch : chunks)
                    if (!kb.has(ch)) throw new IllegalArgumentException(where + ": gold chunk '" + ch + "' does not exist in the knowledge base");
                facts.add(new ExpectedFact(str(fm, "fact", file, id, true), chunks, strList(fm.get("keywords"))));
            }
        }
        if (behavior.equals("answer") && facts.isEmpty())
            throw new IllegalArgumentException(where + ": an 'answer' case needs at least one expected fact");
        Object added = m.get("added");
        String addedStr = added instanceof Date d ? d.toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString() : str(m, "added", file, id, true);
        return new EvalCase(id, str(m, "question", file, id, true), str(m, "category", file, id, true),
            str(m, "subtype", file, id, false), behavior, facts,
            str(m, "source", file, id, true), str(m, "owner", file, id, true), addedStr);
    }

    private static String str(Map<?, ?> m, String key, String file, String id, boolean required) {
        Object v = m.get(key);
        if (v == null || v.toString().isBlank()) {
            if (required) throw new IllegalArgumentException(file + " case '" + id + "': missing '" + key + "'");
            return null;
        }
        return v.toString();
    }

    private static List<String> strList(Object o) {
        return o instanceof List<?> l ? l.stream().map(String::valueOf).toList() : List.of();
    }
}
```
Seed data. `eval/cases/single-source.yaml`:
```yaml
- id: ss-safari-bundle
  question: "Which Safari version does bundle.js support?"
  category: single-source
  expected_behavior: answer
  facts:
    - fact: "bundle.js supports Safari starting from version 14"
      chunks: [browser-support#browser-support]
      keywords: ["Safari", "14"]
  source: authored
  owner: platform
  added: "2026-09-24"

- id: ss-zonejs
  question: "From which zone.js version do the Usercentrics script tags work?"
  category: single-source
  expected_behavior: answer
  facts:
    - fact: "The script tags support zone.js starting from version 0.11.4"
      chunks: [browser-support#libraries-support]
      keywords: ["zone.js", "0.11.4"]
  source: authored
  owner: platform
  added: "2026-09-24"
```
`eval/cases/out-of-scope.yaml`:
```yaml
- id: oos-pricing
  question: "How much does a Usercentrics Web CMP v2 subscription cost per month?"
  category: out-of-scope
  subtype: unrelated
  expected_behavior: refuse
  source: authored
  owner: platform
  added: "2026-09-24"

- id: oos-geo-language
  question: "How do I configure a Geolocation Rules ruleset to show a different banner based on the visitor's browser language?"
  category: out-of-scope
  subtype: plausible-nonexistent
  expected_behavior: refuse
  source: authored
  owner: platform
  added: "2026-09-24"
```

- [ ] **Step 4: Run to verify it passes, and that the seed files load**

Run: `mvn -q test -Dtest=EvalCaseLoaderTest`
Expected: PASS. (Seed files are exercised end-to-end in Task 8.)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/eval src/test/java/eval eval/cases
git commit -m "feat: eval case loader that hard-fails on a missing gold chunk" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: AssistantClient, Check interface and Refusal (unit 5; HTTP half of unit 4)

**Files:**
- Create: `src/main/java/eval/{Answer,Claim,AssistantClient}.java`, `src/main/java/eval/checks/{Check,CheckResult,Registered,Checks,RefusalCheck}.java`
- Test: `src/test/java/eval/AssistantClientTest.java`, `src/test/java/eval/checks/RefusalCheckTest.java`

**Interfaces:**
- Consumes: `EvalCase` (Task 6).
- Produces:
  - `record Claim(String claim, List<String> citations)`; `record Answer(boolean refused, List<Claim> claims)` (in `eval`; Jackson ignores unknown fields).
  - `new AssistantClient(String endpoint)`; `boolean reachable()`; `Answer ask(String question, String runId) throws IOException, InterruptedException` (sets header `X-Eval-Run: <runId>`, 60 s request timeout, non-200 throws `IOException("HTTP <status>: <body>")`).
  - `interface Check { String name(); CheckResult run(EvalCase c, Answer a); }`; `record CheckResult(boolean passed, String reason)` with `static ok()` and `static fail(String reason)`; `record Registered(Check check, boolean gating)`; `Checks.registered()` returns `List<Registered>` with Refusal first, gating.
  - Refusal reasons (exact prefixes, later reported verbatim): `"hallucination: assistant answered where it should have refused"`, `"over-refusal: assistant refused a question the docs cover"`, `"contract violation: refused=true but response has N claims"`.

- [ ] **Step 1: Write the failing tests**

`RefusalCheckTest.java`:
```java
package eval.checks;

import eval.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RefusalCheckTest {
    private final RefusalCheck check = new RefusalCheck();
    private EvalCase c(String behavior) {
        return new EvalCase("c", "q", "x", null, behavior, List.of(), "authored", "o", "2026-09-24");
    }
    private static final Claim CL = new Claim("c", List.of("d#a"));

    @Test void expectedRefuseAndRefusedPasses() { assertTrue(check.run(c("refuse"), new Answer(true, List.of())).passed()); }
    @Test void expectedAnswerAndAnsweredPasses() { assertTrue(check.run(c("answer"), new Answer(false, List.of(CL))).passed()); }

    @Test void confidentAnswerToOutOfScopeIsHallucination() {
        var r = check.run(c("refuse"), new Answer(false, List.of(CL)));
        assertFalse(r.passed());
        assertTrue(r.reason().startsWith("hallucination"));
    }
    @Test void refusingACoveredQuestionIsOverRefusal() {
        var r = check.run(c("answer"), new Answer(true, List.of()));
        assertFalse(r.passed());
        assertTrue(r.reason().startsWith("over-refusal"));
    }
    @Test void refusedWithClaimsIsContractViolation() {
        var r = check.run(c("refuse"), new Answer(true, List.of(CL)));
        assertFalse(r.passed());
        assertTrue(r.reason().startsWith("contract violation"));
    }
    @Test void refusalIsFirstAndGatingInTheRegistrationList() {
        var first = Checks.registered().get(0);
        assertEquals("Refusal", first.check().name());
        assertTrue(first.gating());
    }
}
```
`AssistantClientTest.java`:
```java
package eval;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AssistantClientTest {
    private HttpServer server;
    private final AtomicReference<String> header = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{\"refused\":false,\"claims\":[{\"claim\":\"c\",\"citations\":[\"d#a\"]}],\"extra\":1}";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/answer", ex -> {
            header.set(ex.getRequestHeaders().getFirst("X-Eval-Run"));
            body.set(new String(ex.getRequestBody().readAllBytes()));
            byte[] b = reply.getBytes();
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private String url() { return "http://localhost:" + server.getAddress().getPort() + "/answer"; }

    @Test void postsQuestionWithEvalRunHeaderAndParsesClaims() throws Exception {
        Answer a = new AssistantClient(url()).ask("Which Safari?", "run-1");
        assertEquals("run-1", header.get());
        assertTrue(body.get().contains("Which Safari?"));
        assertFalse(a.refused());
        assertEquals("d#a", a.claims().get(0).citations().get(0));
    }

    @Test void non200BecomesIOExceptionWithStatus() {
        status = 500; reply = "{\"error\":\"boom\"}";
        var e = assertThrows(IOException.class, () -> new AssistantClient(url()).ask("q", "r"));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test void garbageBodyBecomesIOException() {
        reply = "not json";
        assertThrows(IOException.class, () -> new AssistantClient(url()).ask("q", "r"));
    }

    @Test void reachableTrueWhenUpFalseWhenDown() {
        assertTrue(new AssistantClient(url()).reachable());
        server.stop(0);
        assertFalse(new AssistantClient(url()).reachable());
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -q test -Dtest='RefusalCheckTest,AssistantClientTest'`
Expected: compilation FAIL.

- [ ] **Step 3: Implement**

`Claim.java`, `Answer.java` (in `eval`):
```java
package eval;

import java.util.List;

public record Claim(String claim, List<String> citations) {}
```
```java
package eval;

import java.util.List;

public record Answer(boolean refused, List<Claim> claims) {}
```
`AssistantClient.java`:
```java
package eval;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

public final class AssistantClient {
    private static final ObjectMapper M = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI endpoint;

    public AssistantClient(String endpoint) { this.endpoint = URI.create(endpoint); }

    /** Any HTTP response counts as reachable; only a failed connection does not. */
    public boolean reachable() {
        try {
            http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public Answer ask(String question, String runId) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(60))
            .header("content-type", "application/json")
            .header("X-Eval-Run", runId)
            .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(Map.of("question", question)))).build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
        try {
            return M.readValue(res.body(), Answer.class);
        } catch (IOException e) {
            throw new IOException("response is not valid claims JSON: " + res.body(), e);
        }
    }
}
```
`checks/Check.java`, `CheckResult.java`, `Registered.java`, `Checks.java`, `RefusalCheck.java`:
```java
package eval.checks;

import eval.Answer;
import eval.EvalCase;

public interface Check {
    String name();
    CheckResult run(EvalCase c, Answer a);
}
```
```java
package eval.checks;

public record CheckResult(boolean passed, String reason) {
    public static CheckResult ok() { return new CheckResult(true, null); }
    public static CheckResult fail(String reason) { return new CheckResult(false, reason); }
}
```
```java
package eval.checks;

public record Registered(Check check, boolean gating) {}
```
```java
package eval.checks;

import java.util.List;

/** The single registration list. Add a check: one class plus one line here. Order is run order. */
public final class Checks {
    public static List<Registered> registered() {
        return List.of(
            new Registered(new RefusalCheck(), true));
    }
}
```
```java
package eval.checks;

import eval.Answer;
import eval.EvalCase;

public final class RefusalCheck implements Check {
    @Override public String name() { return "Refusal"; }

    @Override public CheckResult run(EvalCase c, Answer a) {
        int n = a.claims() == null ? 0 : a.claims().size();
        if (a.refused() && n > 0) return CheckResult.fail("contract violation: refused=true but response has " + n + " claims");
        if (c.expectedBehavior().equals("refuse") && !a.refused())
            return CheckResult.fail("hallucination: assistant answered where it should have refused");
        if (c.expectedBehavior().equals("answer") && a.refused())
            return CheckResult.fail("over-refusal: assistant refused a question the docs cover");
        return CheckResult.ok();
    }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -q test -Dtest='RefusalCheckTest,AssistantClientTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/eval src/test/java/eval
git commit -m "feat: HTTP assistant client and Refusal check behind a registration list" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: Harness, report and exit code (units 4, 6, 7; enforces 8)

**Files:**
- Create: `src/main/java/eval/{CaseResult,SuiteReport,Harness}.java`
- Test: `src/test/java/eval/SuiteReportTest.java`, `src/test/java/eval/HarnessTest.java`, `src/test/java/eval/ArchitectureTest.java`

**Interfaces:**
- Consumes: everything from Tasks 6 and 7.
- Produces:
  - `record CheckOutcome(String check, boolean gating, boolean passed, String reason)`; `record CaseResult(String id, String category, String subtype, String expectedBehavior, boolean passed, String error, Answer answer, List<CheckOutcome> checks)`.
  - `record SuiteReport(String runId, String endpoint, double passFloor, List<CaseResult> cases)` with `long passed()`, `double passRate()`, `List<String> exitReasons()`, `int exitCode()`. Exit reasons: `"pass rate 89.3% is below floor 90.0%"` and `"out-of-scope case failed: <id>[, <id>]"`. Failures in other categories never add a reason by themselves. Passing is `passed >= floor * total` computed as `passed / (double) total >= floor`.
  - `Harness.run(String[] args, Path root, PrintStream out)` returns an exit code (`0` ok, `1` run failed policy, `2` could not run). `main` calls it with `Path.of(".")` and `System.exit`. Args: `--endpoint <url>`.
  - Ordering guarantee (unit 8): docs and cases are loaded and validated before the endpoint is contacted.
  - Report file: `<root>/results/<runId>.json`, `runId` = `yyyyMMdd-HHmmss` UTC.
- Case result rule: a case passes only if no assistant error occurred and every gating check passed. An assistant error (HTTP failure, timeout, invalid JSON) fails that case with the message in `error`; the run continues.

- [ ] **Step 1: Write the failing tests**

`SuiteReportTest.java`:
```java
package eval;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SuiteReportTest {
    /** n cases; ids in failing set fail; ids starting "oos" get category out-of-scope. */
    private SuiteReport report(int n, Set<Integer> failing, Set<Integer> oos) {
        List<CaseResult> cs = new ArrayList<>();
        for (int i = 0; i < n; i++)
            cs.add(new CaseResult((oos.contains(i) ? "oos-" : "c-") + i, oos.contains(i) ? "out-of-scope" : "single-source",
                null, "answer", !failing.contains(i), null, null, List.of()));
        return new SuiteReport("r", "http://x", 0.90, cs);
    }

    @Test void twentySixOfTwentyEightMeetsFloor() {
        var r = report(28, Set.of(0, 1), Set.of());
        assertEquals(0, r.exitCode());
    }
    @Test void twentyFiveOfTwentyEightMissesFloorAndSaysWhy() {
        var r = report(28, Set.of(0, 1, 2), Set.of());
        assertEquals(1, r.exitCode());
        assertTrue(r.exitReasons().get(0).contains("89.3%") && r.exitReasons().get(0).contains("90.0%"));
    }
    @Test void nineOfTenIsExactlyOnTheFloor() { assertEquals(0, report(10, Set.of(0), Set.of()).exitCode()); }

    @Test void oneFailingOutOfScopeFailsRunEvenWhenRateIsHigh() {
        var r = report(28, Set.of(27), Set.of(27));
        assertEquals(1, r.exitCode());
        assertEquals(List.of("out-of-scope case failed: oos-27"), r.exitReasons());
    }
    @Test void nonOutOfScopeFailuresAloneDoNotTriggerTheOutOfScopeRule() {
        assertEquals(List.of(), report(28, Set.of(5), Set.of(27)).exitReasons());
    }
}
```
`HarnessTest.java` (end to end against a fake endpoint in a temp project root):
```java
package eval;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HarnessTest {
    @TempDir Path root;
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String replyFor = "{\"refused\":true,\"claims\":[]}";
    private volatile int status = 200;

    @BeforeEach void setUp() throws Exception {
        Files.createDirectories(root.resolve("docs"));
        Files.createDirectories(root.resolve("eval/cases"));
        Files.writeString(root.resolve("docs/d.md"), "## A\nbody\n");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/answer", ex -> {
            requests.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            byte[] b = replyFor.getBytes();
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        Files.writeString(root.resolve("eval/config.yaml"), "endpoint: " + url() + "\npassFloor: 0.90\n");
    }
    @AfterEach void tearDown() { server.stop(0); }
    private String url() { return "http://localhost:" + server.getAddress().getPort() + "/answer"; }

    private void cases(String yaml) throws Exception { Files.writeString(root.resolve("eval/cases/c.yaml"), yaml); }
    private static final String OOS = "- id: oos-1\n  question: How much?\n  category: out-of-scope\n  subtype: unrelated\n  expected_behavior: refuse\n  source: authored\n  owner: p\n  added: \"2026-09-24\"\n";

    private String[] out(int[] code, String... args) throws Exception {
        var buf = new ByteArrayOutputStream();
        code[0] = Harness.run(args, root, new PrintStream(buf));
        return new String[]{buf.toString()};
    }

    @Test void refusedOutOfScopePassesAndWritesTimestampedReport() throws Exception {
        cases(OOS);
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(0, code[0], o);
        assertTrue(o.contains("PASS") && o.contains("oos-1"));
        try (var s = Files.list(root.resolve("results"))) { assertEquals(1, s.filter(p -> p.toString().endsWith(".json")).count()); }
    }

    @Test void confidentAnswerToOutOfScopeFailsRunAndSaysHallucination() throws Exception {
        cases(OOS);
        replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"It costs 5 EUR\",\"citations\":[\"d#a\"]}]}";
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(1, code[0]);
        assertTrue(o.contains("answered where it should have refused"), o);
        assertTrue(o.contains("out-of-scope case failed: oos-1"), o);
    }

    @Test void assistantHttpErrorFailsThatCaseButRunCompletesWithReport() throws Exception {
        cases(OOS);
        status = 500; replyFor = "{\"error\":\"boom\"}";
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(1, code[0]);
        assertTrue(o.contains("HTTP 500"), o);
        assertTrue(Files.exists(root.resolve("results")));
    }

    @Test void missingGoldChunkStopsBeforeAnyAssistantCall() throws Exception {
        cases("- id: c1\n  question: Q?\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: F, chunks: [d#nope]}\n  source: a\n  owner: p\n  added: \"2026-09-24\"\n");
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0]);
        assertTrue(o.contains("c1") && o.contains("d#nope"), o);
        assertEquals(0, requests.get(), "no request may reach the assistant");
    }

    @Test void unreachableEndpointExplainsHowToStartTheStub() throws Exception {
        cases(OOS);
        server.stop(0);
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0]);
        assertTrue(o.contains("assistant.StubServer"), o);
        assertFalse(o.contains("ConnectException"), o);
    }

    @Test void endpointFlagOverridesConfig() throws Exception {
        cases(OOS);
        Files.writeString(root.resolve("eval/config.yaml"), "endpoint: http://localhost:1/answer\npassFloor: 0.90\n");
        int[] code = new int[1];
        out(code, "--endpoint", url());
        assertEquals(0, code[0]);
    }
}
```
`ArchitectureTest.java` (D28 boundary):
```java
package eval;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    @Test void evalOnlyImportsChunkAndChunkerFromAssistant() throws Exception {
        Set<String> allowed = Set.of("import assistant.Chunk;", "import assistant.Chunker;");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/eval"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList())
                for (String line : Files.readAllLines(f))
                    if (line.startsWith("import assistant.") && !allowed.contains(line.strip()))
                        fail(f + " breaks the HTTP-only boundary: " + line);
        }
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `mvn -q test -Dtest='SuiteReportTest,HarnessTest,ArchitectureTest'`
Expected: compilation FAIL for `SuiteReport`/`Harness`/`CaseResult`; `ArchitectureTest` alone would pass.

- [ ] **Step 3: Implement**

`CaseResult.java` (with `CheckOutcome` in the same file is not allowed for public records; use two files):
```java
package eval;

public record CheckOutcome(String check, boolean gating, boolean passed, String reason) {}
```
```java
package eval;

import java.util.List;

public record CaseResult(String id, String category, String subtype, String expectedBehavior, boolean passed,
                         String error, Answer answer, List<CheckOutcome> checks) {}
```
`SuiteReport.java`:
```java
package eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.*;

public record SuiteReport(String runId, String endpoint, double passFloor, List<CaseResult> cases) {
    public long passed() { return cases.stream().filter(CaseResult::passed).count(); }

    @JsonProperty("passRate")
    public double passRate() { return cases.isEmpty() ? 0 : passed() / (double) cases.size(); }

    @JsonProperty("exitReasons")
    public List<String> exitReasons() {
        List<String> reasons = new ArrayList<>();
        if (passRate() < passFloor)
            reasons.add(String.format("pass rate %.1f%% is below floor %.1f%%", passRate() * 100, passFloor * 100));
        List<String> oos = cases.stream().filter(c -> !c.passed() && "out-of-scope".equals(c.category())).map(CaseResult::id).toList();
        if (!oos.isEmpty()) reasons.add("out-of-scope case failed: " + String.join(", ", oos));
        return reasons;
    }

    public int exitCode() { return exitReasons().isEmpty() ? 0 : 1; }
}
```
`Harness.java`:
```java
package eval;

import com.fasterxml.jackson.databind.*;
import eval.checks.*;
import org.yaml.snakeyaml.Yaml;
import java.io.PrintStream;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Harness {
    public static void main(String[] args) throws Exception {
        System.exit(run(args, Path.of("."), System.out));
    }

    static int run(String[] args, Path root, PrintStream out) throws Exception {
        Map<String, Object> cfg = new Yaml().load(Files.readString(root.resolve("eval/config.yaml")));
        String endpoint = (String) cfg.get("endpoint");
        double floor = ((Number) cfg.get("passFloor")).doubleValue();
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--endpoint")) endpoint = args[i + 1];

        // Validate cases against the docs BEFORE contacting the assistant.
        List<EvalCase> cases;
        try {
            cases = EvalCaseLoader.load(root.resolve("eval/cases"), new KnowledgeBase(root.resolve("docs")));
        } catch (IllegalArgumentException e) {
            out.println("ERROR: " + e.getMessage());
            return 2;
        }

        AssistantClient client = new AssistantClient(endpoint);
        if (!client.reachable()) {
            out.println("ERROR: cannot reach the assistant at " + endpoint);
            out.println("Start the stub in another terminal first:");
            out.println("  export ANTHROPIC_API_KEY=...");
            out.println("  mvn -q compile exec:java -Dexec.mainClass=assistant.StubServer");
            return 2;
        }

        String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        List<CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) results.add(runCase(c, client, runId));
        SuiteReport report = new SuiteReport(runId, endpoint, floor, results);

        print(report, out);
        Files.createDirectories(root.resolve("results"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(root.resolve("results/" + runId + ".json").toFile(), report);
        out.println("Report: results/" + runId + ".json");
        return report.exitCode();
    }

    private static CaseResult runCase(EvalCase c, AssistantClient client, String runId) {
        Answer answer;
        try {
            answer = client.ask(c.question(), runId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CaseResult(c.id(), c.category(), c.subtype(), c.expectedBehavior(), false, "interrupted", null, List.of());
        } catch (Exception e) {
            return new CaseResult(c.id(), c.category(), c.subtype(), c.expectedBehavior(), false, e.getMessage(), null, List.of());
        }
        List<CheckOutcome> outcomes = new ArrayList<>();
        boolean passed = true;
        for (Registered r : Checks.registered()) {
            CheckResult res = r.check().run(c, answer);
            outcomes.add(new CheckOutcome(r.check().name(), r.gating(), res.passed(), res.reason()));
            if (r.gating() && !res.passed()) passed = false;
        }
        return new CaseResult(c.id(), c.category(), c.subtype(), c.expectedBehavior(), passed, null, answer, outcomes);
    }

    private static void print(SuiteReport r, PrintStream out) {
        for (CaseResult c : r.cases()) {
            out.printf("%-4s %-22s %-14s%n", c.passed() ? "PASS" : "FAIL", c.id(), c.category());
            if (c.error() != null) out.println("       assistant error: " + c.error());
            for (CheckOutcome o : c.checks())
                if (!o.passed()) out.println("       " + o.check() + (o.gating() ? "" : " (advisory)") + ": " + o.reason());
        }
        out.printf("%nPass rate: %d/%d (%.1f%%), floor %.1f%%%n", r.passed(), r.cases().size(), r.passRate() * 100, r.passFloor() * 100);
        if (r.exitCode() == 0) out.println("RESULT: OK");
        else r.exitReasons().forEach(x -> out.println("RESULT: FAIL - " + x));
    }
}
```
One correction while implementing: advisory failures must not fail a case (unit 17 later), and the loop above already only fails on gating checks. Keep as written.

- [ ] **Step 4: Run to verify they pass**

Run: `mvn -q test`
Expected: all tests PASS (Chunker, BM25, Llm, Assistant, StubServer, Loader, Client, Refusal, SuiteReport, Harness, Architecture).

- [ ] **Step 5: End-to-end demo with the real stub (needs API key)**

Terminal A: `export ANTHROPIC_API_KEY=...; mvn -q compile exec:java -Dexec.mainClass=assistant.StubServer`
Terminal B: `mvn -q compile exec:java -Dexec.mainClass=eval.Harness; echo "exit=$?"`
Expected: 4 seed cases listed with PASS/FAIL, pass-rate line, `results/<timestamp>.json` written. Then check each build-order acceptance point:
- Stop terminal A and rerun B: prints the "Start the stub" message, exit 2, no stack trace.
- Break a gold chunk (edit `eval/cases/single-source.yaml` to `browser-support#nope`, rerun): `ERROR:` naming `ss-safari-bundle` and `browser-support#nope`, exit 2, and no request appears in terminal A. Revert the edit.
- Hallucination demo without depending on model behavior: run an always-answering fake and point the harness at it:
  ```bash
  python3 - <<'EOF' &
  import http.server, json
  class H(http.server.BaseHTTPRequestHandler):
      def do_POST(s):
          s.rfile.read(int(s.headers['content-length']))
          b = json.dumps({"refused": False, "claims": [{"claim": "It costs 5 EUR", "citations": ["browser-support#browser-support"]}]}).encode()
          s.send_response(200); s.send_header("content-length", str(len(b))); s.end_headers(); s.wfile.write(b)
      def do_GET(s): s.send_response(405); s.end_headers()
  http.server.HTTPServer(("localhost", 8099), H).serve_forever()
  EOF
  mvn -q compile exec:java -Dexec.mainClass=eval.Harness -Dexec.args="--endpoint http://localhost:8099/answer"; echo "exit=$?"; kill %1
  ```
  Expected: both out-of-scope cases FAIL with "answered where it should have refused", `RESULT: FAIL - out-of-scope case failed: …`, exit 1.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/eval src/test/java/eval
git commit -m "feat: harness with pass floor and out-of-scope exit rule" -m "Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage (units 1–8):**
- Unit 1 (chunk IDs, five docs, source URLs, stable IDs, D33 suffixes): Tasks 1, 2.
- Unit 2 (POST /answer, claims only, BM25 top-k, model from config at temp 0): Tasks 3, 4, 5.
- Unit 3 (refusal by prompt, no cutoff, never refused+claims): Task 5 (prompt, normalisation, live check).
- Unit 4 (Harness.main, cases YAML, `X-Eval-Run`, unreachable message, registration list with gating flag, Refusal first, summary + timestamped JSON, `--endpoint`): Tasks 6, 7, 8.
- Unit 5 (hallucination / over-refusal / contract violation, no LLM): Task 7.
- Unit 6 (floor from config, 90%, 26/28 vs 25/28): Task 8 (`SuiteReportTest`).
- Unit 7 (out-of-scope failure fails run, names cases, other categories don't trigger): Task 8.
- Unit 8 (KnowledgeBase, hard error naming case and chunk, no calls before, any-of chunks): Tasks 6, 8. "Any-of" is data-shape only here; its semantics are used by unit 13.

**Placeholder scan:** none. Every code step has full code. Judge model config is deliberately absent (unit 11 adds `judgeModel`; D15 is satisfied by the assistant model living in its own file).

**Type consistency:** `Answer`/`Claim` (eval) vs `AssistantResponse`/`Claim` (assistant) are deliberately separate. `Checks.registered()` takes no arguments now; unit 9 adds the `KnowledgeBase` parameter and updates `Harness.runCase`. `EvalCase.expectedBehavior` is the string `"answer"|"refuse"` throughout. `CaseResult` fields match between `SuiteReportTest`, `Harness` and `SuiteReport`.

**Known risks to surface at review:**
1. Maven dependency versions were written from memory and not resolved offline; Task 1 Step 6 catches this.
2. `temperature: 0` may be rejected by some newer Claude models (Task 4 Step 5 checks and prescribes the response).
3. Seed case `oos-geo-language` assumes the five docs never mention browser-language rulesets (true of the fetched `geolocation-rules.md`).
4. Whether `claude-haiku-4-5-20251001` refuses reliably by prompt alone is unknown until the live run. A weak stub is intended (D36), so a failing out-of-scope case is a finding, not a bug.

## Units 9–26 (outline only; each gets its own plan after this lands)

- **Plan 2, units 9–16:** Citation integrity, Coverage (keyword filter, judge confirm, judge-picks), Groundedness (gold shortcut + judge), 20-pair calibration, Source. Introduces `Judge` on top of `Llm`, `judgeModel` in `eval/config.yaml`, and changes `Checks.registered(kb, judge)`.
- **Plan 3, units 17–22:** Relevance (advisory), baseline + regression, re-run, measured cost (wrap `Llm`), full 28-case set, doc-hash warning. Extends `SuiteReport` with category and out-of-scope-subtype rollups.
- **Plan 4, units 23–26:** live-change rehearsal, `PATTERN.md`, the scale plan, README (README can move up right after this plan since unit 26 only depends on unit 4).

## Verification (whole plan)

1. `mvn clean package` — all green (76 tests), no network, no API key needed.
2. `java -cp kb/target/classes kb.Chunker` — one ID per section across all five docs; `tcf2#non-iab-vendors-1` and `-2` present.
3. With `ANTHROPIC_API_KEY` set: `java -jar assistant/target/assistant.jar`, then `java -jar eval/target/eval.jar` from the repo root; covered question returns cited claims, pricing question refuses, harness prints summary and writes `results/<ts>.json`.
4. Task 8 Step 5 negative demos (use the jars instead of `exec:java`): stub down (exit 2, start instructions), bad gold chunk (exit 2, no requests), always-answer fake on `--endpoint` (exit 1, hallucination named).

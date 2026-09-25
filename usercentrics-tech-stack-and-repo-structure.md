# Tech Stack & Repo Structure — Usercentrics Take-Home

Updated after the grilling session. See `usercentrics-eval-harness-plan.md` for the design and `grilling-decisions.md` for the decision log.

## Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language | **Java** (whatever version you're fastest in — 17+ recommended for `HttpClient`, records) | Fastest language for live, under-pressure code changes — the stated constraint that overrides ecosystem convenience. |
| Retrieval (BM25) | **Hand-rolled**, ~40–50 lines | No real complexity to hide behind a library; implementing it yourself makes "I wrote this" a true and strong sentence in the walkthrough. Lucene is the fallback if BM25-by-hand eats too much time. |
| YAML parsing | **SnakeYAML** | One dependency, trivial API, standard choice. |
| JSON (chunks, claims, eval results) | **Jackson** (`jackson-databind`) | Same reasoning as SnakeYAML — one well-known dependency, not worth hand-rolling. |
| LLM calls | **Java's built-in `HttpClient`** (`java.net.http`, Java 11+) direct to the provider's REST API | Zero extra dependency. It's a POST with a JSON body — no SDK needed for what this build does. The client also records calls and tokens per role and per check, for the measured cost report. |
| Harness ↔ assistant | **HTTP**: the harness POSTs `{question}` to an endpoint and reads `{refused, claims}` back, using the same `HttpClient`. | The assistant runs as a separate service, so the harness code is identical for the stub and for any other app on the hub, in any language. |
| Stub assistant server | **Spring Boot 3.3** (`spring-boot-starter-web`), one `@RestController` | Reviewer asked for it (D37). Only the `assistant` module carries Spring; the harness stays plain Java. Started separately from the harness; the harness pre-checks that the endpoint is reachable and prints how to start the stub if not. |
| Build | **Maven, multi-module**: `kb`, `llm`, `assistant`, `eval` under one parent POM | The module graph enforces the HTTP-only boundary (D28, D37): `eval` has no dependency on `assistant`. |
| Testing/running | JUnit 5 for deterministic code; the harness itself is a plain `main()` | The harness is the eval framework; JUnit only guards its own deterministic parts. |

**Two commands:** `mvn -q -DskipTests package`, then start the stub (`java -jar assistant/target/assistant.jar`) and run the harness (`java -jar eval/target/eval.jar`), which is the one command the brief asks for. Both run from the repo root. `--config <file>` runs it with another team's config (paths in it resolve against the config file's directory, from any working directory); every setting in the config can be overridden for one run with `--<setting> <value>` (`--endpoint <url>` points the harness at any other app, `--skipCalibration true` skips the judge trap pairs and the Groundedness sample that otherwise run first, `--baseline <file>` compares with a previous report, promoted by copying it to `caseResults/baseline.json`, and fails the run on a regression after one re-run; `--skip-calibration` is a shorthand).

**Models:** the assistant model and the judge model are separate config values (both routed through OpenRouter, D39), with the judge stronger than the assistant. The Coverage confirm step uses the main judge. Temperature is 0 everywhere.

**Explicitly not used, and why (for the walkthrough/Q&A):**
- **Python + rank_bm25/pyyaml** — considered first; dropped because live-session code changes need to happen in the language you're fastest in under pressure, not the one with marginally more convenient libraries for a 5-hour build.
- **promptfoo / DeepEval** — considered; a ~300-line custom runner is the right call when the brief requires knowing every line live.
- **Elasticsearch or a vector DB** — no infrastructure needed for 5–6 doc pages; BM25 in memory is enough.
- **A separate confirm model for Coverage** — the narrow "do these agree?" question is cheap on the main judge, and one judge config is simpler than three model settings.

---

## Repo structure

**Module layout (D37, supersedes the paths in the tree below):** `kb/` (Chunk, Chunker), `llm/` (Llm, OpenRouterLlm), `assistant/` (Spring Boot: Assistant, BM25Index, StubServer, AnswerController), `eval/` (harness sources under `eval/src`, plus `eval/config.yaml` and `eval/cases/`). `docs/`, `config/application.yaml` and `caseResults/` stay at the repo root. `Chunk` and `Chunker` are in package `kb`, not `assistant`.

```
usercentrics-eval-harness/
├── README.md                      # 2–3 min orientation: how to start the stub, how to run the harness
├── pom.xml                        # or build.gradle
├── CONTEXT.md                     # glossary of domain terms
├── grilling-decisions.md          # decision log behind the plan
│
├── docs/                          # Step 1 — the knowledge base
│   ├── browser-support.md
│   ├── ab-test.md
│   ├── geolocation-rules.md
│   ├── consent-mode.md
│   └── tcf2.md
│   # each file: source URL in a comment/frontmatter at the top,
│   # content copied/cleaned from the live page
│
├── src/main/java/assistant/       # the app under test — a separate service
│   ├── Chunk.java                 # id (doc#heading), sourceDoc, text
│   ├── Chunker.java               # splits docs/*.md into Chunks with heading-based IDs
│   ├── BM25Index.java             # hand-rolled BM25 over the chunks
│   ├── Claim.java                 # claim text + citations (chunk IDs)
│   ├── AssistantResponse.java     # refused flag + List<Claim>; no free-text field
│   ├── Assistant.java             # question -> retrieve -> generate -> AssistantResponse
│   └── StubServer.java            # main() — HttpServer exposing POST /answer
│
├── src/main/java/eval/            # the evaluation layer
│   ├── EvalCase.java               # question, category, expected_behavior, expected facts, source/owner/added
│   ├── ExpectedFact.java           # fact, chunks (gold, any-of), optional keywords
│   ├── EvalCaseLoader.java         # reads eval/cases/*.yaml; fails loudly on a gold chunk that doesn't exist
│   ├── KnowledgeBase.java          # loads docs/ via Chunker; chunk lookup for integrity and groundedness
│   ├── AssistantClient.java        # HTTP adapter: POST question, parse AssistantResponse
│   ├── LlmClient.java              # thin wrapper over HttpClient → provider API; records calls and tokens
│   ├── Judge.java                  # shared LLM-judge call wrapper (Coverage confirm, Groundedness fallback, Relevance)
│   ├── checks/
│   │   ├── Check.java              # the one interface: (case, response) -> pass/fail + reason
│   │   ├── Checks.java             # the single registration list, with a gating flag per check
│   │   ├── CoverageCheck.java      # keyword filter, then judge confirm; finds covering claims
│   │   ├── CitationIntegrityCheck.java  # deterministic: ≥1 citation, every cited ID exists
│   │   ├── GroundednessCheck.java  # gold-chunk match passes; otherwise judge
│   │   ├── SourceCheck.java        # deterministic: cited docs match the fact's gold-chunk docs
│   │   ├── RelevanceCheck.java     # judge; advisory (not gating)
│   │   └── RefusalCheck.java       # deterministic: refused flag + claim count vs expected_behavior
│   ├── CaseResult.java             # per-case pass/fail + per-check detail
│   ├── SuiteReport.java            # overall + by-category (and out-of-scope subtype) pass rate; call/token/cost/time totals
│   ├── Baseline.java               # loads a previous results file; finds pass→fail flips (with one re-run)
│   └── Harness.java                # main() — endpoint pre-check, loops cases, runs checks, prints + writes report, sets exit code
│
├── eval/
│   ├── config.yaml                 # endpoint, assistant model, judge model, pass floor
│   └── cases/
│       ├── single-source.yaml      # 8 cases
│       ├── multi-source.yaml       # 6 cases
│       ├── out-of-scope.yaml       # 5 cases (~2 unrelated, ~3 plausible-nonexistent)
│       ├── false-premise.yaml      # 6 cases
│       └── edge-cases.yaml         # 3 cases: exact-value, multi-ask, light paraphrase
│
├── calibration/
│   ├── labeled-sample.yaml         # 20 hand-labeled claim/chunk pairs: 10 subtly unsupported, 5 supported, 5 hard-supported
│   ├── trap-pairs.yaml             # ~5 negation traps for the Coverage confirm step
│   └── calibration-notes.md        # agreement rate, false-"supported" count, what you adjusted
│
├── caseResults/
│   ├── .gitkeep                    # harness writes timestamped JSON reports here
│   └── baseline.json               # a previous report promoted by copying it (the known-good run)
│
├── docs-pattern/
│   └── PATTERN.md                  # Step 5 deliverable — the reusable pattern doc
│
└── the scale plan                   # Step 6 deliverable — one-pager for the live session
```

**A couple of structural notes worth keeping in mind while building:**
- `assistant/` and `eval/` are kept as separate Maven modules on purpose — it's the physical expression of the "contract" framing from the plan doc (application vs. evaluation layer), and it's a good thing to point at directly during the code walkthrough. **`eval/` must not depend on `assistant/`**: it only talks to the app through `AssistantClient` over HTTP. The shared `Chunk` and `Chunker` live in the small `kb` module, used by both sides, so the harness can look up chunk IDs and text without pulling in Spring.
- `checks/` being one class per check behind one `Check` interface, with one registration list in `Checks.java`, is what makes "add a new check" a small, explainable diff if they ask for a live change there. No check is wired into `Harness` by name. The gating flag lives in the same list, so "make Relevance gating" is a one-word change.
- The other two rehearsed live changes: flip the pass floor (`eval/config.yaml`), and add a case (a YAML entry, no code).
- `caseResults/` writing timestamped JSON (not overwriting a single file) is what makes regression diffing possible: promote a report by copying it to `baseline.json`, and pass `--baseline`.
- **Not built in v1:** the per-app `checks` list and risk-tier config, which are described in the pattern doc and scale plan as the onboarding design. The doc-hash staleness warning is built only if time allows. See the cut order in the plan.

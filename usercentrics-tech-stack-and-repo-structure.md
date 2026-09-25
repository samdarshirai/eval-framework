# Tech Stack & Repo Structure — Usercentrics Take-Home

Updated after the grilling session. See `usercentrics-eval-harness-plan.md` for the design and `grilling-decisions.md` for the decision log.

## Tech stack

| Concern | Choice | Why |
|---|---|---|
| Language | **Java** (whatever version you're fastest in — 17+ recommended for `HttpClient`, records) | Fastest language for live, under-pressure code changes — the stated constraint that overrides ecosystem convenience. |
| Retrieval (BM25) | **Hand-rolled**, ~40–50 lines | No real complexity to hide behind a library; implementing it yourself makes "I wrote this" a true and strong sentence in the walkthrough. Lucene is the fallback if BM25-by-hand eats too much time. |
| YAML parsing | **SnakeYAML** | One dependency, trivial API, standard choice. |
| JSON (chunks, claims, eval results) | **Jackson** (`jackson-databind`) | Same reasoning as SnakeYAML — one well-known dependency, not worth hand-rolling. |
| LLM calls | **Java's built-in `HttpClient`** (`java.net.http`, Java 11+) direct to the provider's REST API | Zero extra dependency. It's a POST with a JSON body — no SDK needed for what this build does. `MeteredLlm` records judge calls and tokens per check into `UsageMeter` for the measured cost report (D50). |
| Harness ↔ assistant | **HTTP**: the harness POSTs `{question}` to an endpoint and reads `{refused, claims}` back, using the same `HttpClient`. | The assistant runs as a separate service, so the harness code is identical for the stub and for any other app on the hub, in any language. |
| Stub assistant server | **Spring Boot 3.3** (`spring-boot-starter-web`), one `@RestController` | Reviewer asked for it (D37). Only the `assistant` module carries Spring; the harness stays plain Java. Started separately from the harness; the harness pre-checks that the endpoint is reachable and prints how to start the stub if not. |
| Build | **Maven, multi-module**: `kb`, `llm`, `assistant`, `eval` under one parent POM | The module graph enforces the HTTP-only boundary (D28, D37): `eval` has no dependency on `assistant`. |
| Testing/running | JUnit 5 for deterministic code; the harness itself is a plain `main()` | The harness is the eval framework; JUnit only guards its own deterministic parts. |

**Two commands:** `mvn -q -DskipTests package`, then start the stub (`java -jar assistant/target/assistant.jar`) and run the harness (`java -jar eval/target/eval.jar`), which is the one command the brief asks for. Both run from the repo root. `--config <file>` runs it with another team's config (paths in it resolve against the config file's directory, from any working directory); every setting in the config can be overridden for one run with `--<setting> <value>` (`--endpoint <url>` points the harness at any other app, `--skipCalibration true` skips the judge trap pairs and the Groundedness sample that otherwise run first, `--baseline <file>` compares with a previous report, promoted by copying it to `caseResults/baseline.json`, and fails the run on a regression after one re-run; `--skip-calibration` is a shorthand, and `--case <id>[,<id>...]` runs only the named cases (D52)).

**Models:** the assistant model and the judge model are separate config values (both routed through OpenRouter, D39), with the judge stronger than the assistant. The Coverage confirm step uses the main judge. Temperature is 0 everywhere.

**Explicitly not used, and why (for the walkthrough/Q&A):**
- **Python + rank_bm25/pyyaml** — considered first; dropped because live-session code changes need to happen in the language you're fastest in under pressure, not the one with marginally more convenient libraries for a 5-hour build.
- **promptfoo / DeepEval** — considered; a ~300-line custom runner is the right call when the brief requires knowing every line live.
- **Elasticsearch or a vector DB** — no infrastructure needed for 5–6 doc pages; BM25 in memory is enough.
- **A separate confirm model for Coverage** — the narrow "do these agree?" question is cheap on the main judge, and one judge config is simpler than three model settings.

---

## Repo structure

**Module layout (D37):** `kb/` (Chunk, Chunker), `llm/` (Llm, OpenRouterLlm), `assistant/` (Spring Boot: Assistant, BM25Index, StubServer, AnswerController), `eval/` (harness sources under `eval/src`, plus `eval/config.yaml` and `eval/cases/`). `docs/`, `config/application.yaml` and `caseResults/` stay at the repo root. `Chunk` and `Chunker` are in package `kb`, not `assistant`.

```
usercentrics-eval-harness/
├── README.md                      # 2-3 min orientation: how to start the stub, how to run the harness
├── PATTERN.md                     # the reusable pattern doc (was planned as docs-pattern/PATTERN.md)
├── the scale plan                  # the one-pager for the live session
├── pom.xml                        # parent POM: modules kb, llm, assistant, eval
├── CONTEXT.md                     # glossary of domain terms
├── scope.md                       # what is in and out, and the cut list
├── build-order.md                 # units 1-26 with acceptance criteria
├── grilling-decisions.md          # decision log (D1-D51)
├── usercentrics-eval-harness-plan.md
│
├── docs/                          # the knowledge base: 5 public pages, each with a source URL in front matter
│   ├── browser-support.md  ab-test.md  geolocation-rules.md  consent-mode.md  tcf2.md
│
├── config/application.yaml        # Spring config for the stub (127.0.0.1)
├── kb/                            # Chunk, Chunker: heading-based chunk IDs, shared by stub and harness
├── llm/                           # Llm, Completion, OpenRouterLlm (temperature 0, require_parameters)
├── assistant/                     # the app under test: Spring Boot stub
│   └── src/main/java/assistant/   # Assistant, BM25Index, AssistantResponse, Claim, AnswerController, StubServer
│
├── eval/                          # the evaluation layer (never depends on assistant)
│   ├── config.yaml                # endpoint, passFloor, judgeModel, judgePricing, categories, knowledgeBase, ...
│   ├── cases/                     # single-source, multi-source, false-premise, out-of-scope, edge-case (.yaml, 28 cases)
│   └── src/main/java/eval/
│       ├── Harness, CliArgs, EvalConfig      # main(), --config and --<setting> overrides
│       ├── SuiteRunner, CaseRunner, CaseState, CaseResult, SuiteReport, ConsoleReport, ReportWriter
│       ├── AssistantClient, Assistant, Answer, Claim   # HTTP adapter and contract types
│       ├── Judge, MeteredLlm, UsageMeter     # judge calls and the measured cost report
│       ├── Baseline, StaleCases, CaseHash, StampCaseHashes   # regression and doc-hash warning
│       ├── DebugLog, CalibrationRun          # --debug trace, judge calibration run
│       ├── EvalCaseLoader, EvalCase, Expected, ExpectedFact, KnowledgeBase
│       ├── knowledge/                        # KnowledgeSource: docs (built), http and manifest (placeholders)
│       ├── calibration/                      # LabeledSample, TrapPairs
│       └── checks/                           # Check, Checks (the registration list), Registered, and one class per check:
│                                             #   Refusal, CitationIntegrity, Coverage, Groundedness, Source, Relevance (advisory)
│   └── src/main/resources/calibration/       # trap-pairs.yaml (7 pairs), labeled-sample.yaml (20 pairs), bundled in the jar
│
├── calibration/calibration-notes.md          # measured agreement, false-"supported" count, limits
├── caseResults/                              # timestamped JSON reports; baseline.json is the promoted reference
└── .github/workflows/test.yml                # mvn test on every PR (LLM mocked, no key needed)
```

**A couple of structural notes worth keeping in mind while building:**
- `assistant/` and `eval/` are kept as separate Maven modules on purpose — it's the physical expression of the "contract" framing from the plan doc (application vs. evaluation layer), and it's a good thing to point at directly during the code walkthrough. **`eval/` must not depend on `assistant/`**: it only talks to the app through `AssistantClient` over HTTP. The shared `Chunk` and `Chunker` live in the small `kb` module, used by both sides, so the harness can look up chunk IDs and text without pulling in Spring.
- `checks/` being one class per check behind one `Check` interface, with one registration list in `Checks.java`, is what makes "add a new check" a small, explainable diff if they ask for a live change there. No check is wired into `Harness` by name. The gating flag lives in the same list, so "make Relevance gating" is a one-word change.
- The other two rehearsed live changes: flip the pass floor (`eval/config.yaml`), and add a case (a YAML entry, no code).
- `caseResults/` writing timestamped JSON (not overwriting a single file) is what makes regression diffing possible: promote a report by copying it to `baseline.json`, and pass `--baseline`.
- **Not built in v1:** the per-app `checks` list and risk-tier config, which are described in the pattern doc and scale plan as the onboarding design. The doc-hash staleness warning is built (D48): cases carry an optional confirmed_hash, written by eval.StampCaseHashes. See the cut order in the plan.

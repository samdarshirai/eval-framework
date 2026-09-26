# How To: evaluating an AI Hub application

## Does this fit your application?

| Application shape | What your endpoint returns | Checks (`appType`) | Your first ~10 cases | Status |
|---|---|---|---|---|
| Answers questions from documents, with citations | Claims with citations (section 1) | `cited` | 4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise | Built |
| Generates text without sources | Claims without citations | `uncited`, `addChecks: [Relevance]` | Expected facts the output must contain, plus out-of-scope requests it must refuse | Built (Refusal, Coverage, Relevance); needs a config update to set `appType`/`addChecks` |
| Answers questions from documents, in prose | Prose plus sources, through a prose adapter (section 9) | `cited` once the adapter exists | As the first row | Ask the platform team to add the adapter |
| Extracts fields or classifies | Not a fit: it needs field-level exact match or label accuracy, not claims | None | None | Not supported, talk to the platform team |

- Find your row, then continue below.
- The first row is the only fully built path. The others need a config choice or the platform team.

## What you get

One command runs your application against a set of test cases and prints a pass/fail report. It exits non-zero when the application is not good enough to ship:

```
java -jar eval/target/eval.jar --config my-team/eval.yaml
```

The harness does not care what your application is built on. It only needs the contract in section 1. It isn't just string matching: it checks whether each fact is covered, whether each claim is supported by the source it cites, whether the application refuses what it should not answer, and whether a change made a previously passing case fail.

## 1. Build your endpoint: the contract

Your application exposes one HTTP endpoint.

**Request:** `POST <endpoint>`, `Content-Type: application/json`

```json
{"question": "Which Safari version does bundle.js support?"}
```

**Response:** HTTP 200 and this JSON, and nothing else:

```json
{
  "refused": false,
  "claims": [
    {"claim": "bundle.js supports Safari starting from version 14",
     "citations": ["browser-support#browser-support"]}
  ]
}
```

Rules that the checks depend on:

- **There is no free-text answer field.** Everything the application asserts is a **claim**: one atomic factual statement plus the citations that support it.
- A **citation** is a chunk ID: the document name plus the section heading (`consent-mode#default-consent-states`). 
- A refusal is `{"refused": true, "claims": []}`. `refused: true` together with claims is a contract violation and fails the case.
- Any status other than 200, or a body that is not this shape, fails that case with the error text. A slow call fails after 60 seconds.
- The harness sends an `X-Eval-Run: <run id>` header on every request so your gateway can exclude eval traffic from analytics.

If your application returns free text instead of structured claims, splitting it into cited claims needs a prose adapter, which the platform team adds (section 9).

**Chunk IDs must be resolvable.** The harness must know the chunks your application cites, so that it can check that a citation exists and read what it says. Section 2 shows how it gets them.

**Fallback for a team that cannot expose an endpoint**: export answers to a JSON file and have the harness score the file or reach out to the Platform Team.

## 2. Configuration

Every setting is a key in a config file, and `--<key> <value>` overrides it for one run. The value is read as YAML, so it has the same type as in the file. An unknown key is an error.

A minimal team config, `my-team/eval.yaml`:

```yaml
endpoint: http://localhost:9000/answer      # your application, started for testing
passFloor: 0.90
judgeModel: anthropic/claude-opus-4.8       # OpenRouter slug, stronger than your app's model
categories: [single-source, multi-source, false-premise, out-of-scope]
knowledgeBase:
  type: docs
  path: docs                                # markdown files, relative to this file
# cases: cases                              # default: a "cases" folder next to this file
# outputDir: caseResults
# baseline: caseResults/baseline.json
# appType: cited                            # cited | uncited | smoke — default is cited (section 5)
# addChecks: [Relevance]                    # adds to appType's set, never removes (section 5)
# assistantVersion: v1.2.0                  # label for provenance in the report (section 6)
# calibration:
#   trapPairs: my-trap-pairs.yaml
#   labeledSample: calibration/labeled-sample.yaml
```

Run it from anywhere with `--config my-team/eval.yaml`. Every relative path in the file resolves against the file's folder. 

The judge needs `LLM_API_KEY` in the environment and calls OpenRouter by default. To use another OpenAI-compatible provider or an internal gateway, set `llmBaseUrl` in the config (or `--llmBaseUrl`, for example `https://llm-gateway.example.com/v1`), put that provider's key in `LLM_API_KEY`, and set `judgeModel` to a model id it accepts; the harness appends `/chat/completions`. 

**Untested with non-OpenRouter endpoints:** the request also carries OpenRouter-specific fields (`provider.require_parameters`, `reasoning.effort`) that a strict gateway may reject with a 400, so try one run against your gateway before relying on it. Your own application can use any model provider.

Useful overrides: `--case id1,id2` runs only those cases, `--appType uncited` picks the check set, `--skip-calibration` skips the judge calibration, `--debug` prints one `[debug]` line per config, case, check, assistant call and judge call, and `--baseline ""` switches a configured baseline off for one run. Keep `out-of-scope` in `categories`, because the exit rule depends on it (section 7).

This minimal config doesn't set `appType`, so it defaults to `cited` (five checks; Relevance is added with `addChecks`). Section 5 covers what each check does and the other app types.

### Where the harness gets the chunks

The harness needs the same chunk IDs and text your application has. `knowledgeBase.type` chooses the source:

- **`docs`** (built, the default): a folder of markdown files, chunked by heading. `browser-support.md` with a `## Browser Support` section gives the ID `browser-support#browser-support`. This works if your citations use the same `document#heading-slug` scheme — if your application chunks differently, its citations won't resolve, so use `http`/`manifest` (or your own `KnowledgeSource`) and export your own chunks instead.
- **`http`** and **`manifest`** (placeholders, selecting one exits with "not implemented yet"): the application serves, or exports at build time, a list of `{id, text}`. This is the better fit for an application in any language, because the harness sees exactly what the application indexed.
- **Your own source**: needs a new `KnowledgeSource` implementation in the harness itself - Ask the platform team if `docs`, `http` or `manifest` don't fit.

Whatever the source, the application and the harness must see the **same version** of the documents. A case that names gold chunks whose text changed since it was confirmed produces a warning that names the case, and it still runs — this only fires for cases that have `confirmed_hash` set (section 3).

## 3. Writing test cases

Start with about 10 cases across the four categories: 4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise. Growing the set beyond that is covered in section 8.

A case is a YAML entry. Cases live in `*.yaml` files in one folder, with any number of cases per file:

```yaml
- id: ss-safari-bundle
  question: "Which Safari version does bundle.js support?"
  category: single-source
  expected_behavior: answer          # answer | refuse, set per case
  facts:
    - fact: "bundle.js supports Safari starting from version 14"
      chunks: [browser-support#browser-support]   # gold chunks: citing any ONE is enough
      confirmed_hash: <hash>                       # hash of the gold chunks' text, set when confirmed — see below
      keywords: ["Safari", "14"]                  # optional, see below
  source: authored                   # or e.g. support-ticket-1234
  owner: platform                    # who to ask when the case looks stale
  added: "2026-09-24"
```

- **`facts`** are plain-language key points the answer must contain. They are not tied to a sentence count or wording, because the application may split one fact across several claims.
- **`chunks`** are the **gold chunks**: chunks a human has verified support the fact. They are alternatives, so citing any one of them is enough. Citing another chunk is not automatically wrong. It is just not pre-verified. Gold chunks are used by Coverage and Source, not to skip Groundedness.
- **`confirmed_hash`** is a hash of the gold chunks' text, recorded when a human confirmed the case. If a document changes afterwards, the loader warns and names the case, but only for cases that have this field. Stamp it after you confirm a case, and re-stamp after a deliberate doc change.
- **`keywords`** are optional, and allowed only for an exact value or identifier that any correct answer must contain verbatim: a version number (`14`, `0.11.4`), an API, variable, event or attribute name (`getTCData`, `UC_AB_VARIANT`, `data-tcf-enabled`), a literal config value (`denied`) or a product acronym (`TCF`). A claim must contain all of them before it can count as covering the fact. They are a cheap filter that catches near-misses ("Safari 13" for "Safari 14") without a model call. They are never sufficient on their own, because they cannot detect negation ("all Safari versions except 14"), so a judge confirms every keyword hit.
- **Never use an ordinary word or phrase** ("invalid", "delete", "default", "before", "all users", "v2"). A correct answer can paraphrase it ("no longer valid", "version 2"). When a claim lacks a keyword, no judge is called and the fact counts as missed, so the paraphrase is a false negative. All keywords must also appear in one claim, so a fact the application splits across two claims fails too. For a value a miss is a real miss; for a word it is not.
- Leave `keywords` out when there is no such value or identifier. A judge then decides which claims cover the fact. If a fact mixes both, keep the values and drop the words.
- A `refuse` case has no `facts`. An `answer` case must have at least one.

### The four categories to start with

| Category | The question | Expected |
|---|---|---|
| single-source | Answerable from one document | answer, with the fact cited |
| multi-source | Needs two documents combined | answer covering a fact from each |
| out-of-scope | The knowledge base cannot answer it | **refuse** |
| false-premise | Built on a wrong assumption about something the docs cover | answer, with a claim that **corrects** the premise |

For out-of-scope, add a `subtype` so the report shows what the application fails on: `unrelated` (pricing, a coding request) or `plausible-nonexistent` (sounds like a real feature but is not in the docs). The second is the hardest hallucination bait.

For false-premise, the expected fact is the correction itself. Going along with the premise fails the case, and refusing also fails it, because the docs do cover the topic.

## 4. Run it

Build once from the repo root:

```
mvn -q -DskipTests package
```

Then run, with `LLM_API_KEY` set in your environment:

```
java -jar eval/target/eval.jar --config my-team/eval.yaml
```

The judge calibration (section 5) runs first on every run by default. Add `--skip-calibration` while you're iterating on cases — a scheduled job can run the full calibration separately, and CI can too. See section 6 for what the console output means, and section 7 for what the exit code means.

## 5. The menu of checks

Each case runs the enabled checks (the app type's set, default `cited`, section 2), in this order. A case passes only if every **gating** check passes.

| Check | Type | What it decides | Gating |
|---|---|---|---|
| **Refusal** | deterministic | Refused when it should have, and answered when it should have. Catches over-refusal, a confident answer to a question it should refuse, and refused-with-claims. | yes |
| **Citation integrity** | deterministic | Every claim cites at least one chunk, and every cited chunk exists. A citation to a chunk that does not exist is a fabricated citation and fails hard. | yes |
| **Coverage** | keyword filter, then judge | Each expected fact is covered by a claim that says it, not one that contradicts it. | yes |
| **Groundedness** | judge | Each claim is supported by a chunk it cites. Every claim goes to a judge, including claims that cite a gold chunk, because Coverage accepts extra detail and only this check can catch an invented addition to a correct fact. | yes |
| **Source** | deterministic | A covering claim cites a document that holds one of the fact's gold chunks. This proves a multi-source answer used both documents. | yes |
| **Relevance** | judge | The claim is pertinent to the question and not true-but-off-topic padding. | **no, advisory** |

**Advisory** means reported and counted but never able to fail a case.

### App types

`appType` (in the config, or `--appType`) picks which checks run. It defaults to `cited`.

- **`cited`**: Refusal, Citation integrity, Coverage, Groundedness, Source.
- **`uncited`**: Refusal, Coverage. For text without sources.
- **`smoke`**: Refusal, Citation integrity. A cheap check that the endpoint works and cites real chunks.

`addChecks: [Relevance]` adds checks on top of the app type's set. It never removes any.

Startup fails with exit 2 on an unknown `appType`, or when `Refusal` is off while the cases include out-of-scope ones.

### The judge, and why you should measure it

Coverage (the model part), Groundedness and Relevance use a judge model, set with `judgeModel` and called at temperature 0. Before you trust it, calibrate it: add your own trap pairs and hand-labeled supported/unsupported pairs, written from your own documents, under `calibration.trapPairs` and `calibration.labeledSample` in your config. The bundled pairs are written from our own documentation in docs/, so until you replace them, a passing calibration tells you nothing about your own application.

## 6. Reading a report

The console prints one block per failing case, then a rollup:

```
FAIL fp-tcf-gettcdata       false-premise
       Refusal: over-refusal: assistant refused a question the docs cover
       Coverage: not covered: "The getTCData command is not available anymore in TCF 2.2"
By category
  false-premise            0/1
Pass rate: 0/1 (0.0%), floor 90.0%
RESULT: FAIL - pass rate 0.0% is below floor 90.0%
Report: caseResults/20260925-184858.json
```

- Each failing case names the check that failed and why. Two checks failing at once usually have one cause: here the refusal is also why Coverage found nothing.
- The rollup shows the pass rate overall, by category, and for out-of-scope by subtype.
- **Cost and time** are measured: judge calls, tokens and estimated cost per check (from `judgePricing` in the config), and wall-clock time.
- **Provenance** in the JSON report says what the run was measured with: the judge model, a hash of the judge prompts, the harness git commit (`-dirty` if the tree had uncommitted changes), and an optional `assistantVersion` label you set in the config. Set `assistantVersion` whenever you change the application, because the harness cannot see inside it. Without these, a regression against the baseline cannot be tied to a change.
- The JSON report has the full detail: the question, the expected facts, what the assistant returned, and every check with its reason. When a failure is unclear, read the JSON, or rerun the single case with `--case <id> --debug`.

To find out whether a failure is the application or the case, look at the claims the application returned. If it cited the wrong chunk or none, it is the application. If its answer is right and the check says "not covered", suspect the case, and read the fact and gold chunk again. The first run mostly exposes case mistakes, not application bugs — fix the case first, then re-run before concluding the application is at fault.

## 7. Thresholds, baselines and the exit code

The run exits **0** when everything passes, **1** when it fails, and **2** for a setup error (a bad config, a case that names a missing chunk, an unreadable baseline, no API key). It exits 1 when any of these is true:

1. **The pass rate is below `passFloor`** (the example config uses 0.90). In v1 the floor is just a config value. The design for other applications lets departments raise it and never lower it below a platform minimum.
2. **Any out-of-scope case fails**, whatever the overall rate. A confident answer to a question the docs cannot answer is the biggest risk, so it is never averaged away.
3. **A regression against the baseline**: a case that passed in the baseline and fails now, even when the overall rate is above the floor.
4. **The judge fails calibration** (section 5).

### The baseline

A **baseline** is a previous run's report that later runs are compared against. Normally it is a run you trust. Every run writes a timestamped JSON report to `caseResults/`. To promote one:

```
cp caseResults/<run id>.json caseResults/baseline.json
```

- A case that failed in the baseline and passes now is listed as `improved since baseline`, which is a hint to promote a newer baseline. It never changes the exit code.
- A suspected regression is **re-run once** and only counts if it fails twice, and the report shows which cases needed a re-run. The re-run count is a free measure of how flaky your suite is.
- A baseline that is missing, unreadable, or shares no case with this run exits 2. 

Once you trust a report promote it as your baseline.

## 8. Growing the set

1. **Every case records where it came from** (`source`, `owner`, `added`), so a stale case has someone to ask.
2. **A hard cap per category.** Adding a case means retiring or merging one, so the set stays small enough to run often. This is an authoring rule and the loader does not enforce it.
3. **A candidate should be admitted only if it adds a distinct failure.** If it would fail the same check for the same reason as an existing case, it becomes a note on that case.
4. **New cases come from real failures**: support corrections and questions the application got wrong, not an attempt to cover every page.
5. **The loader flags rot.** A missing gold chunk is a hard error. A changed document is a warning. Stamp cases with `confirmed_hash` using `java -cp eval/target/eval.jar eval.StampCaseHashes [--config team-config.yaml] `.
6. **Adding a case is data only**: one YAML entry and no code.

## 9. Prose-based applications: ask the platform team

If your application answers in prose instead of structured claims, ask the platform team to add a support for such apps.

**Limit.** Even once built, this only helps question-answering applications. Extraction or classification needs different checks (see the fit table at the top of this document).

## Checklist for your first run

- [ ] The endpoint returns the contract for an answer and for a refusal, from an instance started for testing.
- [ ] Every cited chunk ID exists in the knowledge base the harness loads.
- [ ] 10 cases: 4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise, each fully answerable or fully out-of-scope.
- [ ] `LLM_API_KEY` is set, and `judgeModel` is stronger than your application's model.
- [ ] `out-of-scope` is in `categories`.
- [ ] The first report is read, the case mistakes fixed, and a report you trust promoted to `baseline.json`.
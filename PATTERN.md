# How To: evaluating an AI Hub application

## Does this fit your application?

| Application shape | What your endpoint returns | Checks (`appType`) | Your first ~10 cases | Status |
|---|---|---|---|---|
| Answers questions from documents, with citations | Claims with citations (section 1) | `cited` | 4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise | Built |
| Generates text without sources | Claims without citations | `uncited`, `addChecks: [Relevance]` | Expected facts the output must contain, plus out-of-scope requests it must refuse | Built (Refusal, Coverage, Relevance); needs a config update to set `appType`/`addChecks` |
| Answers questions from documents, in prose | Prose plus sources, through the prose adapter (section 10) | `cited` once the adapter exists; until then, wrap the app so it returns claims | As the first row | Adapter designed, not built |
| Extracts fields or classifies | Not a fit: it needs field-level exact match or label accuracy, not claims | None | None | Not supported, talk to the platform team |

- Find your row, then continue below.
- The first row is the only fully built path. The others need a wrapper, a config choice, or the platform team.

## What you get

One command runs your application against a set of test cases and prints a pass/fail report. It exits non-zero when the application is not good enough to ship:

```
java -jar eval/target/eval.jar --config my-team/eval.yaml
```

The harness does not care what your application is built on. It only needs the contract in section 1. It doesn't just do a string matching: it checks whether each fact is covered, whether each claim is supported by the source it cites, whether the application refuses what it should not answer, and whether a change made a previously passing case fail.

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

If your application returns free text instead of structured claims, splitting it into cited claims is the main integration work — see the prose adapter design in section 10 (Designed for onboarding, not built in v1) before you build against this contract.

**Chunk IDs must be resolvable.** The harness must know the chunks your application cites, so that it can check that a citation exists and read what it says. Section 2 shows how it gets them.

**Fallback for a team that cannot expose an endpoint**: export answers to a JSON file and have the harness score the file. This is designed but **not built in v1** (see section 10). Today you need a live endpoint, even a thin wrapper around your application.

## 2. Configuration

Every setting is a key in a config file, and `--<key> <value>` overrides it for one run. The value is read as YAML, so it has the same type as in the file. An unknown key is an error.

A minimal team config, `my-team/eval.yaml`:

```yaml
endpoint: http://localhost:9000/answer      # your application, started for testing
passFloor: 0.90
judgeModel: anthropic/claude-opus-4.8       # model id your provider accepts (OpenRouter slug by default), stronger than your app's model
categories: [single-source, multi-source, false-premise, out-of-scope]
knowledgeBase:
  type: docs
  path: docs                                # markdown files, relative to this file
# cases: cases                              # default: a "cases" folder next to this file
# outputDir: caseResults
# baseline: caseResults/baseline.json
```

Run it from anywhere with `--config my-team/eval.yaml`. Every relative path in the file resolves against the file's folder. The judge needs `LLM_API_KEY` in the environment and calls OpenRouter by default. To use another OpenAI-compatible provider or an internal gateway, set `llmBaseUrl` in the config (or `--llmBaseUrl`, for example `https://llm-gateway.example.com/v1`), put that provider's key in `LLM_API_KEY`, and set `judgeModel` to a model id it accepts; the harness appends `/chat/completions`. **Untested with non-OpenRouter endpoints:** the request also carries OpenRouter-specific fields (`provider.require_parameters`, `reasoning.effort`) that a strict gateway may reject with a 400, so try one run against your gateway before relying on it. Your own application can use any model provider.

Useful overrides: `--case id1,id2` runs only those cases, `--appType uncited` picks the check set, `--skip-calibration` skips the judge calibration, `--debug` prints one `[debug]` line per config, case, check, assistant call and judge call, and `--baseline ""` switches a configured baseline off for one run. Keep `out-of-scope` in `categories`, because the exit rule depends on it (section 7).

This minimal config doesn't set `appType`, so it defaults to `cited` (five checks; Relevance is added with `addChecks`). Section 5 covers what each check does and the other app types.

### Where the harness gets the chunks

The harness needs the same chunk IDs and text your application has. `knowledgeBase.type` chooses the source:

- **`docs`** (built, the default): a folder of markdown files, chunked by heading. `browser-support.md` with a `## Browser Support` section gives the ID `browser-support#browser-support`. It splits at `#` to `###` headings, skips headings that appear inside code fences, and strips frontmatter; IDs are always `filename#heading-slug`. This works if your citations use the same `document#heading-slug` scheme — if your application chunks differently, its citations won't resolve, so use `http`/`manifest` (or your own `KnowledgeSource`) and export your own chunks instead.
- **`http`** and **`manifest`** (placeholders, selecting one exits with "not implemented yet"): the application serves, or exports at build time, a list of `{id, text}`. This is the better fit for an application in any language, because the harness sees exactly what the application indexed.
- **Your own source**: implement the one-method `KnowledgeSource` interface and add one line in `KnowledgeSources` (Java).

Whatever the source, the application and the harness must see the **same version** of the documents. A case that names gold chunks whose text changed since it was confirmed produces a warning that names the case, and it still runs — this only fires for cases that have `confirmed_hash` set (section 3).

## 3. Writing test cases

Start with about 10 cases across the four categories: 4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise. Growing the set beyond that is covered in section 9.

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

### The authoring rule

**Every case must be fully answerable or fully out-of-scope.** A question the docs answer only in part (setup is documented, cost is not) has no expected outcome in the contract, because there is no way to say "here is what I know, and I cannot answer the rest". That is a known gap, not a tested behaviour. The loader cannot enforce this rule, so it is a judgement you apply when you write the case.

Where real questions come from: support tickets, Slack threads, and corrections that support staff give to the assistant. Do not use customer data. Only use public or synthetic material.

### What the loader checks before it calls your application

The harness validates every case against your chunks first. A gold chunk that does not exist is a hard error that names the case and the chunk, and no calls are made. This is what stops a case from silently rotting when documents change. Your run fails at startup, not with a confusing result.

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

The principle is to use a deterministic check whenever the property is deterministic, and to use a model judge only where meaning has to be interpreted.

**Advisory** means reported and counted but never able to fail a case. Relevance is advisory because it is the most subjective judge and is not calibrated. Making it gating is a one-word change in the registration list, `eval/src/main/java/eval/checks/Checks.java`.

### Which checks suit which application

The fit table at the top of this document says which checks fit which application shape. For a customer-facing or high-stakes application such as the Implementation Assistant, run all of them: Groundedness and Refusal matter most, because a wrong answer that sounds right reaches a customer. An application that cannot cite cannot use Citation integrity, Groundedness or Source.

**App types and turning checks off.** Set `appType` in the config: `cited` (Refusal, Citation integrity, Coverage, Groundedness, Source), `uncited` (Refusal, Coverage; for an application that cannot cite) or `smoke` (Refusal, Citation integrity; no judge calls). The type is the least an application of that kind must run. The application can add with `addChecks: [Relevance]` but cannot remove; lowering a type's floor is a change in `Checks.java`, so it goes through the platform team. Without `appType`, `checks: [Refusal, Coverage]` (or `--checks "Refusal,Coverage"`) names an explicit list, and with neither all six run. Setting `appType` and `checks` together is an error. Names are as in the table, any case. Order is always the table's order. A check that needs another pulls it in (Source needs Coverage), and the console says `Checks added because another check needs them`. Startup fails with exit 2, before any call, on an unknown name or app type, an empty list, `addChecks` without `appType`, a list with no gating check, or `Refusal` off while the cases include out-of-scope ones. The calibration for a check that is off is skipped, the console prints `Checks off: ...`, and a baseline that ran a different set gets a warning.

### The judge, and why you should measure it

Coverage (the model part), Groundedness and Relevance use a judge model, set with `judgeModel` and called at temperature 0. **A judge you have not measured is not evidence**, so a run starts with a calibration:

- **7 trap pairs** guard Coverage: claims that a keyword filter would let through ("all Safari versions except 14") which the judge must reject.
- **20 hand-labeled pairs** measure Groundedness: 10 subtly unsupported, 5 plain supported and 5 hard-supported. The run fails if agreement is below 90% or if any unsupported pair is judged supported, because a false "supported" lets a wrong claim through silently.

The bundled pairs are written from the Usercentrics documents. For your own application, add pairs of your own from your own documents (`calibration.trapPairs` and `calibration.labeledSample` in your config); until you do, the calibration measures the judge on Usercentrics text, not yours. Treat the bundled sample as a smoke test rather than proof the judge is safe, and add pairs written by someone other than the harness's author, keeping some held out.

Coverage-by-judge and Relevance are otherwise **uncalibrated** in v1, and Coverage is the first one to add.

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
- **Cost and time** are measured: judge calls, tokens and estimated cost per check (from `judgePricing` in the config), and wall-clock time. The assistant's tokens are not visible over HTTP, so only its calls and time are reported.
- **Provenance** in the JSON report says what the run was measured with: the judge model, a hash of the judge prompts, the harness git commit (`-dirty` if the tree had uncommitted changes), and an optional `assistantVersion` label you set in the config. Set `assistantVersion` whenever you change the application, because the harness cannot see inside it. Without these, a regression against the baseline cannot be tied to a change.
- The JSON report has the full detail: the question, the expected facts, what the assistant returned, and every check with its reason. When a failure is unclear, read the JSON, or rerun the single case with `--case <id> --debug`.

To find out whether a failure is the application or the case, look at the claims the application returned. If it cited the wrong chunk or none, it is the application. If its answer is right and the check says "not covered", suspect the case, and read the fact and gold chunk again. The first run mostly exposes case mistakes, not application bugs — fix the case first, then re-run before concluding the application is at fault.

## 7. Thresholds, baselines and the exit code

The run exits **0** when everything passes, **1** when it fails, and **2** for a setup error (a bad config, a case that names a missing chunk, an unreadable baseline, no API key). It exits 1 when any of these is true:

1. **The pass rate is below `passFloor`** (0.90 in this repo, which allows 2 failing cases out of 28). In v1 the floor is just a config value. The design for other applications (section 10) lets departments raise it and never lower it below a platform minimum.
2. **Any out-of-scope case fails**, whatever the overall rate. A confident answer to a question the docs cannot answer is the headline risk, so it is never averaged away.
3. **A regression against the baseline**: a case that passed in the baseline and fails now, even when the overall rate is above the floor.
4. **The judge fails calibration** (section 5).

In CI, the exit code is what blocks a merge, floor included. Until your application reaches its tier floor, set `passFloor` in your CI config to your current baseline pass rate and raise it as the application improves (a ratchet: it only goes up). The tier floor is the release bar.

### The baseline

A **baseline** is a previous run's report that later runs are compared against. Normally it is a run you trust. In this repo it is the latest stub run (19 of 28): a reference for change, not a known-good run. Every run writes a timestamped JSON report to `caseResults/`. To promote one:

```
cp caseResults/<run id>.json caseResults/baseline.json
```

In this repo `eval/config.yaml` sets `baseline: caseResults/baseline.json`, so every run compares against it, and `--baseline ""` switches that off for one run. In your own config, set `baseline:` the same way; without it the run says so and goes ahead with no regression check.

- A case that failed in the baseline and passes now is listed as `improved since baseline`, which is a hint to promote a newer baseline. It never changes the exit code.
- A suspected regression is **re-run once** and only counts if it fails twice, and the report shows which cases needed a re-run. Temperature 0 does not make runs identical, and a case near the edge can pass on one run and fail on the next. The re-run count is a free measure of how flaky your suite is.
- A baseline that is missing, unreadable, or shares no case with this run exits 2. It never reads as "no regressions".

Once you trust a report — the case mistakes fixed, the failures understood — promote it as your baseline with the command above.

## 8. Worked example: the Implementation Assistant

This is what a mature set looks like after the general recipe above. It is not the instructions.

The application is a thin assistant that answers questions about the Usercentrics Web CMP from five public documentation pages (Browser Support, A/B Test, Geolocation Rules, Google Consent Mode, TCF 2.2). The stub uses BM25 search to pick the top 3 chunks and one model call to write cited claims.

The set has 28 cases: 8 single-source, 6 multi-source, 6 false-premise, 5 out-of-scope (2 unrelated and 3 plausible-nonexistent) and 3 edge cases (exact-value precision, a two-part question, and a light paraphrase). Every case names its gold chunks. What the set deliberately leaves out is in `scope.md`.

The latest full run (2026-09-25, run 20260925-220009, `caseResults/baseline.json`) passed 19 of 28 and exited 1 (67.9%):

| Category | Passed |
|---|---|
| single-source | 7 of 8 |
| out-of-scope | 5 of 5 |
| false-premise | 5 of 6 |
| edge-case | 2 of 3 |
| multi-source | 0 of 6 |

The baseline run (with calibration) used 98 judge calls, cost about $0.21 and took about 350 seconds. The harness did its job on a deliberately thin assistant. Reading the failures showed:

- **7 failures come with a retrieval gap.** Each is a question about two topics, where the stub's top 3 chunks all came from one topic. The model then refused, or answered only half. The harness cannot see this itself, because it only sees claims and citations, never the chunks the application retrieved. Replaying the search against the gold chunks showed the gap. It is not the whole cause: in an earlier experiment, when the score was 20 of 28, raising top-k from 3 to 6 made three cases pass (two multi-source, one edge) and three false-premise cases fail with over-refusals, and the score stayed 20 of 28. Regressions and improvements at once are what the baseline comparison is for. Telling a retrieval miss from a model miss needs an optional `retrieved` field in the contract.
- **1 failure is a model miss** (`fp-tcf-gettcdata`). The right chunk was retrieved first, and the model still refused. With a modified prompt in a later experiment it answered, but added an invented detail ("deprecated from 2.0"), and Groundedness and Coverage both failed it. That is the failure the brief describes: a wrong answer that sounds right, caught by a check that is not a string match.
- **1 failure is an over-refusal I have not diagnosed** (`ss-tcf-cmp-version`, a single-document question). It passed when run alone and was refused in the full runs after it, so it is also the flakiness example in `SCALE-PLAN.md` section 5.

The stub's failures are kept as evidence and not tuned away.

## 9. Growing the set without a maintenance burden

1. **Every case records where it came from** (`source`, `owner`, `added`), so a stale case has someone to ask.
2. **A hard cap per category.** Adding a case means retiring or merging one, so the set stays small enough to run often. This is an authoring rule and the loader does not enforce it.
3. **A candidate is admitted only if it adds a distinct failure.** If it would fail the same check for the same reason as an existing case, it becomes a note on that case.
4. **New cases come from real failures**: support corrections and questions the application got wrong, not an attempt to cover every page.
5. **The loader flags rot.** A missing gold chunk is a hard error. A changed document is a warning. Stamp cases with `confirmed_hash` using `eval.StampCaseHashes`.
6. **Adding a case is data only**: one YAML entry and no code.

## 10. Designed for onboarding, not built in v1

These are described here and in `SCALE-PLAN.md`, and none of them exists as code:

- **Risk tiers and a sign-off rule for removing a check.** (The `checks` list itself is built; the rule around it is not.) The platform defines 2 or 3 tiers, each with a pass floor and mandatory checks. Every application starts in the top tier. Lowering a tier or removing a check needs a written reason and platform sign-off, and a change of audience (internal to customer-facing) triggers a re-review. Departments can only raise the floor. There is no per-release approval queue.
- **Severity-tiered pass/fail** (critical, error, warning), a better rule than "every gating check must pass".
- **The `http` and `manifest` knowledge sources**, **the replay-file fallback**, **majority-of-N runs** and **scheduled runs**.
- **Cases for partially answerable and under-specified questions**, which need a contract field for what the application could not answer.

### The prose adapter

For an application that produces free text, the application would return `{"answer": "<prose>", "sources": ["<chunk id>", ...]}` and a splitter would turn the prose into claims. Today the application must return claims itself.

- **Where it plugs in.** `eval.Assistant` is a one-method interface (`Answer ask(String question, String runId)`), and `AssistantClient` implements it. A `ProseAssistantClient` would implement the same interface. It calls the application, sends the prose and the sources to the splitter (one LLM call), which returns atomic claims with citations drawn only from `sources`, and returns an `Answer`. No check changes.
- **Refusal.** The splitter can detect it, or the application keeps a `refused` flag. The flag is cheaper and preferred.
- **How the splitter can go wrong:**

| Splitter mistake | Effect | Severity |
|---|---|---|
| Drops a claim | An invented statement never reaches Groundedness: the hallucination is hidden | Worst: a silent pass |
| Wrong citation attribution | Groundedness fails a correct claim | Fails safe |
| Merges or over-splits claims | Coverage and Groundedness noise | Minor |

- **Calibration before use.** Hand-label about 20 prose answers with their correct claim lists. Measure completeness (every assertion in the prose appears as a claim, gating) and attribution accuracy. An optional safeguard is one extra judge call per answer, asking whether the prose asserts anything the claims do not cover.
- **Cost.** One splitter call per case, plus the optional safeguard. That is small next to Groundedness, which makes one call per claim per cited chunk.
- **Limit.** It does not help applications that are not question answering (extraction, classification). They need different checks (see the fit table at the top of this document).
- **Why it was cut from v1.** The splitter is itself an LLM judgement that needs its own calibration, and shipping it uncalibrated would contradict "a judge you have not measured is not evidence".

## Checklist for your first run

- [ ] The endpoint returns the contract for an answer and for a refusal, from an instance started for testing.
- [ ] Every cited chunk ID exists in the knowledge base the harness loads.
- [ ] 10 cases: 4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise, each fully answerable or fully out-of-scope.
- [ ] `LLM_API_KEY` is set, and `judgeModel` is stronger than your application's model.
- [ ] `out-of-scope` is in `categories`.
- [ ] The first report is read, the case mistakes fixed, and a report you trust promoted to `baseline.json`.
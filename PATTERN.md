# Pattern: evaluating an AI Hub application

For an AI Enablement Engineer in another department who has never seen this code and has about one hour. The goal is a first evaluation report for your own application. Do the quickstart first, then read sections 1 to 5 and follow the one-hour path in section 6. Section 8 shows a mature example, and it is deliberately last.

Terms in **bold** are defined in `CONTEXT.md`. Why the harness works this way is logged in `grilling-decisions.md`; you do not need it to use this document.

## Quickstart (10 minutes)

The shortest path to a first report. Everything else in this document is reference.

**1. Your application answers with claims.** `POST <endpoint>` with `{"question": "..."}` returns HTTP 200 and:

```json
{"refused": false,
 "claims": [{"claim": "bundle.js supports Safari starting from version 14",
             "citations": ["browser-support#browser-support"]}]}
```

A refusal is `{"refused": true, "claims": []}`. A citation is a chunk ID, `<document>#<heading-slug>`, from the documents your application answers from.

**2. A config**, `my-team/eval.yaml`, next to a `docs/` folder of your markdown files and a `cases/` folder:

```yaml
endpoint: http://localhost:9000/answer
categories: [single-source, out-of-scope]
knowledgeBase:
  type: docs
  path: docs
```

**3. One case**, `my-team/cases/first.yaml`:

```yaml
- id: ss-safari-bundle
  question: "Which Safari version does bundle.js support?"
  category: single-source
  expected_behavior: answer
  facts:
    - fact: "bundle.js supports Safari starting from version 14"
      chunks: [browser-support#browser-support]
```

**4. Run it** (with `OPENROUTER_API_KEY` set, from the repo root, after `mvn -q -DskipTests package`):

```
java -jar eval/target/eval.jar --config my-team/eval.yaml
```

**5. Read the exit code.** `0`: every case passed. `1`: the run failed, so read the block printed for each failing case, which names the check and the reason. `2`: a setup problem (bad config, a case that names a chunk that does not exist, a missing API key), and the message says what to fix. Add `--skip-calibration` while you iterate, because the judge calibration runs first on every run.

Then read sections 1 to 4 for the contract and the checks, and section 6 for the one-hour plan.

## What you get

One command runs your application against a set of test cases and prints a pass/fail report. It exits non-zero when the application is not good enough to ship:

```
java -jar eval/target/eval.jar --config my-team/eval.yaml
```

The harness does not care what your application is built on. It only needs the contract in section 1. What it checks is not string matching: it checks whether each fact is covered, whether each claim is supported by the source it cites, whether the application refuses what it should not answer, and whether a change made a previously passing case fail.

## 1. The contract

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

- **There is no free-text answer field.** Everything the application asserts is a **claim**: one atomic factual statement plus the citations that support it. If your application produces prose today, your endpoint has to split it into claims. This is the main integration cost, and it is what makes every assertion checkable.
- A **citation** is a chunk ID: the document name plus the section heading (`consent-mode#default-consent-states`). IDs use the heading, never a position number, so they survive re-chunking. Duplicate headings in one document get `-1`, `-2` suffixes.
- A refusal is `{"refused": true, "claims": []}`. `refused: true` together with claims is a contract violation and fails the case.
- Any status other than 200, or a body that is not this shape, fails that case with the error text. A slow call fails after 60 seconds.
- The harness sends an `X-Eval-Run: <run id>` header on every request so your gateway can exclude eval traffic from analytics.

**Point the harness at an instance started for testing, never at live production**. Production would mix eval traffic into real usage and cost figures, may have side effects (logging, connectors that write), can change in the middle of a run, and may not allow temperature 0. Use a candidate build for CI, or a dedicated instance with production's exact prompt, model and config for drift checks. The harness only knows a URL, so this is a deployment choice, not a code change.

Before it runs any case, the harness sends a GET to the endpoint. Any HTTP response counts as reachable, and a failed connection stops the run with a message.

**Designed, not built: a prose adapter.** For an application that produces free text, the harness would accept `{"answer": "...", "sources": [...]}` and use the judge to split the answer into claims before running the checks. The trade-off is an extra judge step that itself needs calibrating, because a wrong split changes what every later check sees. Today the application must return claims itself.

**Fallback for a team that cannot expose an endpoint**: export answers to a JSON file and have the harness score the file. This is designed but **not built in v1**. Today you need a live endpoint, even a thin wrapper around your application.

**Chunk IDs must be resolvable.** The harness must know the chunks your application cites, so that it can check that a citation exists and read what it says. Section 5 shows how it gets them.

## 2. Writing test cases

A case is a YAML entry. Cases live in `*.yaml` files in one folder, with any number of cases per file:

```yaml
- id: ss-safari-bundle
  question: "Which Safari version does bundle.js support?"
  category: single-source
  expected_behavior: answer          # answer | refuse, set per case
  facts:
    - fact: "bundle.js supports Safari starting from version 14"
      chunks: [browser-support#browser-support]   # gold chunks: citing any ONE is enough
      keywords: ["Safari", "14"]                  # optional, see below
  source: authored                   # or e.g. support-ticket-1234
  owner: platform                    # who to ask when the case looks stale
  added: "2026-09-24"
```

- **`facts`** are plain-language key points the answer must contain. They are not tied to a sentence count or wording, because the application may split one fact across several claims.
- **`chunks`** are the **gold chunks**: chunks a human has verified support the fact. They are alternatives, so citing any one of them is enough. Citing another chunk is not automatically wrong. It is just not pre-verified. Gold chunks are used by Coverage and Source, not to skip Groundedness.
- **`keywords`** are optional tokens that must all appear in a claim before it can count as covering the fact. Use them for a checkable token such as a version number. They are a cheap filter that catches near-misses ("Safari 13" for "Safari 14") without a model call. They are never sufficient on their own, because they cannot detect negation ("all Safari versions except 14"), so a judge confirms every keyword hit.
- Leave `keywords` out when there is no single token to check. A judge then decides which claims cover the fact.
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

## 3. The menu of checks

Each case runs the enabled checks (all six unless `checks` says otherwise, section 5), in this order. A case passes only if every **gating** check passes.

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

- **Customer-facing or high-stakes answers** (the Implementation Assistant): all of them. Groundedness and Refusal matter most, because a wrong answer that sounds right reaches a customer.
- **An internal summariser** with no citations to check: Coverage and Relevance are the meaningful ones.
- **An application not grounded in documents**: Refusal and Coverage. It cannot use Citation integrity, Groundedness or Source.

**App types and turning checks off.** Set `appType` in the config: `cited` (Refusal, Citation integrity, Coverage, Groundedness, Source), `uncited` (Refusal, Coverage; for an application that cannot cite) or `smoke` (Refusal, Citation integrity; no judge calls). The type is the least an application of that kind must run. The application can add with `addChecks: [Relevance]` but cannot remove; lowering a type's floor is a change in `Checks.java`, so it goes through the platform team. Without `appType`, `checks: [Refusal, Coverage]` (or `--checks "Refusal,Coverage"`) names an explicit list, and with neither all six run. Setting `appType` and `checks` together is an error. Names are as in the table, any case. Order is always the table's order. A check that needs another pulls it in (Source needs Coverage), and the console says `Checks added because another check needs them`. Startup fails with exit 2, before any call, on an unknown name or app type, an empty list, `addChecks` without `appType`, a list with no gating check, or `Refusal` off while the cases include out-of-scope ones. The calibration for a check that is off is skipped, the console prints `Checks off: ...`, and a baseline that ran a different set gets a warning.

### The judge, and why you should measure it

Coverage (the model part), Groundedness and Relevance use a judge model, set with `judgeModel` and called at temperature 0. It should be stronger than the model your application uses. **A judge you have not measured is not evidence**, so a run starts with a calibration:

- **7 trap pairs** guard Coverage: claims that a keyword filter would let through ("all Safari versions except 14") which the judge must reject.
- **20 hand-labeled pairs** measure Groundedness: 10 subtly unsupported, 5 plain supported and 5 hard-supported. The run fails if agreement is below 90% or if any unsupported pair is judged supported, because a false "supported" lets a wrong claim through silently.

The bundled pairs are written from the Usercentrics documents. For your own application, add pairs of your own from your own documents (`calibration.trapPairs` and `calibration.labeledSample` in your config). Until you do, the calibration measures the judge on Usercentrics text, not on yours.

Two caveats about the bundled sample:

- **It is a smoke test, not a measurement.** Getting 0 false-supported out of 10 unsupported pairs still allows a true false-supported rate of up to about 30% at 95% confidence (the rule of three: 3 divided by 10). It catches a judge that is badly wrong, and it cannot show that the judge is safe.
- **The author wrote the pairs.** The bundled pairs were written by the person who built the harness, so they may share that person's blind spots. Add pairs written by someone else, and keep some held out that you never tune the judge prompt against.

Coverage-by-judge and Relevance are otherwise **uncalibrated** in v1, and Coverage is the first one to add.

## 4. Thresholds, baselines and the exit code

The run exits **0** when everything passes, **1** when it fails, and **2** for a setup error (a bad config, a case that names a missing chunk, an unreadable baseline, no API key). It exits 1 when any of these is true:

1. **The pass rate is below `passFloor`** (0.90 in this repo, which allows 2 failing cases out of 28). In v1 the floor is just a config value. The design for other applications (section 10) lets departments raise it and never lower it below a platform minimum.
2. **Any out-of-scope case fails**, whatever the overall rate. A confident answer to a question the docs cannot answer is the headline risk, so it is never averaged away.
3. **A regression against the baseline**: a case that passed in the baseline and fails now, even when the overall rate is above the floor.
4. **The judge fails calibration** (section 3).

### The baseline

A **baseline** is a previous report that you promote as the known-good reference. Every run writes a timestamped JSON report to `caseResults/`. To promote one:

```
cp caseResults/<run id>.json caseResults/baseline.json
```

In this repo `eval/config.yaml` sets `baseline: caseResults/baseline.json`, so every run compares against it, and `--baseline ""` switches that off for one run. In your own config, set `baseline:` the same way; without it the run says so and goes ahead with no regression check.

- A case that failed in the baseline and passes now is listed as `improved since baseline`, which is a hint to promote a newer baseline. It never changes the exit code.
- A suspected regression is **re-run once** and only counts if it fails twice, and the report shows which cases needed a re-run. Temperature 0 does not make runs identical, and a case near the edge can pass on one run and fail on the next. The re-run count is a free measure of how flaky your suite is.
- A baseline that is missing, unreadable, or shares no case with this run exits 2. It never reads as "no regressions".

## 5. Configuration

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
```

Run it from anywhere with `--config my-team/eval.yaml`. Every relative path in the file resolves against the file's folder. The judge needs `OPENROUTER_API_KEY` in the environment and calls OpenRouter by default; set the optional `LLM_BASE_URL` (for example `https://llm-gateway.example.com/v1`) to route it through an internal OpenAI-compatible gateway, and the harness appends `/chat/completions`. Your own application can use any model provider.

Useful overrides: `--case id1,id2` runs only those cases, `--checks "Refusal,Coverage"` runs only those checks, `--skip-calibration` skips the judge calibration (the calibration was 27 of the 98 judge calls in the baseline run), `--debug` prints one `[debug]` line per config, case, check, assistant call and judge call, and `--baseline ""` switches a configured baseline off for one run. Keep `out-of-scope` in `categories`, because the exit rule depends on it.

### Where the harness gets the chunks

The harness needs the same chunk IDs and text your application has. `knowledgeBase.type` chooses the source:

- **`docs`** (built, the default): a folder of markdown files, chunked by heading. `browser-support.md` with a `## Browser Support` section gives the ID `browser-support#browser-support`. This works if your citations use the same `document#heading-slug` scheme.
- **`http`** and **`manifest`** (placeholders, selecting one exits with "not implemented yet"): the application serves, or exports at build time, a list of `{id, text}`. This is the better fit for an application in any language, because the harness sees exactly what the application indexed.
- **Your own source**: implement the one-method `KnowledgeSource` interface and add one line in `KnowledgeSources` (Java).

Whatever the source, the application and the harness must see the **same version** of the documents. A case that names gold chunks whose text changed since it was confirmed produces a warning that names the case, and it still runs.

## 6. The one-hour path

The goal is a first honest report, not a complete set. Start with about 10 cases: **4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise**.

| Minutes | Step |
|---|---|
| 15 | Expose the endpoint from a test instance and return the contract in section 1. This takes 15 minutes only if the application already produces structured, cited output; if it produces prose, splitting it into cited claims is the main integration work and takes longer. Make a config file like the one in section 5. Check that `curl` returns claims with citations and that a refusal is `{"refused": true, "claims": []}`. |
| 25 | Write the 10 cases. Pick real questions. For every fact, look up the chunk in your documents and confirm it says the fact. Put a `keywords` list on the facts with a checkable token. |
| 10 | Run the harness and read the report (section 7). |
| 10 | Fix mistakes in the **cases**, not the application. The first run mostly exposes case errors: a wrong gold chunk, a fact that two chunks state, a question that is only partly answerable. |

Then commit the report you trust as the baseline. The starter set is where you begin, not where you stop. Grow it by the rules in section 9.

## 7. Reading a report

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

To find out whether a failure is the application or the case, look at the claims the application returned. If it cited the wrong chunk or none, it is the application. If its answer is right and the check says "not covered", suspect the case, and read the fact and gold chunk again.

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

## Checklist for your first run

- [ ] The endpoint returns the contract for an answer and for a refusal, from an instance started for testing.
- [ ] Every cited chunk ID exists in the knowledge base the harness loads.
- [ ] 10 cases: 4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise, each fully answerable or fully out-of-scope.
- [ ] `OPENROUTER_API_KEY` is set, and `judgeModel` is stronger than your application's model.
- [ ] `out-of-scope` is in `categories`.
- [ ] The first report is read, the case mistakes fixed, and a report you trust promoted to `baseline.json`.

# Build Order — Eval Harness

Sliced from `usercentrics-eval-harness-plan.md` (the spec), with `grilling-decisions.md` as the ADR layer (numbered decisions win over any older plan text) and `CONTEXT.md` for vocabulary. There are no `docs/adr/` files, so decisions are cited as **D<n>**.

**Status (2026-09-25):** units 1-22 and 24-26 are built or written (24-26 are `PATTERN.md`, `SCALE-PLAN.md` and `README.md`). Unit 23, the live-change rehearsal, is not done. `--case` (D52) is an addition to unit 4 and has no unit of its own.

Each unit is an observable outcome you can demo alone. Units marked **Cut n** map to the cut order in D30 (1 = cut first). Units marked **Never cut** are the floor that answers the brief's stated risk.

**Cross-cutting rules, applied as criteria in each relevant unit, not built as units:** temperature 0 for the assistant and judges (D14); assistant and judge models are separate config values, judge stronger (D15); `eval/` never imports `Assistant` and talks to the app only over HTTP (D28).

**Checkpoints:** after unit 8 you have a working harness that catches hallucinated answers on out-of-scope cases. After unit 16 the safety-critical checks and their calibration exist. Everything after 16 is refinement, the eval set, and the deliverable docs.

---

## 1. The five docs are chunked into IDs that survive re-chunking

**Depends on:** none
**Refs:** plan Step 1; D6
**Never cut**

Acceptance criteria:
- `docs/` holds the five pages (Browser Support, A/B Test, Geolocation Rules, Google Consent Mode, TCF 2.2), each tagged with its source URL.
- Each **chunk** ID is document name plus section heading (`consent-mode#default-consent-states`), never a position number.
- Adding, removing, or reordering a section leaves every other chunk's ID unchanged.
- Printing all chunk IDs shows one per section across all five docs.
- When a heading appears more than once in a document, each occurrence gets a numeric suffix counting from 1 in order (`doc#overview-1`, `doc#overview-2`); a heading that appears once has no suffix (D33).

## 2. The stub answers a covered question over HTTP with cited claims

**Depends on:** 1
**Refs:** plan Step 2; D1, D28
**Never cut**

Acceptance criteria:
- `POST /answer` with `{question}` returns `{refused: false, claims: [{claim, citations}]}`, with the stub started on its own (`StubServer`).
- The response has **no free-text answer field**; everything asserted is a claim (D1).
- BM25 retrieval picks the top 2–3 chunks, and each claim's `citations` are chunk IDs (§Step 2).
- The generation model is read from config and runs at temperature 0, separate from the judge model (D14, D15).
- A question like "Which browsers does the CMP support?" returns claims citing `browser-support#…` chunks.

## 3. The stub refuses a question the docs don't cover

**Depends on:** 2
**Refs:** plan Step 2; D1
**Never cut**

Acceptance criteria:
- For an **out-of-scope** question (e.g. pricing), the response is `refused: true` with zero claims.
- A response never has `refused: true` together with claims (D1, D25).
- The stub is prompted to refuse rather than answer from outside the retrieved chunks. There is no retrieval score cutoff: the top chunks always go to the model and the prompt alone decides (D36).

## 4. One command runs a case file against the endpoint and reports pass/fail per case

**Depends on:** 3
**Refs:** plan Step 4 (opening, pass rule); D2, D12, D27, D28
**Never cut**

Acceptance criteria:
- `Harness.main` loads cases from `eval/cases/*.yaml` (question, category, `expected_behavior`, expected facts) and calls the assistant through `AssistantClient` over HTTP, with an `X-Eval-Run` header on each request (D32).
- If the endpoint is unreachable, the run exits immediately with a message saying how to start the stub, not a raw connection error (D29).
- Checks run from a single registration list; each check has a gating flag; a case passes only if every gating check passes (D12, D27).
- The first check in the list is Refusal. A case with `expected_behavior: refuse` and a `refused: true` response, or `answer` and `refused: false`, passes.
- A summary table prints to the terminal, and full per-case detail is written to a timestamped JSON file in `caseResults/`.
- `--endpoint <url>` points the harness at any other app. It is one of the `--<setting> <value>` overrides of a config key (D47).

## 5. An out-of-scope question that gets a confident answer fails the case

**Depends on:** 4
**Refs:** plan Step 4 (Refusal); D25
**Never cut**

Acceptance criteria:
- Expected refuse, response `refused: false` with claims: the case fails, and the reason says the assistant answered where it should have refused (the hallucination case).
- Expected answer, response `refused: true`: the case fails as over-refusal.
- `refused: true` with claims present: the case fails as a contract violation.
- Refusal makes no LLM call.

## 6. The run exits non-zero when the pass rate is below the floor

**Depends on:** 4
**Refs:** plan Step 4 (Exit code); D13
**Never cut**

Acceptance criteria:
- The pass floor is read from `eval/config.yaml` and is set to 90% (D34).
- Overall pass rate below the floor gives a non-zero exit code, and at or above gives zero (subject to units 7 and 18).
- The summary shows the floor and the rate, so the reason for a non-zero exit is readable.
- With 28 cases, 26 passing (92.9%) meets the floor and 25 (89.3%) does not, so at most 2 failing cases are tolerated.

## 7. A failing out-of-scope case fails the whole run even when the pass rate is high

**Depends on:** 5, 6
**Refs:** plan Step 4 (Exit code); D13
**Never cut**

Acceptance criteria:
- With the overall rate above the floor and one out-of-scope case failing, the exit code is non-zero.
- The output names the failing out-of-scope case(s) as the reason.
- Failures in other categories don't trigger this rule on their own.

## 8. A case that cites a gold chunk that doesn't exist stops the run before any assistant call

**Depends on:** 1, 4
**Refs:** plan Step 3 (growth rule 4); D5, D6
**Never cut**

Acceptance criteria:
- `KnowledgeBase` loads `docs/` through `Chunker` and the case loader checks every fact's `chunks` against it.
- A missing chunk ID is a hard error naming the case and the chunk, and no calls are made to the assistant.
- A fact's `chunks` list is read as alternatives (any one is enough), per **gold chunk** in `CONTEXT.md`.

## 9. A claim with no citation, or a fabricated one, fails Citation integrity

**Depends on:** 8
**Refs:** plan Step 4 (Citation integrity); D26
**Never cut**

Acceptance criteria:
- Every claim needs at least one citation; a claim with none fails the check.
- Every cited chunk ID must exist in the knowledge base; a nonexistent ID fails with "fabricated citation: <id>".
- The check is deterministic (no LLM call) and runs first on every claim, so later checks only see valid IDs (D26).
- The failing claim and reason appear in the per-case detail.

## 10. A fact with keywords is not covered when no claim contains all of them

**Depends on:** 4
**Refs:** plan Step 4 (Coverage); D24
**Never cut**

Acceptance criteria:
- An **expected fact** may list `keywords`; a claim is a candidate only if it contains **all** of them (case-insensitive).
- With no candidate, the fact is not covered and the case fails Coverage, with **zero LLM calls** for that fact.
- Example: the Safari-version fact fails when the assistant says "Safari 13" and no claim contains both `Safari` and `14`.

## 11. A keyword candidate is confirmed or rejected by the judge

**Depends on:** 10
**Refs:** plan Step 4 (Coverage, Models); D14, D15, D24
**Never cut** (the ~5-pair trap test run is **Cut 4**)

Acceptance criteria:
- Each candidate claim goes to the main judge model with one narrow question: do the fact and the claim agree?
- A keyword hit never passes on its own: "All Safari versions except 14" is not covered for the fact "Safari 14 or later is supported".
- The judge model is a separate config value from the assistant model, and is called at temperature 0 and low effort.
- Any confirmed candidate makes it a **covering claim**, recorded for later checks.
- `calibration/trap-pairs.yaml` holds ~5 negation traps (all except 14, Safari 13 not 14, Safari 14 is not supported, plus 2 correct paraphrases). Running them reports any miss (**Cut 4**: keep the file as documentation and skip the run).

## 12. A fact without keywords is covered when the judge names a claim that states it

**Depends on:** 11
**Refs:** plan Step 4 (Coverage); D24
**Never cut**

Acceptance criteria:
- One judge call receives the fact plus the numbered claims and returns the covering claim indices.
- An empty list means the fact is not covered and the case fails Coverage.
- The returned claims become the covering claims, same as unit 11.

## 13. A claim citing a gold chunk of the fact it covers is grounded with no LLM call

**Depends on:** 9, 11, 12
**Refs:** plan Step 4 (Groundedness); D5, D10
**Never cut**

Acceptance criteria:
- For a covering claim, if any cited chunk is in the fact's gold `chunks`, Groundedness passes with zero LLM calls.
- The per-case detail marks the claim as "grounded (gold chunk)".
- A claim citing a real chunk outside the gold list is **not** failed here; it is passed on to unit 14.

## 14. A claim that doesn't cite a gold chunk is decided by the Groundedness judge

**Depends on:** 13
**Refs:** plan Step 4 (Groundedness); D10, D15
**Never cut**

Acceptance criteria:
- One claim plus its cited chunk text go to the judge per call, with a narrow yes/no: does the chunk support the claim?
- Supported: the claim passes. Unsupported: Groundedness fails the case, with the claim and chunk shown in the detail.
- A gold-chunk mismatch alone never fails a case (D10).
- The check only ever receives chunk IDs that passed Citation integrity (unit 9).

## 15. The Groundedness judge is measured against 20 hand-labeled pairs

**Depends on:** 14
**Refs:** plan Step 4 (Judge calibration); D16
**Never cut**

Acceptance criteria:
- `calibration/labeled-sample.yaml` holds 20 claim/chunk pairs: 10 subtly unsupported, 5 plain supported, 5 hard-supported (paraphrase, split across sentences, equivalent numbers).
- Running the calibration reports overall agreement (target at least 90%) and the count of unsupported pairs the judge called "supported" (target 0 of 10).
- False-"unsupported" is counted and reported, with no target.
- `calibration/calibration-notes.md` records results and any judge-prompt change made to hit the targets.
- Only Groundedness is calibrated; Coverage-by-judge and Relevance are named as uncalibrated.

## 16. A multi-source answer that skips a required document fails Source

**Depends on:** 13
**Refs:** plan Step 4 (Source); D11
**Cut 5** (if cut, say multi-source coverage is enforced by gold-chunk matching only)

Acceptance criteria:
- The documents behind a covering claim's citations must match the documents of the fact's gold chunks; the document is derived from the chunk ID.
- A claim covering a Geolocation Rules fact that cites only `consent-mode#…` fails Source.
- The check is deterministic (no LLM call).

## 17. Relevance is judged and reported but never fails a case

**Depends on:** 11
**Refs:** plan Step 4 (Relevance, pass rule); D12
**Cut 6**

Acceptance criteria:
- Each claim goes to the judge with a narrow question: is it pertinent to the question, not true-but-off-topic padding?
- Relevance is an **advisory check**: its failures appear in the per-case detail and are counted in the summary, but never change a case's pass/fail.
- Making it gating means changing one flag in the registration list.

## 18. A previously passing case that now fails makes the run fail, even above the floor

**Depends on:** 6
**Refs:** plan Step 4 (Exit code); D13
**Never cut**

Acceptance criteria:
- The `baseline` setting (config key, or `--baseline <file>` for one run, D47) names a previous results JSON; a baseline is promoted by copying a report to `caseResults/baseline.json`. No path set is logged and the run goes ahead without a baseline; a path with no file behind it, or a file that is unusable, exits 2 (D46, D47).
- Any case that passed in the baseline and fails now is a **regression**, listed by name; the exit code is non-zero even if the overall rate is above the floor.
- Without a baseline, no regression check runs.
- Cases that failed in the baseline and pass now are listed as `improved since baseline` (a hint to promote a newer baseline); this never changes the exit code (D46).

## 19. A suspected regression is re-run once and only counts if it fails again

**Depends on:** 18
**Refs:** plan Step 4 (Non-determinism); D14
**Cut 3** (flips are reported, not retried)

Acceptance criteria:
- A case that would be flagged as a regression is run a second time.
- It counts as a regression only if it fails both attempts.
- The report shows which cases needed a re-run, and how many.

## 20. The report shows measured calls, tokens, cost, and wall-clock time

**Depends on:** 11
**Refs:** plan Step 4 (Cost and time); D17
**Cut 2** (reduced to call count and wall-clock time; cost example hand-calculated in the scale plan)

Acceptance criteria:
- `LlmClient` records calls and tokens by role (assistant, judge) and by check.
- The report prints total calls, tokens, estimated cost, and wall-clock time for the run.
- The scale plan's cost line is computed from one measured run, not the earlier "~3 judge calls" guess.

## 21. The full 28-case set runs and rolls up by category

**Depends on:** 5, 9, 12, 14
**Refs:** plan Step 3; D2, D3, D4, D7, D8, D18
**Never cut**

Acceptance criteria:
- `eval/cases/` holds 8 single-source, 6 multi-source, 5 out-of-scope, 6 false-premise, and 3 edge cases (exact-value precision, multi-ask, light paraphrase).
- **False-premise** cases have `expected_behavior: answer`, and their expected fact is the correction itself. **Out-of-scope** cases have `expected_behavior: refuse` and are tagged `unrelated` or `plausible-nonexistent` (about 2 and 3).
- Every case records `source`, `owner` and `added`.
- No case is partially answerable: each is fully answerable or fully out-of-scope (authoring rule, D7).
- Each expected fact lists its gold `chunks`, and the loader passes (unit 8).
- The summary shows pass rate overall, by category, and out-of-scope by subtype.
- The hard cap per category (D19) is an authoring rule only; the loader does not enforce it (D35).

## 22. A case whose source doc changed since it was confirmed produces a warning

**Depends on:** 8
**Refs:** plan Step 3 (growth rule 4); D21
**Cut 1**

Acceptance criteria:
- Each case records the content hash of the docs it was last confirmed against.
- A changed hash produces a warning naming the case; the case still runs.
- A missing gold chunk stays a hard error (unit 8), not a warning.

## 23. The three rehearsed live changes each work as advertised

**Depends on:** 21
**Refs:** plan Step 4 (Design for the live change); D27
**Never cut**

Acceptance criteria:
- Adding a new check needs one new class implementing `Check` plus one line in the registration list; `Harness` doesn't name it.
- Changing the pass floor or a gating flag is a one-value edit.
- Adding an eval case is a YAML entry only, with no code change.
- Each of the three has been done once, out loud, from a clean state.

## 24. Another engineer can follow `PATTERN.md` to their first report in about an hour

**Depends on:** 21
**Refs:** plan Step 5; D22, D23, D28, D31, D32
**Never cut**

Acceptance criteria:
- Covers, in this order: the contract (POST endpoint, claims JSON, replay-file fallback), writing cases (four categories, `expected_behavior`, the authoring rule), the menu of checks with which suits which app, thresholds and baselines, the one-hour path, and the worked example last.
- The one-hour path is a starter set of about 10 cases (4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise) with the 15 / 25 / 10 / 10 minute split.
- Says to test an instance started for testing, never live prod, and to send `X-Eval-Run` (D32).
- Marks the per-app `checks` list and risk tiers as onboarding design, not built in v1 (D23).
- States the growth rules and the hard cap.

## 25. The scale plan fits one page and answers each question the brief asks

**Depends on:** 20 (measured numbers) or a hand-calculated example if unit 20 is cut
**Refs:** plan Step 6; D22, D32
**Never cut**

Acceptance criteria:
- One page or five slides, covering: where it runs and when (local, CI, nightly, and the instance for each), who owns what, what "good enough to ship" means, cost and time, and what breaks first with what to build next.
- **Risk tier** rules stated: default top tier, downgrade needs a written reason and platform sign-off, departments can only raise the floor, no per-release approval queue.
- The cost line is built from a measured run (or the hand example if unit 20 was cut).

## 26. The README gets a stranger from clone to first result

**Depends on:** 4
**Refs:** plan Step 4; D29
**Never cut**

Acceptance criteria:
- Documents the two commands: start `StubServer`, then run `Harness`.
- Explains `--config` and the `--<setting> <value>` override for every config key, with `endpoint` and `baseline` as examples (D47).
- Reads in 2–3 minutes and points to `PATTERN.md` and `SCALE-PLAN.md`.

---

## Not units (design only in v1)

Kept out per the plan and D23, and only described in `PATTERN.md` and `SCALE-PLAN.md`: the per-app `checks` list and tier-mandatory-check enforcement, risk-tier configuration, nightly scheduling, majority-of-N runs, severity-tiered pass/fail, the `unanswered` field for partially answerable questions, under-specified questions, and the refusal-message-quality and cited-from-memory checks.

## Suggested pacing against the 5-hour budget

| Units | Plan budget line | Time |
| --- | --- | --- |
| 1–3 | Doc snapshot + thin assistant | 0:45 |
| 4–20 | Harness + checks | 1:45 |
| 21 | Evaluation set | 1:00 |
| 24, 26 | Pattern document, README | 0:45 |
| 23, 25 | Scale plan + rehearsing the live change | 0:45 |

If the harness block runs over, cut in the order given in D30: unit 22, then 20, then 19, then the trap-test run in 11, then 16, then 17.

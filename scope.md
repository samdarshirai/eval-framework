# Scope: what is in, what is out, and why

The brief asks for two different things, so this page has two halves:

- **Part A, the eval set** (deliverable 2: "what you deliberately did not cover, and how you would grow it"): which questions the cases do not test.
- **Part B, the build** (practical notes: "what you cut is part of your answer"): what I dropped from the harness to stay inside 5 hours.

Decision numbers (D#) point to `grilling-decisions.md`. Guiding rule from the brief: a small thing that works beats a large thing that does not.

---

# Part A: The eval set

## What the cases cover

Cases are written by hand from five real pages of the public Usercentrics docs (Browser Support, A/B Testing, Geolocation Rules, Google Consent Mode, TCF 2.2). No customer data. Each case names its gold chunk(s), so a case is checked against the source text, not against a remembered answer (D5, D6).

| Category | What it tests | Cases |
|---|---|---|
| Single-source | A fact from one chunk, including exact values (a version number) | 8 |
| Multi-source | An answer that needs two documents | 6 |
| False premise | A wrong assumption about a feature the docs cover; the assistant must correct it, not go along and not refuse (D3) | 6 |
| Out-of-scope | The docs cannot answer; the assistant must refuse. Two subtypes: `unrelated` (pricing) and `plausible-nonexistent` (sounds real, is not) (D4) | 5 |
| Edge cases | Exact-value precision, multi-ask, light paraphrase (D8) | 3 |

The set has 28 cases.

**How a case gets in.** It is admitted only if it adds a distinct failure. Otherwise it becomes a note on an existing case (D20).

## What the cases deliberately do not cover

| Not covered | Why |
|---|---|
| Partially answerable questions | A documented gap. Every case is fully answerable or fully out-of-scope, so the expected result is unambiguous (D7) |
| Under-specified questions | Same gap; testing them needs an `unanswered` outcome the contract does not have |
| Hard vocabulary mismatch ("cookie banner" vs "CMP", zero shared terms) | Tests BM25's known weakness, i.e. retrieval quality, not the harness (D8) |
| Refusal wording | Refusal is checked on behaviour (refused or not), not on how it is phrased |
| Multi-turn conversations, non-English questions, customer-specific setups | Not the brief's application, and no customer data is allowed |
| Adversarial and prompt-injection questions | A different threat model than answer correctness from trusted internal docs; named as awareness, not tested |
| Docs beyond the five pages (Account Interface, Embeddings, Events, Analytics, Smart Data Protector, ...) | None adds a distinct failure once the five chosen pages are in. Out-of-scope behaviour is tested by asking about pages that were never included |
| Cases for HyDE or re-ranking | Those are not built, so there is nothing to test (D8) |

## How the set grows without becoming a burden

- **A hard cap per category.** Adding a case means retiring or merging one (D19). It is an authoring rule, not enforced by the loader (D35).
- **Every case records** `source`, `owner` and `added`, so a stale case has someone to ask (D18).
- **New cases come from real failures** and from corrections that support gives, not from trying to cover every page.
- **Staleness is caught by the loader.** A gold chunk that no longer exists is a hard error; a doc that changed since the case was last confirmed is a warning (D21, D48).
- **Adding a case is data only**: one YAML entry, no code.

---

# Part B: The build

## In scope, built

| What | Why it is in |
|---|---|
| **One command** (`java -jar eval/target/eval.jar`) that evaluates the assistant over HTTP and exits non-zero on failure | Deliverable 1. The harness is the point (D28, D29) |
| **Thin stub assistant** (BM25 retrieval, claims-only answers) | The assistant is not the point; it only needs to give the harness something real to catch (D36, D37) |
| **Claims-only contract**: `{refused, claims:[{claim, citations}]}`, no free-text answer | Makes every claim checkable (D1) |
| **Refusal** (deterministic) | Out-of-scope behaviour is the main hallucination risk (D25) |
| **Citation integrity** (deterministic): every claim cites, every cited chunk exists | Catches fabricated citations at zero cost (D26) |
| **Coverage**: keyword filter, then an LLM judge confirms | Catches wrong values ("Safari 13" vs "14") cheaply and negation with the judge (D24) |
| **Groundedness** (LLM judge on every claim): does the cited chunk support the claim? | Deliverable 3, the check that is not a string match. Chosen first because "a wrong answer that sounds right reaches a customer" (D43, D53) |
| **28-case eval set** (8 / 6 / 5 / 6 / 3) with a per-category and per-subtype rollup | Deliverable 2, and the worked example for `PATTERN.md` (D2-D8, D48) |
| **Doc-hash warning**: a case whose gold chunks changed since it was confirmed warns by name and still runs | The staleness rule of the growth plan (D21, D48) |
| **Source** (deterministic, document-level) | Proves a multi-source answer used the required documents (D11) |
| **Judge calibration** inside the same command: 7 trap pairs plus 20 hand-labeled Groundedness pairs (measured: 20/20 agreement, 0 of 10 false-supported) | A judge you have not measured is not evidence (D16, D41, D43) |
| **Regression against a baseline** (`--baseline`): a case that passed there and fails now fails the run, after one re-run | Blocks a silent slide even when the pass rate stays above the floor; the re-run keeps flakes from crying wolf (D13, D14, D46) |
| **Exit-code rules**: pass floor 90%, and any failing out-of-scope case fails the run | Blocks a hallucination even when the pass rate is high (D13, D34) |
| **Relevance** (LLM judge, advisory): flags off-topic claims, never fails a case; uncalibrated | Reported and counted so padding is visible without a noisy judge gating releases (D12, D49) |
| **Measured cost and time**: judge calls and tokens per check, estimated cost from `judgePricing`, wall-clock time; the baseline run (28 cases with calibration) made 98 judge calls, cost $0.21 and took 350 s; the calibration is 27 of those calls and about $0.04 | The scale plan's cost line comes from a measured run, not a guess; assistant tokens are not visible over HTTP (D17, D28, D50) |
| **Reusable by other teams**: `--config`, a `--<setting> <value>` override for every config key (including `--case` to run selected cases), pluggable knowledge source | The "pattern for thirty more" (D38, D42, D52) |

## In scope, written

| What | Where |
|---|---|
| **Pattern document** for an engineer with one hour | `PATTERN.md` (deliverable 4) |
| **README**, clone to first result | `README.md` |

Cut order if time runs out (D30): cost detail, automatic re-run, trap-pair run, Source, Relevance. Never cut: claims-only contract, Citation integrity, Groundedness with calibration, Refusal, Coverage, the exit code, the eval set, the pattern document.

## Out of scope, designed but not built

Described in the pattern document and scale plan, not coded in v1 (D22, D54).

| What | Why not built |
|---|---|
| Risk tiers with a mandatory check set, and the sign-off rule for removing a check | Only needed with more than one app. The per-app `checks` list, `appType` and `addChecks` are built (D54); the tiers and the rule around them are not |
| Platform-owned `judgeModel` and minimum pass floor | Enforcement is a later platform decision (D42) |
| Nightly scheduled runs, majority-of-N runs | A scale-plan topic. Temperature 0 does not make runs identical (the scale plan, section 5); today the single re-run of a suspected regression is the only guard (D14) |
| Severity-tiered pass/fail (CRITICAL / ERROR / WARNING) | Better design, but new rules plus code; the gating/advisory flag is the small step toward it |
| `http` and `manifest` knowledge sources | Placeholders; the `docs` source covers the one app |

## Out of scope, considered and left out

| What | Why |
|---|---|
| **Retrieval metric (context recall)** | The harness sees only claims and citations, never the retrieved chunks. Needs an optional `retrieved` field in the contract. About 2-3 hours as its own PR (D44) |
| **Cited-from-memory check** | Same reason: needs the `retrieved` field that other apps would not have |
| **Fail-fast to save judge cost** | Skipping judge-backed checks after a deterministic failure saves judge calls per failing case (not measured), but a failing case would then show only its first failure. Deferred until the measured cost report shows it matters (D45) |
| Refusal-message-quality check | Behaviour is checked, wording is not |
| HyDE and re-ranking | Assistant retrieval improvements, not needed to prove the harness works |
| A separate model for the Coverage confirm step | The main judge is enough; one judge setting is simpler (D24) |
| promptfoo / DeepEval, Elasticsearch or a vector DB | A small runner I know line by line (one class per check, one registration list) fits the live-change requirement; BM25 in memory suffices for 5 pages |

## Known open items

The latest full run of the 28 cases (2026-09-25, `caseResults/baseline.json`, run 20260925-220009) passes 19 and exits 1 at 67.9%. Out-of-scope is 5/5, single-source 7/8, false-premise 5/6, edge-case 2/3, but multi-source is 0/6: the stub over-refuses or retrieves only one of the two documents, and it once wrote an uncited claim. `edge-multi-ask` and `fp-tcf-gettcdata` fail the same way, and `ss-tcf-cmp-version` (single-source) is an over-refusal that passed when run alone, so it is also the flakiness example in the scale plan. All the stub failures are kept as evidence, not tuned to pass. Telling a retrieval miss from a model miss is exactly what the retrieval metric above would do (D44).

`caseResults/baseline.json` is that 28-case run (19 of 28), promoted so a later run can show `improved since baseline` and regressions. A baseline is a previous run's report that later runs are compared against. Normally it is a run you trust. In this repo it is the latest stub run (19 of 28): a reference for change, not a known-good run. Eight of the nine failures were diagnosed in an earlier full run by replaying the search against each case's gold chunks: for 7 of the 8, the top 3 chunks miss one of the two needed documents, and 1 (`fp-tcf-gettcdata`) is a model miss, since its gold chunk was retrieved first and the stub still refused. `ss-tcf-cmp-version` has not been diagnosed. Retrieval is not the whole story, though. In one experiment on that earlier run (20 of 28), raising the stub's top-k from 3 to 6 made three cases pass (two multi-source, one edge) and three false-premise cases fail with over-refusals, and the score stayed at 20 of 28. The stub's refusals depend on its prompt as well as on what it retrieves, and the harness alone cannot separate the two (D44).

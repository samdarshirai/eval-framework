# Scope: what is in, what is out, and why

The brief asks for two different things, so this page has two halves:

- **Part A, the eval set** (deliverable 2: "what you deliberately did not cover, and how you would grow it"): which questions the cases do not test.
- **Part B, the build** (practical notes: "what you cut is part of your answer"): what I dropped from the harness to stay inside 5 hours.

Decision numbers (D#) point to `grilling-decisions.md`; unit numbers point to `build-order.md`. Guiding rule from the brief: a small thing that works beats a large thing that does not.

---

# Part A: The eval set

## What the cases cover

Cases are written by hand from five real pages of the public Usercentrics docs (Browser Support, A/B Testing, Geolocation Rules, Google Consent Mode, TCF 2.2). No customer data. Each case names its gold chunk(s), so a case is checked against the source text, not against a remembered answer (D5, D6).

| Category | What it tests | Cases today / planned |
|---|---|---|
| Single-source | A fact from one chunk, including exact values (a version number) | 4 / 8 |
| Multi-source | An answer that needs two documents | 1 / 6 |
| False premise | A wrong assumption about a feature the docs cover; the assistant must correct it, not go along and not refuse (D3) | 1 / 6 |
| Out-of-scope | The docs cannot answer; the assistant must refuse. Two subtypes: `unrelated` (pricing) and `plausible-nonexistent` (sounds real, is not) (D4) | 2 / 5 |
| Edge cases | Exact-value precision, multi-ask, light paraphrase (D8) | 0 / 3 |

Planned total is 28 (unit 21); 8 exist today.

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
- **Staleness is caught by the loader.** A gold chunk that no longer exists is a hard error; a doc that changed since the case was last confirmed is a warning (D21, warning still to be built).
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
| **Groundedness** (LLM judge, gold-chunk shortcut): does the cited chunk support the claim? | Deliverable 3, the check that is not a string match. Chosen first because "a wrong answer that sounds right reaches a customer" (D10, D43) |
| **Source** (deterministic, document-level) | Proves a multi-source answer used the required documents (D11) |
| **Judge calibration** inside the same command: 7 trap pairs plus 20 hand-labeled Groundedness pairs (measured: 20/20 agreement, 0 of 10 false-supported) | A judge you have not measured is not evidence (D16, D41, D43) |
| **Regression against a baseline** (`--baseline`): a case that passed there and fails now fails the run, after one re-run | Blocks a silent slide even when the pass rate stays above the floor; the re-run keeps flakes from crying wolf (D13, D14, D46) |
| **Exit-code rules**: pass floor 90%, and any failing out-of-scope case fails the run | Blocks a hallucination even when the pass rate is high (D13, D34) |
| **Reusable by other teams**: `--config`, `--endpoint`, `--skip-calibration`, pluggable knowledge source | The "pattern for thirty more" (D38, D42) |

## In scope, planned and not built yet

These are committed to the plan; say plainly that they are unfinished.

| What | Unit | Note |
|---|---|---|
| Full 28-case set | 21 | 8 exist today |
| **Pattern document** for an engineer with one hour | 24 | Deliverable 4 |
| **Scale plan**, one page | 25 | The five questions in the brief |
| Measured calls, tokens, cost, wall-clock time | 20 | Feeds the scale plan cost line |
| Relevance check (advisory, never fails a case) | 17 | Last on the cut list |
| Doc-hash staleness warning | 22 | First to cut |
| README and the live-change rehearsal | 26, 23 | |

Cut order if time runs out (D30): doc-hash warning, cost detail, automatic re-run, trap-pair run, Source, Relevance. Never cut: claims-only contract, Citation integrity, Groundedness with calibration, Refusal, Coverage, the exit code, the eval set, the pattern document.

## Out of scope, designed but not built

Described in the pattern document and scale plan, not coded in v1 (D22, D23).

| What | Why not built |
|---|---|
| Per-app `checks` list and risk tiers with mandatory checks | Only needed with more than one app; v1 runs every check for the one app |
| Platform-owned `judgeModel` and minimum pass floor | Enforcement is a later platform decision (D42) |
| Nightly scheduled runs, majority-of-N runs | A scale-plan topic; temperature 0 already keeps runs steady |
| Severity-tiered pass/fail (CRITICAL / ERROR / WARNING) | Better design, but new rules plus code; the gating/advisory flag is the small step toward it |
| `http` and `manifest` knowledge sources | Placeholders; the `docs` source covers the one app |

## Out of scope, considered and left out

| What | Why |
|---|---|
| **Retrieval metric (context recall)** | The harness sees only claims and citations, never the retrieved chunks. Needs an optional `retrieved` field in the contract. About 2-3 hours as its own PR (D44) |
| **Cited-from-memory check** | Same reason: needs the `retrieved` field that other apps would not have |
| **Fail-fast to save judge cost** | Skipping judge-backed checks after a deterministic failure saves roughly 5-10 judge calls per failing case, but a failing case would then show only its first failure. Deferred until the measured cost report shows it matters (D45) |
| Refusal-message-quality check | Behaviour is checked, wording is not |
| HyDE and re-ranking | Assistant retrieval improvements, not needed to prove the harness works |
| A separate model for the Coverage confirm step | The main judge is enough; one judge setting is simpler (D24) |
| promptfoo / DeepEval, Elasticsearch or a vector DB | A ~300-line runner I know line by line fits the live-change requirement; BM25 in memory suffices for 5 pages |

## Known open item

The multi-source seed case `ms-geo-tcf-and-consent-mode` fails today: the stub's retrieval misses the second half of a two-document question, so the default run exits 1 at 87.5%. Kept as evidence, not tuned to pass. Telling a retrieval miss from a model miss is exactly what the retrieval metric above would do (D44).

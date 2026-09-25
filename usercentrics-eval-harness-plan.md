# Usercentrics Take-Home — Eval Harness Plan

Sep 23, 2026 · @Samdarshi Rai
Updated after the grilling session. Decisions are logged in `grilling-decisions.md`; terms are defined in `CONTEXT.md`.

## Challenge summary

Usercentrics has an internal AI Hub (LLM gateway, MCP servers, connectors, skill hub) that 30+ non-engineering teams already build AI apps on top of. There is currently no way to check whether any of those apps give correct answers. The case study app is **Implementation Assistant**: an internal bot that answers technical setup questions for Customer Success/Support, grounded in public product docs. A wrong-but-confident answer can reach a paying customer.

**Deliverables (take-home, 5-hour cap):**

1. A working evaluation harness — one command, produces a result, evaluates a thin stub of the assistant.
2. An evaluation set, with reasoning for how cases were chosen, what was deliberately left out, and how to grow it without it becoming a maintenance burden.
3. At least one non-string-match check (e.g. groundedness, hallucination detection, refusal behaviour, regression).
4. A pattern document — written for a different engineer, in a different department, who has never seen the code, to apply this to their own app in about an hour.

**For the live session:** a one-page/five-slide plan for scaling this from one app to thirty — where it runs and when, who owns what, what "good enough to ship" means, cost/time, and what breaks first at scale.

**Session format:** 60 minutes total — about 25 minutes walking through the actual code (screen share, not slides, expect them to open files unprompted), about 25 minutes of questions including one live change to the harness, about 10 minutes for your own questions.

## Build plan — steps 1 through 4

**Central story:** this submission is not "an eval harness for a RAG assistant" — it's a small, reusable evaluation contract that any AI application on the hub could implement, demonstrated against a minimal assistant. The application produces atomic claims with evidence; the evaluation layer checks whether required facts were covered, whether claims are grounded, whether unsupported questions are refused, and whether behavior regresses over time. The same contract could be implemented by other AI applications, while each domain team keeps ownership of its own expected behavior and thresholds.

### Step 1 — Doc snapshot (knowledge base)

Scope derived from the eval categories (Step 3), not picked first: 5 pages from Usercentrics Web CMP v2 (docs.usercentrics.com, `#/browser-cmp`), each chosen because it's load-bearing for at least one test category, not just "more coverage." Save as markdown in the repo, each file tagged with its source URL:

1. Browser Support — single-doc, dense checkable facts (version tables): https://docs.usercentrics.com/#/browser-cmp?id=browser-support
2. A/B Test — single-doc + false-premise bait ("distribution is always even"): https://docs.usercentrics.com/#/ab-test
3. Geolocation Rules — multi-doc pairing #1, with Google Consent Mode: https://docs.usercentrics.com/#/geolocation-rules
4. Google Consent Mode — multi-doc pairing #1 and #2: https://docs.usercentrics.com/#/consent-mode
5. TCF 2.2 — multi-doc pairing #2 (with Consent Mode) + a second, distinct source of false-premise bait: https://docs.usercentrics.com/#/tcf2

Deliberately excluded: Implementation (Browser UI/SDK), Account Interface, Embeddings, Events, Interaction/Granular Analytics, Smart Data Protector, and the 5 Performance Guide sub-pages — all real docs, but none is uniquely load-bearing for any of the four eval categories once Browser Support, A/B Test, Geolocation Rules, and Google Consent Mode + TCF 2.2 are in. Out-of-scope cases are tested by asking about a page that was never scraped (e.g. pricing, or Apps CMP), or about a plausible-sounding feature that doesn't exist, not by adding more pages.

**Chunk IDs are heading-based**, not positional: `consent-mode#default-consent-states`, not `consent-mode#chunk-3`. Positional IDs shift whenever a doc is re-chunked or updated, which would silently rot every case that references them. A chunk ID is document name plus section heading, so it survives re-chunking. If a heading appears more than once in a document, each occurrence gets a numeric suffix counting from 1 (`doc#overview-1`, `doc#overview-2`); a heading that appears once has none. Fine-grained IDs are what make claim-level groundedness checking possible — a document ID alone is too coarse to verify a specific claim against.

Reasoning chain used to pick pages — risk/failure mode → eval case → required evidence → source document, not the reverse:

- Correct factual answer → single-source → Browser Support
- Requires synthesis → multi-source → Geolocation Rules + Google Consent Mode
- Hallucination bait → false-premise → A/B Test / TCF 2.2
- Outside knowledge boundary → out-of-scope → no supporting source (deliberately)
- Citation is wrong → groundedness → an exact chunk within any of the above

### Step 2 — Thin assistant

One function: question in, structured answer out. **The assistant runs as a separate HTTP service** (one POST endpoint: `{question}` in, claims JSON out) and the harness calls it over HTTP. This is the same integration path any other app on the hub would use, in any language, so the harness code is identical for the stub and for a real app. The stub is a small standalone Spring Boot service in its own Maven module (D37; this replaced the JDK `HttpServer` first planned), so the harness module never depends on it.

- **Retrieval:** BM25 keyword search over the chunks to pull the top 2–3 most relevant.
- **Generation:** LLM prompted to answer using only those chunks, and to refuse if the docs don't cover it. Temperature 0. The prompt alone decides "not covered": BM25 always returns the top chunks and there is no retrieval score cutoff. A weaker stub gives the Refusal check something real to catch; a score cutoff is a "what I'd add next" item.
- Two behaviours: answer, or refuse. Two known gaps, deliberately not handled (see Step 3): **under-specified** questions and **partially answerable** questions.
- **Output shape (the contract):** the response is `refused` (true/false) plus a **list of claims**, each with `claim` (one atomic factual statement) and `citations` (chunk IDs supporting it). There is **no free-text answer field** — every assertion the assistant makes is a claim, so everything it says is checkable. A refusal has zero claims; anything non-factual lives in the `refused` flag, not in `claims`. This is what lets the checks work claim-by-claim instead of guessing how to split an answer.

### Step 3 — Evaluation set

\~28 test cases in YAML (8 single-source, 6 multi-source, 5 out-of-scope, 6 false-premise, 3 edge cases) — deliberately capped rather than maximizing coverage; v1 establishes a maintainable mechanism, coverage grows from real failures and support corrections.

**Case format.** Each case has: the question, a category, **`expected_behavior`** (`answer` or `refuse`, set per case, not implied by category), and **expected facts**. Each expected fact is a plain-language key point that must appear (not tied to a sentence count or phrasing, since the LLM may split a fact across claims) plus:

- **`chunks`**: the *gold chunks*, i.e. chunks a human verified support the fact. They are alternatives — citing *any one* is enough. No separate `source_doc` field; the document is derived from the chunk ID.
- **`keywords`** (optional): tokens that must all appear in a claim for it to count as a candidate cover. Used for facts with a checkable token (a version number). Keywords are a *necessary* filter, never sufficient — see Coverage in Step 4.
- Case metadata: `source` (`support-ticket-1234` or `authored`), `owner`, `added` (date).

**Categories:**

- **Single-source** — answerable from one doc.
- **Multi-source** — needs two docs combined.
- **False premise** — a question built on a wrong assumption about a feature the docs *do* cover (e.g. "since the A/B test always splits evenly…"). Expected behaviour is **answer**: the assistant must correct the premise with cited claims. Going along with the premise fails; refusing also fails, because the docs cover the topic.
- **Out-of-scope** — the docs can't answer. Expected behaviour is **refuse**. Two subtypes, tagged per case so the report can show which the assistant fails on: `unrelated` (pricing, legal advice) and `plausible-nonexistent` (sounds like a real feature, isn't in the docs — the hardest hallucination bait). Roughly 2 unrelated and 3 plausible-nonexistent.
- **Edge cases (3)**, each aimed at a different part of the pipeline: **exact-value precision** (a version number from the Browser Support tables — a near-miss is a confident wrong answer, and keyword filtering catches it cheaply), **multi-ask** (two unrelated answerable questions in one, checks that all facts are covered), and **light paraphrase** (worded differently but still sharing at least one distinctive doc term).

**Deliberately left out:** multi-turn, non-English, customer-specific setups. Also:

- **Hypothetical-answer-first retrieval (HyDE)** — not built, so no test cases; worth revisiting if retrieval is swapped.
- **Re-ranking** — improves precision, adds latency, not needed to prove the harness works.
- **Adversarial / prompt-injection testing** — a different threat model than answer correctness from trusted internal docs; named as awareness, not tested.
- **Hard vocabulary mismatch** (user says "cookie banner", docs say "CMP", zero shared terms) — this tests BM25's known weakness, i.e. retrieval quality rather than the harness. Would be tested if retrieval changed.
- **Refusal message quality** — refusal is checked on behaviour, not wording.
- **Citing a real chunk that retrieval never showed the model** (cited from memory) — needs an extra `retrieved` field in the contract that other apps wouldn't have.

**Known design gaps, not just missing techniques:**

- **Under-specified questions** (incomplete rather than wrong, e.g. no product named) fit neither answer nor refuse. Whether to ask a clarifying question or state an assumption is left open.
- **Partially answerable questions** (setup is documented, cost isn't) have no response shape for "here's what the docs cover, I can't answer the rest." Options are an `unanswered` field in the contract or leaving it out. Left out for v1. Authoring rule: every case is fully answerable or fully out-of-scope (a judgement, not something the loader can enforce).

**How the set grows without becoming a burden:**

1. Every case records its origin (`source`, `owner`, `added`).
2. Hard cap per category. Adding a case means retiring or merging one, so the set stays small enough to run in CI. This is an authoring rule; the loader does not enforce it.
3. A candidate case is admitted only if it adds a *distinct* failure. If it would fail the same check for the same reason as an existing case, it becomes a note on that case.
4. The loader flags rot: a missing gold chunk is a hard error; a doc whose content hash changed since the case was last confirmed is a warning (built, D48).

### Step 4 — Harness

One command runs the harness (the stub is started separately; README documents both). The harness first checks that the endpoint is reachable and, if not, exits immediately with a clear message on how to start the stub. It then loops every case: calls the assistant over HTTP, gets back claims + citations, and runs six checks per case. Every check implements one small interface (case + response in, pass/fail + reason out), and the harness runs whatever is in a single registration list, so no check is wired into the runner by name.

**Order of evidence for a citation, per claim:** (1) integrity, (2) gold-chunk match, (3) judge, so the judge only ever sees valid IDs and only decides what the deterministic checks couldn't.

- **Coverage** — is each expected fact covered by a claim? For a fact with `keywords`, a claim must contain **all** of them to be a candidate; no candidate means not covered, with no LLM call (this is the fast fail for "Safari 13" vs "Safari 14"). A keyword hit never passes on its own, because it can't detect negation ("all Safari versions except 14"), so each candidate goes to the main judge model with a narrow question: do the fact and the claim agree? For a fact without `keywords`, one judge call gets the fact plus the numbered claims and returns which claims cover it. Either way the result is a set of *covering claims*, which the next checks use.
- **Citation integrity** — deterministic. Every claim needs at least one citation, and every cited chunk ID must exist in the knowledge base. A nonexistent ID is a hard fail ("fabricated citation") — arguably worse than a missing one, because it looks like verified evidence. Runs first on every claim.
- **Groundedness** — for each claim, does its cited chunk actually support it? If the claim cites a gold chunk of the fact it covers, it **passes deterministically** with no LLM call. Otherwise it falls to the LLM judge, one claim + its chunk per call. A gold-chunk mismatch never fails a case on its own, because another chunk may legitimately support the fact.
- **Source** — deterministic. The documents behind a covering claim's citations must match the documents of the fact's gold chunks. This is what proves a multi-source answer actually used both docs.
- **Relevance** — is each claim pertinent to the question, not just true-but-irrelevant padding? LLM judge, same narrow yes/no pattern. **Advisory**: reported and counted, but never fails a case, because it is the most subjective and likely flakiest judge.
- **Refusal** — deterministic, no judge. Compares the `refused` flag and claim count with `expected_behavior`. Fails if: refuse was expected but the assistant answered (the hallucination case); answer was expected but it refused (over-refusal); or `refused` is true while claims are present (contract violation). The contract made this check free.

Philosophy: use a deterministic check wherever the property is deterministic (citation integrity, source, refusal, keyword filtering, gold-chunk matching); reserve an LLM judge for where semantic interpretation is genuinely required (coverage confirmation, groundedness fallback, relevance). A stronger Staff-level argument than defaulting to an LLM judge for everything.

**Pass rule.** A case passes if every **gating** check passes. All checks gate except Relevance. Failing checks are always listed in the per-case detail. Roll up into a summary (pass rate overall, by category, and out-of-scope by subtype). Print a summary table to the terminal; write full detail to timestamped JSON in `caseResults/`.

**Exit code.** Non-zero if any of:

1. the overall pass rate is below the floor (**90%**, i.e. at most 2 failing cases out of 28);
2. there is a **regression**: a case that passed in the **baseline** (a previous results file promoted by copying it, passed via `--baseline` or the `baseline` config key) and fails now, even when the overall rate is above the floor;
3. **any out-of-scope case fails**, because that is the brief's headline risk (a confident wrong answer reaching a customer).

**Non-determinism.** Temperature 0 for the assistant and every judge. A suspected regression is re-run once and only counts if it fails both attempts; the report notes which cases needed a re-run, a free signal of how flaky the suite is. If the re-run rate turns out high, the upgrade is majority-of-N runs.

**Models.** The assistant and the judge are separate config values in the same provider. The judge is stronger than the assistant (e.g. assistant on a fast model, judge on Sonnet 5 at low effort), so it doesn't share the assistant's blind spots. The Coverage confirm step uses the main judge, not a third model.

**Judge calibration (Groundedness).** Hand-label 20 claim/chunk pairs and check how often the judge agrees, deliberately built rather than random: 10 subtly **unsupported** pairs (adds a detail the chunk doesn't state, wrong version number, overstates "always" vs "typically"), 5 plain supported pairs, and 5 **hard-supported** pairs (paraphrase, split across two sentences, equivalent numbers). Report two numbers: overall agreement (target ≥ 90%) and false-"supported" on the unsupported pairs (**target 0 of 10**). The asymmetry matters: a false "unsupported" makes a good case fail (annoying but safe); a false "supported" lets a wrong claim through silently, which is the exact risk the brief describes. False-"unsupported" is counted but has no target. If any unsupported pair is missed, revise the judge prompt, re-measure, and record the change in `calibration-notes.md`. Coverage-by-judge and Relevance are named as uncalibrated in v1; Coverage is the first to add. Before trusting the Coverage confirm step, run ~5 trap pairs ("all except 14", "Safari 13, not 14", "Safari 14 is not supported", plus two correct paraphrases); any miss means raising the effort for that call.

**Cost and time are measured, not guessed.** The LLM client records calls and tokens by role (assistant, judge) and by check. The report prints total calls, tokens, estimated cost, and wall-clock time. Judge calls happen per claim, so a case is closer to ~10 calls than the ~3 first estimated (the gold-chunk shortcut trims some).

**Considered, out of scope: severity-tiered pass/fail.** A flat rule ("every gating check must pass") is a blunt instrument — a genuinely better design assigns severity (e.g. CRITICAL: incorrect required fact, unsupported claim, wrong refusal; ERROR: missing citation, partial coverage; WARNING: irrelevant-but-harmless claim) and defines case_pass as no CRITICAL failures AND coverage above a threshold. The gating/advisory flag is the small step toward it. Not built here: it's new design plus new code for a take-home whose evaluator is judged on the harness existing and being reasoned about, not on rule sophistication. Stated as the v2 improvement.

**Design for the live change.** Three changes rehearsed out loud: (1) **add a check** (one class, one line in the registration list); (2) **flip a gating flag or change the pass floor** (one value); (3) **add an eval case** (data only). Models, cases, and the report are "here's where it lives".

**Cut order if time runs short** (first cut to last): (1) doc-hash staleness warning; (2) cost reporting reduced to call count and wall-clock time, with a hand-calculated example in the scale plan; (3) automatic re-run of suspected regressions (flips reported, not retried); (4) the trap test run (keep the examples as documentation); (5) Source check (if cut, say "multi-source coverage is enforced by gold-chunk matching only"); (6) Relevance check. **Never cut:** claims-only contract, Citation integrity, Groundedness with its calibration, Refusal, Coverage, the exit code (floor, out-of-scope rule, regression), the eval set, the pattern document.

## Step 5 — Pattern document

Written for a different AI Enablement Engineer, in a different department, who has never seen this code and has about one hour. It documents the reusable **pattern**, not this implementation. Structure:

1. **The contract** — any app exposes one POST endpoint: `{question}` in, `{refused, claims: [{claim, citations}]}` out. Works for any language. **Point the harness at an instance started for testing, not live prod:** a candidate build for CI, or a dedicated prod-equivalent instance (same prompt, model and config) for nightly drift checks. Live prod would mix eval traffic into real analytics and rate limits, may have side effects, can change mid-run, and may not allow temperature 0. Send an `X-Eval-Run` header on every request so the gateway can filter eval traffic. Fallback for a team that can't expose an endpoint: export answers to a JSON file and have the harness score the file (a snapshot, so it needs regenerating after every change).
2. **Writing test cases** — the four generalizable categories (single-source, multi-source, out-of-scope, false-premise), where to source real questions (support tickets, Slack threads), the `expected_behavior` field, and the authoring rule that every case is fully answerable or fully out-of-scope.
3. **Menu of checks** — coverage, citation integrity, groundedness, source, relevance, refusal, plus regression — with guidance on which check suits which kind of app (customer-facing needs groundedness and refusal; an internal summarizer might only need coverage).
4. **Thresholds and reporting** — how to set a pass floor, what the baseline is and how to promote one, and the out-of-scope hard rule.
5. **The one-hour path with a ~10-case starter set** (4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise): expose the endpoint (15 min), write the 10 cases (25 min), run and read the report (10 min), fix case mistakes (10 min). The starter set is where you begin, not where you stop; growth follows the four rules in Step 3.
6. **Worked example** — Implementation Assistant and its 28-case set, shown *after* the general recipe as what a mature set looks like, not as the instructions themselves.
7. **Onboarding design for other apps (designed, not built in v1)** — a per-app config with a `checks` list (default: all) and a risk tier; see Step 6. v1 runs all checks for Implementation Assistant with no per-app config.

## Step 6 — One-page scale plan (for the live session)

**Where it runs, and when:** Locally — fast \~10-case subset against a locally started candidate, feedback in under a minute. In CI — full set on any prompt/model change, against a **candidate instance started for the run** (prod doesn't have the change yet), torn down afterwards; blocks merge on regression. Nightly — scheduled run against a **dedicated prod-equivalent instance** (prod's exact prompt, model and config) to catch silent model/provider drift even with no code change.

**Never point the harness at live prod:** eval traffic pollutes usage analytics and cost attribution and can hit rate limits real users share; prod may have side effects (logging, tool calls, connectors that write); a deploy mid-run makes results uninterpretable; and prod may not allow temperature 0, which the determinism decision relies on. The harness only knows an endpoint URL, so this is a deployment choice, not a code change. **Eval traffic is marked** with an `X-Eval-Run` header so the gateway can exclude it from analytics.

**Who owns what:** Platform (this role) owns the shared harness, judges, judge calibration, risk tiers, and dashboard. Each department owns its own test cases, thresholds above the tier floor, and the ship decision. Explicitly refuse to centralize **domain truth** — the platform can't know what a correct Legal or HR answer looks like.

**Risk tiers.** The platform defines 2–3 tiers, each with a pass floor and a set of **mandatory checks** (e.g. the top tier makes Groundedness and Refusal mandatory). Every app starts in the **top tier with all checks**. Lowering the tier, or removing a check, requires a written reason and platform sign-off during a one-time onboarding review, recorded in the app's config; a change in audience (internal to customer-facing) triggers re-review. The loader rejects a config that drops a tier-mandatory check. Departments can only raise the floor, never lower it. There is **no per-release approval queue**: the platform's control is the floor plus mandatory checks, enforced in CI, so it can't become a bottleneck for 30 teams.

**"Good enough to ship":** no regression against the last known-good run, plus a floor and mandatory checks set by the app's risk tier — a customer-facing app like Implementation Assistant gets the strictest tier. The department owner makes the call; the tier sets the minimum bar it can't go below.

**Cost and time:** rough model — cases × (1 assistant call + judge calls, measured per run) × runs/month × 30 apps. The numbers come from the harness's own measured calls, tokens, cost and wall-clock time for one full run, extrapolated with stated cadence assumptions (CI per prompt change, nightly full set, local subset). Keep a CI run under \~5 minutes via parallelism, or teams start skipping it.

**What breaks first at scale:** (1) flaky/inconsistent judge verdicts — teams learn to ignore red builds, same as flaky integration tests (mitigated by temperature 0, the single re-run, and calibration); (2) eval sets going stale as docs and product behavior change underneath them (mitigated by the growth rules and staleness checks); (3) cost and latency — 30 apps × cases × multiple judge calls adds up fast; (4) ownership ambiguity — without a clear domain owner, nobody actually decides whether an app is safe to ship. Next thing to build: pipe sampled real traffic from the LLM gateway into candidate eval cases.

## Five-hour time budget

| Task | Time |
| --- | --- |
| Doc snapshot + thin assistant (incl. HTTP stub) | 0:45 |
| Harness + checks | 1:45 |
| Evaluation set (\~28 cases) | 1:00 |
| Pattern document | 0:45 |
| Scale plan + rehearsing the live-change | 0:45 |

This budget is tight given the decisions above (HTTP stub and adapter, six checks, baseline comparison, calibration set). If it slips, follow the cut order in Step 4.

Keep a running "what I cut and why" list throughout — it's explicitly scored. A small custom runner (\~300 lines) is the right call over promptfoo/DeepEval here, since the brief requires knowing every line live; worth mentioning those tools were considered, and what you'd adopt once this runs at 30-app scale.

# Grilling Decisions — Eval Harness Plan

Decisions settled while stress-testing `usercentrics-eval-harness-plan.md`. Terms are defined in `CONTEXT.md`.
Status: **in progress**. Not final until confirmed.

## Contract and cases

1. **Claims only.** An answer is a list of claims plus a `refused` flag. There is no free-text answer. A refusal has zero claims.
2. **`expected_behavior` is set per case** (`answer` or `refuse`), not implied by category.
3. **False premise** = wrong assumption about a feature the docs cover. Expected: answer and correct the premise with cited claims. Going along with it fails. Refusing fails.
4. **Out-of-scope** = docs can't answer. Expected: refuse. Subtypes: `unrelated` (pricing, legal) and `plausible-nonexistent` (sounds real, isn't). Plan: ~2 unrelated, ~3 plausible-nonexistent.
5. **Expected fact** carries `chunks`: a list of gold chunks, *any one* of which is enough. No separate `source_doc` field. The doc is derived from the chunk ID.
6. **Chunk IDs are heading-based** (`consent-mode#default-consent-states`), not positional. The loader fails loudly if a tagged chunk ID doesn't exist.
7. **Partially answerable questions** are a documented gap, not tested (same as under-specified). Authoring rule: every case is fully answerable or fully out-of-scope. It is not loader-enforced.
8. **Three edge cases:** exact-value precision (version numbers), multi-ask (two unrelated answerable parts), light paraphrase (shares at least one distinctive doc term). Hard vocabulary mismatch is deliberately excluded (BM25 limit, same bucket as HyDE and re-ranking).

## Checks and pass rules

9. **Six checks:** Coverage, Groundedness, Relevance, Citation completeness, Source, Refusal.
10. **Groundedness:** a claim citing a gold chunk of the fact it covers passes deterministically. Otherwise it falls back to the LLM judge. A gold-chunk mismatch never fails a case on its own.
11. **Source** check (deterministic) verifies the cited chunks' documents match the fact's gold chunks' documents. It exists so multi-source cases prove both docs were used.
12. **Relevance is advisory** (reported, never fails a case). All other checks gate.
13. **Exit code:** non-zero if the pass rate is below the floor, OR any regression vs the baseline (pass to fail flip), OR any out-of-scope case fails.
14. **Non-determinism:** temperature 0 everywhere. A suspected regression is re-run once and only counts if it fails again. The report notes which cases needed a re-run.
15. **Judge model** is stronger than the assistant model. Both are separate config values, both routed through OpenRouter (D39).
16. **Judge calibration (Groundedness only; Refusal has no judge, see 25):** 20 hand-labeled pairs = 10 subtly unsupported + 5 plain supported + 5 hard-supported (paraphrase, split across sentences, equivalent numbers). Targets: overall agreement >= 90% and **0 of 10** unsupported pairs judged "supported". False-"unsupported" is counted but has no target (annoying but safe). Only Groundedness is calibrated in v1.
17. **Measured cost and time:** the LLM client records calls and tokens by role and check. The report prints totals, estimated cost and wall-clock time. The scale plan extrapolates from measured numbers, not the "~3 judge calls" guess (real number is closer to ~10 per case).

## Eval-set growth

18. A case may record `source`, `owner` and `added` (optional, PR #1 review; the loader does not require them). `subtype` stays, since out-of-scope reporting is by subtype.
19. Hard cap per category. Adding a case means retiring or merging one.
20. A candidate is admitted only if it adds a distinct failure. Otherwise it becomes a note on an existing case.
21. Loader flags stale cases: missing gold chunk = hard error. Doc content hash changed since the case was last confirmed = warning (design now, build if time).

## Scale plan (designed, not built in v1)

22. **Risk tiers:** platform defines 2-3 tiers, each with a pass floor and mandatory checks. Every app starts in the top tier. Lowering it needs a written reason and platform sign-off at onboarding, recorded in config. A change in audience triggers re-review. Departments own cases, thresholds above the floor, and the ship decision. They can only raise the floor. There is no per-release approval queue.
23. **Per-app `checks` list** in config (default all) replaces any app-type enum or `citations` boolean. Removing a check follows the downgrade rule, and the loader rejects a config missing a tier-mandatory check. **Out of v1 scope.** v1 runs all checks for Implementation Assistant. Document as the onboarding design in the pattern doc and scale plan.

## Coverage mechanics

24. **Finding the covering claim.**
    - A fact may list `keywords`. A claim must contain **all** of them (case-insensitive) to be a candidate. Keywords are *necessary, not sufficient*: no candidate means the fact is not covered, decided with no LLM call. This is the cheap fast-fail (e.g. Safari 13 vs 14).
    - Each candidate claim goes to the **main judge model** (low effort), which answers "do these two statements agree?". Any confirmed candidate covers the fact. A keyword hit never passes on its own, because it can't detect negation ("all Safari versions except 14").
    - A fact **without** `keywords` sends the fact plus the numbered claims to the judge, which returns the covering claim indices (an empty list means not covered).
    - The covering claims are what the Source check and the gold-chunk shortcut run on.
    - No separate confirm model: the main judge is used, which keeps decision 15 intact.
    - Before trusting it, run ~5 trap pairs ("all except 14", "Safari 13, not 14", "Safari 14 is not supported", plus 2 correct paraphrases). Any miss means raising the effort level for that call.
    - Coverage-by-judge is otherwise uncalibrated in v1. Name it as the first calibration to add.

25. **Refusal is fully deterministic, no judge.** It compares the `refused` flag and claim count with `expected_behavior`. Fails: expected refuse but answered; expected answer but refused; `refused: true` with claims present. Excluded (named in the cut list): quality of the refusal message.

26. **Citation completeness is renamed Citation integrity.** Per claim: at least one citation, and every cited chunk ID exists in the knowledge base (else a hard fail: "fabricated citation"). Runs first on every claim. Per-claim citation order: (1) integrity, (2) gold-chunk match passes deterministically, (3) otherwise the Groundedness judge. Left out (cut list): a real chunk that retrieval never showed the model (cited from memory), which needs an extra `retrieved` field in the contract.

27. **Live-change readiness.** Every check implements one small interface (case + response in, pass/fail + reason out) and the harness runs whatever is in a single registration list. No check is wired into the runner by name. Rehearse three changes out loud: (1) add a new check (one class, one list line), (2) flip a gating flag or the pass floor, (3) add an eval case (data only). Models, cases and the report are "here's where it lives".

28. **The harness calls the app over HTTP, in v1 too.** The app runs separately and exposes one POST endpoint that takes `{question}` and returns the claims JSON. This is the integration path for every app, in any language. Replay file (harness scores a saved answers file) is documented as the fallback for teams that can't expose an endpoint. Costs about 20 lines for the harness HTTP adapter plus a small HTTP server for the stub.

29. **Harness only is the one command.** The stub is started separately and the README documents both commands. The brief only requires one command to run the harness. Mitigation: the harness checks the endpoint first and, if it is unreachable, exits immediately with a clear message that says how to start the stub. There is no raw connection error.

30. **Cut order if time runs short** (first cut to last): (1) doc-hash staleness warning, (2) measured cost reduced to call count and wall-clock time only, with a hand-calculated example in the scale plan, (3) automatic re-run of suspected regressions (flips are reported, not retried), (4) the trap test run (keep the examples as documentation), (5) Source check, cut just before Relevance (if cut, say "multi-source coverage is enforced by gold-chunk matching only"), (6) Relevance check. **Never cut:** claims-only contract, Citation integrity, Groundedness with its calibration, Refusal, Coverage, the exit code (floor, out-of-scope rule, regression), the eval set, the pattern doc.

31. **Pattern doc starter set: ~10 cases** (4 single-source, 2 multi-source, 2 out-of-scope, 2 false-premise) for the one-hour path. Suggested hour: expose the endpoint (15 min), write 10 cases (25 min), run and read the report (10 min), fix case mistakes (10 min). The 28-case set is the worked example of a mature set. Growth follows decisions 18-21.

32. **Which instance the harness calls.** Never live prod. CI and local use a candidate instance started for the run (prod doesn't have the change yet). Nightly drift checks use a dedicated prod-equivalent instance (same prompt, model, config). Reasons: eval traffic pollutes analytics and rate limits, prod may have side effects, prod can change mid-run, prod may not allow temperature 0. Eval traffic carries an `X-Eval-Run` header so the gateway can exclude it. No harness code change: it only knows an endpoint URL.

33. **Duplicate headings.** If a heading appears more than once in a document, each occurrence gets a numeric suffix counting from 1 in order (`doc#overview-1`, `doc#overview-2`). A heading that appears once has no suffix. Known limit: inserting a duplicate heading earlier shifts the numbering. The loader's missing-chunk error (decision 6) catches it.
34. **Pass floor is 90%.** With 28 cases, at most 2 failing cases are tolerated (26/28 = 92.9% passes, 25/28 = 89.3% fails).
35. **The per-category cap is an authoring rule only.** The loader does not enforce it (decision 19).

36. **The stub decides "not covered" by prompt only.** BM25 always returns the top chunks and the model is told to refuse if they don't cover the question. There is no retrieval score cutoff. Reason: the assistant isn't the point, and a weaker stub gives Refusal and the out-of-scope hard rule something real to catch. A score cutoff goes on the "what I'd add next" list.

37. **Multi-module Maven, Spring Boot stub** (PR #1 review). Modules: `kb` (Chunk, Chunker), `llm` (Llm, OpenRouterLlm), `assistant` (Spring Boot app), `eval` (harness). `eval` depends on `kb` and never on `assistant`, so D28 is enforced by the build instead of an import test. Supersedes the JDK `HttpServer` choice in the tech-stack doc. Config lives in `config/application.yaml` (Spring reads `./config`), keeps `server.address: 127.0.0.1`. Both apps ship as jars: `assistant/target/assistant.jar`, `eval/target/eval.jar`.

38. **Pluggable knowledge source** (PR #1 review discussion). The harness only needs the same chunks the assistant has, so where it gets them is configurable: `knowledgeBase.type` in `eval/config.yaml`, default `docs` (chunk `./docs` with `kb.Chunker`). `http` (GET `[{id, text}]` from the app) and `manifest` (JSON file) exist as placeholders that fail with a clear message. New source = one `KnowledgeSource` class plus one case in `KnowledgeSources`. When `http` is built, D8's ordering changes (the app must be up before cases are validated); amend D8 then.

    **For the scale plan / pattern doc (onboarding of other apps):** the contract is HTTP and JSON plus the chunk-ID scheme (D6, D33). `kb` is the reference implementation of that scheme, not something other apps must depend on. An app onboards by choosing a source: `http` (it serves the chunks it indexed, so the harness sees exactly what the app has and no ID rules are reimplemented, best fit for any language), `manifest` (its build exports `[{id, text}]`), or its own `KnowledgeSource` class (Java). Apps not grounded in documents get a shorter `checks` list (D23). Open items to state: the app and harness must see the same doc version (guard: D21 doc-hash warning, optionally a `hash` in the chunk payload); `http` changes D8's ordering.

39. **LLM calls go through OpenRouter** (replaces the direct Anthropic client). One `OpenRouterLlm` behind the `Llm` interface: OpenAI-compatible `POST https://openrouter.ai/api/v1/chat/completions`, `Authorization: Bearer $OPENROUTER_API_KEY`, system prompt as the first message, reply in `choices[0].message.content`. Models are provider-prefixed slugs in config (`assistant.model: anthropic/claude-haiku-4.5`), so the judge model (D15) can be a different provider under the same key. D14 still holds (`temperature: 0` is sent); OpenRouter ignores parameters a model does not support, so a model that rejects `temperature` no longer breaks the call, but whether a given route honours it is still a live check.

## Open

(none right now)

## Not yet grilled

- Pattern document structure and worked example
- Time budget and the "what I cut and why" list
- The live-change rehearsal (which change, how quickly)
- Build details (Java stack, `pom.xml` and `Main.java` show as deleted in git status)

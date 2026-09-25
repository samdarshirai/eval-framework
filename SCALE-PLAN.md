# Scale plan: from one application to the whole marketplace

Answers the five questions in the brief. Numbers come from one measured run of the Implementation Assistant (2026-09-25, 28 cases): 101 judge calls (27 of them calibration), **$0.218**, **501 s**. Where I estimate, I say so. `PATTERN.md` is the recipe for one team; `grilling-decisions.md` holds the decisions (D#).

## 1. What runs where, and when

| When | What | Against | Time |
|---|---|---|---|
| **Local**, editing a prompt | The cases touched (`--case a,b`), `--skip-calibration` | A candidate on the builder's machine | ~2.5 min for 10 cases (estimated, ~15 s per case) |
| **CI**, on any change to prompt, model, config or documents | Full set, with the baseline. **Blocks the merge** on a regression or a failing out-of-scope case | A candidate started for the run | ~7 min for 28 cases |
| **Nightly** | Full set | An instance with production's exact prompt, model and config, to catch silent provider drift | Unattended |
| **Weekly**, and when the judge model or prompt changes | Judge calibration (7 trap pairs, 20 labeled pairs) | The judge itself | ~90 s |

Before a change ships, local and CI decide. After, the nightly run says when something moved on its own. **Never against live production** (D32): eval traffic pollutes analytics and cost attribution, and production can change mid-run. Eval calls carry an `X-Eval-Run` header so the gateway can exclude them.

## 2. Who owns what

| The platform owns | Each department owns |
|---|---|
| The harness and the contract (claims plus citations), the judge and its calibration, risk tiers with a floor per tier, the CI template | Its cases and expected facts, its gold chunks, fixing what the report shows, any threshold above the floor |

I would refuse to centralise **domain truth** (the platform cannot know a correct Legal, HR or Finance answer), **case authoring** (a central team writing cases for 30 applications becomes the bottleneck) and **the ship decision**. Tiers are designed, not built (D22): each has a pass floor and mandatory checks, every application starts in the top tier, and departments can only raise the floor.

## 3. What "good enough to ship" means, and who decides

All of these hold: **no regression** against the last known-good run; pass rate **at or above the tier floor** (90% for the top tier); **no failing out-of-scope case**, because a confident wrong answer is the named risk and is never averaged away (D13); **the judge passes calibration**; and the **set is big enough to mean something** (for example 10 cases with at least 2 out-of-scope).

**The department owner decides, and the tier sets the bar they cannot go below.** I cannot judge whether an application I did not build is right, so I control the bar instead: no owner and no cases, no onboarding. CI enforces the floor, so the platform is never an approval queue.

## 4. Cost and time for thirty applications

Per case: about 2.6 judge calls, **$0.0063**. The assistant's tokens are not visible over HTTP, so I **estimate** $0.0035 per call (Haiku 4.5). About **$0.01 per case**, **$0.29 for a 30-case run**; calibration adds about $0.04.

Assumed monthly cadence per application: 30 nightly full runs ($8.70), 10 CI runs ($2.90), 60 local runs of 10 cases ($6.00), 4 calibrations ($0.16). That is **about $18**, so **about $530 for thirty applications**. Judge calls scale with claims per answer, so plan for **$530 to $1,600**. Two savings are not built: a cheaper judge for Relevance (a third of the calls, and advisory), and stopping judge calls after a deterministic failure (D45).

Cases run one after another: 28 take about 7 minutes and 60 would take about 15. My targets are under 90 seconds locally and under 5 minutes in CI, so **we are over the limit already**. Parallel runs are the first fix; they need care with provider rate limits.

## 5. What breaks first, and what I would build next

1. **Flaky verdicts.** The same model and prompt at temperature 0 passed a case in 2 of 3 runs while I tuned the stub. Teams learn to ignore red builds that go green on a retry. The single re-run of a suspected regression (D14) contains it; majority-of-N is next if the re-run rate is high.
2. **The contract does not fit every application.** The harness needs citations by default; an application that cannot cite turns checks off with the `checks` list (D23).
3. **The judge is calibrated on Usercentrics text.** The bundled pairs say nothing about a Legal or HR judge, so each department supplies its own before its result counts.

**Next, in order:** parallel case runs and early stopping after a deterministic failure; a `retrieved` field and retrieval check (D44), because raising top-k from 3 to 6 on the stub did not fix its failures and the harness could not say why; then sampling real questions and corrections into candidate cases, so sets grow from real failures.

**Not adopted yet:** promptfoo or DeepEval. A runner small enough to know line by line was right for one application; at thirty I would weigh them against maintaining this one.

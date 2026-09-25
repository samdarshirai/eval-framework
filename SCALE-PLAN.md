# Scale plan: from one application to the whole marketplace

One page for the session. It answers the five questions in the brief. The numbers come from one measured run of the Implementation Assistant (2026-09-25, 28 cases): 82 judge calls, 26,461 input and 431 output tokens, **$0.143** and **295 s**. Where I estimate, I say so. `PATTERN.md` is the recipe for a single team, and `grilling-decisions.md` holds the decisions (D#).

## 1. What runs where, and when

| When | What | Against | Purpose | Time |
|---|---|---|---|---|
| **Local**, while editing a prompt | The cases that touch the change (`--case a,b`) or a 10-case subset, with `--skip-calibration` | A candidate instance on the builder's machine | Feedback in about a minute | ~80 s for 10 cases (estimated from the measured ~8 s per case) |
| **CI**, on any change to the prompt, model, config or documents | The full set, with the baseline | A candidate instance started for the run, torn down after | **Blocks the merge** on a regression or an out-of-scope failure | ~5 min for 28 cases today |
| **Nightly** | The full set | A dedicated instance with production's exact prompt, model and config | Catches silent provider or model drift when nobody changed anything | Runs unattended |
| **Weekly, and when the judge model or judge prompt changes** | Judge calibration (7 trap pairs, 20 labeled pairs) | The judge, not the application | Confirms the referee still agrees with a human | ~80 s |

**Before or after a change ships?** Before: local and CI decide whether it ships. After: the nightly run tells us when something moved on its own. **Never against live production** (D32). Eval traffic pollutes usage analytics and cost attribution, production can change mid-run, and it may not allow temperature 0. Eval calls carry an `X-Eval-Run` header so the gateway can exclude them.

## 2. Who owns what

| The platform (this role) owns | Each department owns | What I would refuse to centralise |
|---|---|---|
| The harness and the contract (claims plus citations) | Its test cases and their expected facts | **Domain truth.** The platform cannot know what a correct Legal, HR or Finance answer is. |
| The judge model, its calibration and its cost | The gold chunks and keeping them current | **The ship decision.** It stays with the application owner. |
| Risk tiers and the minimum floor per tier | Any threshold above the tier's floor | **Case authoring.** A central team writing cases for 30 applications becomes the bottleneck and gets them wrong. |
| The CI template, nightly scheduling, the dashboard | Fixing what the report shows | A per-release approval queue. |

**Risk tiers** (designed, not built, D22): 2 or 3 tiers, each with a pass floor and mandatory checks. Every application starts in the top tier. Lowering a tier or dropping a check needs a written reason and platform sign-off once, at onboarding, recorded in the application's config. A change of audience (internal to customer-facing) triggers a re-review. Departments can only raise the floor, never lower it.

## 3. What "good enough to ship" means, and who decides

An application ships when all of these hold:

1. **No regression** against the last known-good run.
2. **The pass rate is at or above the tier's floor** (90% for the top tier).
3. **No failing out-of-scope case.** A confident wrong answer is the risk the brief names, so it is never averaged away (D13).
4. **The judge passes calibration.**
5. **The case set is big enough to mean something**: a tier minimum (for example at least 10 cases with at least 2 out-of-scope), so a green run on 3 easy cases does not count.

**The department owner makes the call. The tier sets the minimum bar they cannot go below.** For an application I did not build, I cannot judge whether its answers are right, and I do not try to. What I control is the bar: no owner and no cases, no onboarding. The platform enforces the floor in CI, so it never becomes an approval queue for 30 teams.

## 4. Cost and time for thirty applications

**Measured per case** (judge only, the calibration run excluded): about 2 judge calls, **$0.0036**, ~8 s. The assistant took 1.8 s per call. Its tokens are not visible over HTTP, so I **estimate** $0.0035 per call (Haiku 4.5, about 2.5k input and 200 output tokens). Together that is about **$0.007 per case**, or **$0.21 for a 30-case run**. Calibration adds about $0.04 and is not needed on every run.

**Assumed cadence per application per month:** a nightly full run (30), 10 CI runs, and 60 local runs of 10 cases.

| Runs | Count | Cost each | Per month |
|---|---|---|---|
| Nightly, full set | 30 | $0.21 | $6.30 |
| CI, full set | 10 | $0.21 | $2.10 |
| Local, 10 cases | 60 | $0.07 | $4.20 |
| Calibration, weekly | 4 | $0.04 | $0.16 |
| **Per application** | | | **about $13** |

**Thirty applications: about $390 a month.** The main uncertainty is how many claims an answer has, because judge calls scale with it. A chattier application costs 2 to 3 times more, so plan for **$400 to $1,200**. Two savings are available: a cheaper judge for Relevance (about a third of the judge calls, and advisory anyway), and skipping the remaining judge calls once a deterministic check has failed (D45). Neither is built.

**Time.** The harness runs cases one after another, so 28 cases take about 5 minutes, and an application with 60 cases would take about 8. People start skipping a check that takes more than a few minutes, so my targets are under 90 seconds locally and under 5 minutes in CI. **We are at the limit already.** Cases are independent, so running them in parallel is the first fix. It is not built, and it will need care with the provider's rate limits.

## 5. What breaks first, and what I would build next

1. **Flaky verdicts.** At temperature 0 the same model and prompt passed a case in 2 of 3 runs while I was tuning the stub. Teams learn to ignore red builds that go green on a retry. The single re-run of a suspected regression (D14) and the judge calibration contain this. I would add majority-of-N runs if the re-run rate is high.
2. **Stale cases.** Documents change under the eval set. The loader fails on a missing gold chunk and warns on a changed one (D21, D48), but nothing catches a case that is merely out of date. Case ownership is what keeps this manageable.
3. **The contract does not fit every application.** The harness needs citations, and it runs every check for every application. An application that cannot cite fails Citation integrity on every case, until the per-application `checks` list exists (D23).
4. **The judge is calibrated on Usercentrics text.** The bundled pairs say nothing about a Legal or HR judge. Each department must supply its own pairs before its result counts.
5. **Time and cost**, as in section 4, once applications have big case sets.
6. **Ownership ambiguity.** Without a named owner, nobody decides whether an application is safe to ship.

**Built next, in this order:**

1. The per-application `checks` list and tier enforcement, so a non-citing application can onboard, and the platform can enforce mandatory checks.
2. Parallel case runs, plus stopping the judge calls after a deterministic failure.
3. The `retrieved` field in the contract and a retrieval check (D44). On the Implementation Assistant, 7 of the 8 failures came from the search missing a document, and the harness could only prove that by replaying the search by hand.
4. Sampling real questions and support corrections from the LLM gateway into candidate cases, so the sets grow from real failures instead of from authors.
5. A dashboard of pass rate, flake rate, run time and cost per application, so a drifting application is visible without opening a report.

**What I would not adopt yet:** promptfoo or DeepEval. A runner small enough to know line by line was the right call for one application. At thirty, I would compare them against the cost of maintaining this one.

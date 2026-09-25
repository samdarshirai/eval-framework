# Evaluation harness for the Usercentrics Implementation Assistant

A small evaluation layer for an AI Hub application, and the pattern for evaluating thirty more. It runs test cases against an assistant over HTTP and checks the answers: whether each expected fact is covered, whether each claim is supported by the source it cites, whether the assistant refuses what it should not answer, and whether a change broke a case that used to pass.

The assistant here is a deliberately thin stub over five public Usercentrics documentation pages. The harness is the point.

## Run it

You need Java 21, Maven, `curl`, and an [OpenRouter](https://openrouter.ai) API key. The stub and the judge both call models through OpenRouter.

```bash
export OPENROUTER_API_KEY=...
./run.sh
```

`run.sh` builds the jars, starts the stub assistant, runs the harness, stops the stub, and exits with the harness's exit code. Any arguments go to the harness (`./run.sh --case ss-zonejs --skip-calibration`). The stub's log is written to `assistant.log`.

The same thing by hand:

```bash
# 1. Build both jars (from the repo root)
mvn -q -DskipTests package

# 2. Start the stub assistant, in its own terminal (listens on 127.0.0.1:8080)
java -jar assistant/target/assistant.jar

# 3. Run the harness
java -jar eval/target/eval.jar
```

You get a summary on the terminal and a full JSON report in `caseResults/`. If the stub is not running, the harness stops at once and tells you how to start it.

A full run of the 28 cases takes about 6 minutes and costs about $0.21 in judge calls (the calibration is about $0.04 of that). The calibration runs on every run by default. To try it fast, run a few cases and skip it:

```bash
java -jar eval/target/eval.jar --case ss-safari-bundle,oos-pricing --skip-calibration
```

**Exit code:** `0` everything passed, `1` the run failed (pass rate under the floor, a failing out-of-scope case, a regression against the baseline, or a failed judge calibration), `2` a setup error such as a bad config or a missing API key.

**Expect red on the default run.** The stub is thin on purpose. The latest full run passed 19 of 28 and exited 1, and `scope.md` explains why, case by case.

To run the tests (no API key needed, the model calls are mocked): `mvn test`.

## Options

Every setting is a key in `eval/config.yaml`. `--<key> <value>` overrides it for one run, and an unknown key is an error.

```bash
java -jar eval/target/eval.jar --endpoint http://localhost:9000/answer   # point at another app
java -jar eval/target/eval.jar --passFloor 0.8
java -jar eval/target/eval.jar --baseline ""                          # switch the regression check off for this run
java -jar eval/target/eval.jar --appType uncited --addChecks Relevance   # an app that cannot cite: fewer checks
java -jar eval/target/eval.jar --case fp-tcf-gettcdata --debug           # one case, with a trace
java -jar eval/target/eval.jar --skip-calibration                        # skip the judge calibration
```

A **baseline** is a previous run's report that later runs are compared against. Normally it is a run you trust. In this repo it is the latest stub run (19 of 28): a reference for change, not a known-good run. `eval/config.yaml` sets `caseResults/baseline.json` by default, so every run includes the regression check. Promote a new one with `cp caseResults/<run id>.json caseResults/baseline.json`. A case that passed in the baseline and fails now fails the run, after one re-run. `eval/config.yaml` documents every key.

### Another team's app, cases and documents

`--config` runs the harness with a team's own config, from any directory. Every relative path in the file resolves against the file's folder.

```bash
java -jar eval/target/eval.jar --config my-team/eval.yaml
```

The Quickstart in `PATTERN.md` walks through this with a small team folder, and the rest of `PATTERN.md` explains how to write the config and the cases.

## What is where

| Path | What |
|---|---|
| `eval/` | The harness. `eval/config.yaml` holds the settings, `eval/cases/` the 28 cases (YAML), `eval/src/.../checks/` one class per check. |
| `assistant/` | The stub assistant: a Spring Boot service with BM25 search and one model call. `eval` never depends on it. |
| `kb/`, `llm/` | Chunking with heading-based IDs, and the OpenRouter client. |
| `docs/` | The five documentation pages the assistant answers from, each tagged with its source URL. |
| `caseResults/` | Timestamped JSON reports, and `baseline.json`. |

## Read next

| If you want to | Read |
|---|---|
| Apply this to your own application in an hour | [`PATTERN.md`](PATTERN.md) |
| See how it scales from one application to thirty | [`SCALE-PLAN.md`](SCALE-PLAN.md) |
| Know what is in, what is out, and why | [`scope.md`](scope.md) |
| Look up a term | [`CONTEXT.md`](CONTEXT.md) |
| See why a design choice was made | [`grilling-decisions.md`](grilling-decisions.md) |

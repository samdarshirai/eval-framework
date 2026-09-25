# Testing `--config` with a separate team folder

Run from the repo root on a build of this branch (`mvn -q -DskipTests package`).

## 1. Create a team folder

```bash
export OPENROUTER_API_KEY=...
mkdir -p /tmp/team-x && cp -r docs /tmp/team-x/docs && cp -r eval/cases /tmp/team-x/cases
```

## 2. Write the team config

`/tmp/team-x/eval.yaml`:

```yaml
endpoint: http://localhost:8080/answer
passFloor: 0.90
judgeModel: anthropic/claude-opus-4.8
categories: [single-source, multi-source, false-premise, out-of-scope, edge-case]
knowledgeBase:
  type: docs
  path: docs          # relative to this file's folder
# Optional keys (paths relative to this file's folder):
# cases: cases
# outputDir: caseResults
# calibration:
#   trapPairs: my-trap-pairs.yaml
```

## 3. Run it from a different directory

```bash
java -jar assistant/target/assistant.jar &
cd /tmp
java -jar <repo>/eval/target/eval.jar --config team-x/eval.yaml
echo $?    # 0 if everything passed
```

Expected: 7 trap pairs run first (bundled in the jar), then the cases pass, and the report lands in `/tmp/team-x/caseResults/`.

## 4. Error and flag checks

```bash
java -jar eval/target/eval.jar --config /nonexistent.yaml   # config file not found, exit 2
java -jar eval/target/eval.jar --config                     # --config needs a value, exit 2
java -jar eval/target/eval.jar --config team-x/eval.yaml --skip-calibration
java -jar eval/target/eval.jar --config team-x/eval.yaml --endpoint http://localhost:9999/answer   # cannot reach, exit 2
```

Stop the stub with `kill %1`.

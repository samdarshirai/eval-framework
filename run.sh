#!/usr/bin/env bash
# One command: build, start the stub assistant, run the harness, stop the stub.
# Extra arguments go to the harness: ./run.sh --case ss-zonejs --skip-calibration
# Exit code is the harness's: 0 passed, 1 failed, 2 setup error.
set -uo pipefail
cd "$(dirname "$0")"

if [ -z "${OPENROUTER_API_KEY:-}" ]; then
  echo "OPENROUTER_API_KEY is not set. Run: export OPENROUTER_API_KEY=..." >&2
  exit 2
fi

mvn -q -DskipTests package || exit 2

java -jar assistant/target/assistant.jar > assistant.log 2>&1 &
stub=$!
trap 'kill "$stub" 2>/dev/null' EXIT

# Any HTTP answer means the stub is up (the harness does the same check).
for _ in $(seq 60); do
  if curl -s -o /dev/null http://127.0.0.1:8080/answer; then break; fi
  if ! kill -0 "$stub" 2>/dev/null; then
    echo "The stub exited. See assistant.log" >&2
    exit 2
  fi
  sleep 1
done
if ! curl -s -o /dev/null http://127.0.0.1:8080/answer; then
  echo "The stub did not answer on port 8080 within 60 s. See assistant.log" >&2
  exit 2
fi

java -jar eval/target/eval.jar "$@"

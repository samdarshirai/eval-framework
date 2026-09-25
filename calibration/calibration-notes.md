# Judge calibration notes

Measured on 2026-09-24 in a live run of `java -jar eval/target/eval.jar` (the date of the measurement, not a version), judge model `anthropic/claude-opus-4.8`, reasoning effort low, temperature 0.

## Groundedness (calibrated, D16)

- Sample: 20 hand-labeled pairs in `eval/src/main/resources/calibration/labeled-sample.yaml` (10 subtly unsupported, 5 plain supported, 5 hard-supported).
- Overall agreement: 20/20 (100%), target at least 90%.
- Unsupported pairs judged supported: 0 of 10, target 0.
- Supported pairs judged unsupported: 0 (counted, no target).
- Prompt changes made to reach the targets: none (SUPPORTS_SYSTEM_PROMPT unchanged since first live run).

## Trap pairs

- All 7 trap pairs in `eval/src/main/resources/calibration/trap-pairs.yaml` passed in the same run.

## Not calibrated in v1

- Coverage by judge (the `agree` and `covering` questions): only the 7 trap pairs guard it. Calibrating it against labeled pairs is the first thing to add.
- Relevance (unit 17, not built yet).

## Limits

- The labels were drafted by the implementer from `docs/` and are pending review by the repo owner. The most debatable label is `hard-gdpr-and-tcf` ("rule" vs "ruleset" wording).
- 20 pairs / 100% is a small sample so it shows the prompt is not obviously broken on this documentation, not a guarantee.
- A new team should add pairs of its own (`calibration.labeledSample` in its config).

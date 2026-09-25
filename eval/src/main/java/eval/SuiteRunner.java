package eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every case once. With a baseline it then re-runs, once, each case that passed in the
 * baseline and failed now (D14, unit 19): the second result replaces the first, so a flake that
 * passes the second time counts as a pass, and a case that fails both times is a regression. Cases
 * that failed in the baseline, and cases the baseline does not know, are never re-run: they are not
 * suspected regressions and a re-run would only cost calls. Out-of-scope cases are never re-run
 * either: a hallucination there fails the run by its own rule (D13), and a lucky second attempt
 * must not clear it, so with a baseline the gate is exactly as strict as without one.
 */
final class SuiteRunner {
  record Outcome(List<CaseResult> results, SuiteReport.Comparison comparison) {}

  private final CaseRunner runner;
  private final DebugLog debug;

  SuiteRunner(CaseRunner runner) {
    this(runner, DebugLog.OFF);
  }

  SuiteRunner(CaseRunner runner, DebugLog debug) {
    this.runner = runner;
    this.debug = debug;
  }

  /** The comparison in the outcome is null when there is no baseline. */
  Outcome run(List<EvalCase> cases, String runId, Baseline baseline) {
    List<CaseResult> results = new ArrayList<>();
    for (EvalCase evalCase : cases) {
      results.add(runner.run(evalCase, runId));
    }
    if (baseline == null) {
      return new Outcome(results, null);
    }
    List<SuiteReport.Comparison.Rerun> reruns = new ArrayList<>();
    for (int index = 0; index < cases.size(); index++) {
      CaseResult firstAttempt = results.get(index);
      if (firstAttempt.passed()
          || !baseline.passed(firstAttempt.id())
          || SuiteReport.OUT_OF_SCOPE.equals(firstAttempt.category())) {
        continue;
      }
      debug.log("re-running " + firstAttempt.id() + ": passed in the baseline, failed now");
      CaseResult secondAttempt = runner.run(cases.get(index), runId);
      results.set(index, secondAttempt);
      reruns.add(new SuiteReport.Comparison.Rerun(firstAttempt.id(), secondAttempt.passed()));
    }
    List<String> improved =
        results.stream()
            .filter(result -> result.passed() && baseline.failed(result.id()))
            .map(CaseResult::id)
            .toList();
    return new Outcome(results, new SuiteReport.Comparison(baseline.name(), reruns, improved));
  }
}

package eval;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs every case once. With a baseline it then re-runs, once, each case that passed in the
 * baseline and failed now (D14, unit 19): the second result replaces the first, so a flake that
 * passes the second time counts as a pass, and a case that fails both times is a regression. Cases
 * that failed in the baseline, and cases the baseline does not know, are never re-run: they are not
 * suspected regressions and a re-run would only cost calls.
 */
final class SuiteRunner {
  record Outcome(List<CaseResult> results, SuiteReport.Comparison comparison) {}

  private final CaseRunner runner;

  SuiteRunner(CaseRunner runner) {
    this.runner = runner;
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
      if (firstAttempt.passed() || !baseline.passed(firstAttempt.id())) {
        continue;
      }
      CaseResult secondAttempt = runner.run(cases.get(index), runId);
      results.set(index, secondAttempt);
      reruns.add(new SuiteReport.Comparison.Rerun(firstAttempt.id(), secondAttempt.passed()));
    }
    return new Outcome(results, new SuiteReport.Comparison(baseline.name(), reruns));
  }
}

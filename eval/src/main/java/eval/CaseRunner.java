package eval;

import eval.checks.CheckResult;
import eval.checks.Registered;
import java.util.ArrayList;
import java.util.List;

/**
 * The core harness logic for one case: ask the assistant, then run every registered check on the
 * answer and build the case's result.
 *
 * <p>The checks come from Checks.registered(kb, judge) (eval/checks/Checks.java), the single list of
 * every check the harness runs, in run order. A check is a small class implementing Check: it takes
 * the eval case, the assistant's answer and the per-case CaseState and returns pass/fail plus a
 * reason (currently Refusal, Citation integrity and Coverage). Each entry is wrapped in Registered
 * with a gating flag: a failing gating check fails the case, an advisory one is only reported.
 * Nothing here names a specific check, so adding one is a new class plus one line in Checks.
 *
 * <p>A check that throws a RuntimeException does not abort the run: it is recorded as a failed
 * outcome and the remaining checks still run. Each case gets a fresh CaseState, so findings never
 * leak from one case to the next.
 */
final class CaseRunner {
  private final Assistant assistant;
  private final List<Registered> checks;

  CaseRunner(Assistant assistant, List<Registered> checks) {
    this.assistant = assistant;
    this.checks = checks;
  }

  CaseResult run(EvalCase evalCase, String runId) {
    Answer answer;
    try {
      answer = assistant.ask(evalCase.question(), runId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return CaseResult.failed(evalCase, "interrupted");
    } catch (Exception e) {
      return CaseResult.failed(evalCase, e.getMessage());
    }
    List<CheckOutcome> outcomes = new ArrayList<>();
    boolean passed = true;
    CaseState state = new CaseState();
    for (Registered registered : checks) {
      CheckResult checkResult;
      try {
        checkResult = registered.check().run(evalCase, answer, state);
      } catch (RuntimeException e) {
        checkResult =
            CheckResult.fail(
                "check error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      }
      outcomes.add(
          new CheckOutcome(registered.check().name(), checkResult.passed(), checkResult.reason()));
      if (registered.gating() && !checkResult.passed()) {
        passed = false;
      }
    }
    return CaseResult.answered(evalCase, passed, answer, outcomes);
  }
}

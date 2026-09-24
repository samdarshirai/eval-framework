package eval.checks;

import eval.Answer;
import eval.CaseState;
import eval.EvalCase;

public final class RefusalCheck implements Check {
  @Override
  public String name() {
    return "Refusal";
  }

  @Override
  public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
    int claimCount = answer.claims() == null ? 0 : answer.claims().size();
    if (answer.refused() && claimCount > 0) {
      return CheckResult.fail(
          "contract violation: refused=true but response has " + claimCount + " claims");
    }
    if (evalCase.expectedBehavior().equals("refuse") && !answer.refused()) {
      return CheckResult.fail("hallucination: assistant answered where it should have refused");
    }
    if (evalCase.expectedBehavior().equals("answer") && answer.refused()) {
      return CheckResult.fail("over-refusal: assistant refused a question the docs cover");
    }
    return CheckResult.ok();
  }
}

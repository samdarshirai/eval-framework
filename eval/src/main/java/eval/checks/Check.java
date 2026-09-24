package eval.checks;

import eval.Answer;
import eval.CaseState;
import eval.EvalCase;

public interface Check {
  String name();

  CheckResult run(EvalCase evalCase, Answer answer, CaseState state);
}

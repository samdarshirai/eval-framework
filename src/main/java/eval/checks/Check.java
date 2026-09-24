package eval.checks;

import eval.Answer;
import eval.EvalCase;

public interface Check {
    String name();
    CheckResult run(EvalCase c, Answer a);
}

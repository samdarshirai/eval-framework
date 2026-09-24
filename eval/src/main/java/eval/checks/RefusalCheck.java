package eval.checks;

import eval.Answer;
import eval.EvalCase;

public final class RefusalCheck implements Check {
    @Override public String name() { return "Refusal"; }

    @Override public CheckResult run(EvalCase c, Answer a) {
        int n = a.claims() == null ? 0 : a.claims().size();
        if (a.refused() && n > 0) return CheckResult.fail("contract violation: refused=true but response has " + n + " claims");
        if (c.expectedBehavior().equals("refuse") && !a.refused())
            return CheckResult.fail("hallucination: assistant answered where it should have refused");
        if (c.expectedBehavior().equals("answer") && a.refused())
            return CheckResult.fail("over-refusal: assistant refused a question the docs cover");
        return CheckResult.ok();
    }
}

package eval;

import java.util.List;

/** Expected (behavior, facts) and actual (answer, check outcomes) side by side, so a failure can be debugged from the report alone. */
public record CaseResult(String id, String question, String category, String subtype, String expectedBehavior,
                         List<ExpectedFact> expectedFacts, boolean passed, String error, Answer answer,
                         List<CheckOutcome> checks) {}

package eval;

import java.util.List;

public record CaseResult(String id, String category, String subtype, String expectedBehavior, boolean passed,
                         String error, Answer answer, List<CheckOutcome> checks) {}

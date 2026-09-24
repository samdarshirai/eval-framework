package eval;

import java.util.List;

/**
 * One case in the report. {@code expected} and {@code actual} share the response shape, so they read side by side:
 * expected claims are the case's facts (claim = fact text, citations = its gold chunks, any-of, plus keywords).
 */
public record CaseResult(String id, String question, String category, String subtype, Expected expected,
                         boolean passed, String error, Answer actual, List<CheckOutcome> checks) {

    /** The assistant produced no answer: the case fails with the given error and no checks. */
    static CaseResult failed(EvalCase evalCase, String error) {
        return new CaseResult(evalCase.id(), evalCase.question(), evalCase.category(), evalCase.subtype(),
            expected(evalCase), false, error, null, List.of());
    }

    static CaseResult answered(EvalCase evalCase, boolean passed, Answer answer, List<CheckOutcome> checks) {
        return new CaseResult(evalCase.id(), evalCase.question(), evalCase.category(), evalCase.subtype(),
            expected(evalCase), passed, null, answer, checks);
    }

    /** The case's expectation in the assistant's response shape, for the report. */
    private static Expected expected(EvalCase evalCase) {
        return new Expected(
            evalCase.expectedBehavior().equals("refuse"),
            evalCase.facts().stream()
                .map(fact -> new Expected.ExpectedClaim(fact.fact(), fact.chunks(), fact.keywords()))
                .toList());
    }
}

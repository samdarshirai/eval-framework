package eval;

import java.util.List;

/**
 * One case in the report. {@code expected} and {@code actual} share the response shape, so they read side by side:
 * expected claims are the case's facts (claim = fact text, citations = its gold chunks, any-of).
 */
public record CaseResult(String id, String question, String category, String subtype, Answer expected,
                         boolean passed, String error, Answer actual, List<CheckOutcome> checks) {}

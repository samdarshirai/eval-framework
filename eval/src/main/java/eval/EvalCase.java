package eval;

import java.util.List;

public record EvalCase(String id, String question, String category, String subtype, String expectedBehavior,
                       List<ExpectedFact> facts, String source, String owner, String added) {}

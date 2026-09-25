package eval;

import java.util.List;

/**
 * One eval case. {@code confirmedHash} is the optional fingerprint of the gold chunks the case was
 * last confirmed against (see {@link CaseHash}); null when the case was never stamped.
 */
public record EvalCase(
    String id,
    String question,
    String category,
    String subtype,
    String expectedBehavior,
    List<ExpectedFact> facts,
    String source,
    String owner,
    String added,
    String confirmedHash) {

  public EvalCase(
      String id,
      String question,
      String category,
      String subtype,
      String expectedBehavior,
      List<ExpectedFact> facts,
      String source,
      String owner,
      String added) {
    this(id, question, category, subtype, expectedBehavior, facts, source, owner, added, null);
  }
}

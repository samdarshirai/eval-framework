package eval.checks;

import eval.*;
import java.util.*;

/**
 * Is each expected fact covered by a claim? (D24) A fact with keywords needs a claim containing all
 * of them (no LLM call when none does), and each such candidate is confirmed by the judge, because
 * a keyword hit cannot see negation. Covered facts leave their confirmed claims in the CaseState
 * for Groundedness and Source.
 */
public final class CoverageCheck implements Check {
  private final Judge judge;

  public CoverageCheck(Judge judge) {
    this.judge = judge;
  }

  @Override
  public String name() {
    return "Coverage";
  }

  @Override
  public CheckResult run(EvalCase c, Answer a, CaseState state) {
    List<Claim> claims = a.claims() == null ? List.of() : a.claims();
    List<String> uncovered = new ArrayList<>();
    for (ExpectedFact fact : c.facts()) {
      List<Claim> covering = coveringClaims(fact, claims);
      if (covering.isEmpty()) {
        uncovered.add(fact.fact());
      } else {
        state.setCovering(fact, covering);
      }
    }
    if (uncovered.isEmpty()) {
      return CheckResult.ok();
    }
    return CheckResult.fail(
        "not covered: " + String.join("; ", uncovered.stream().map(f -> "\"" + f + "\"").toList()));
  }

  private List<Claim> coveringClaims(ExpectedFact fact, List<Claim> claims) {
    if (claims.isEmpty()) {
      return List.of();
    }
    if (fact.keywords().isEmpty()) {
      return judge.covering(fact.fact(), claims).stream().map(claims::get).toList();
    }
    List<Claim> covering = new ArrayList<>();
    for (Claim candidate : claims) {
      if (containsAll(candidate.claim(), fact.keywords())
          && judge.agree(fact.fact(), candidate.claim())) {
        covering.add(candidate);
      }
    }
    return covering;
  }

  private static boolean containsAll(String text, List<String> keywords) {
    String lower = text.toLowerCase();
    return keywords.stream().allMatch(k -> lower.contains(k.toLowerCase()));
  }
}

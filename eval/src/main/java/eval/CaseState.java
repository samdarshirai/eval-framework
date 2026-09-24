package eval;

import java.util.*;

/**
 * Scratch space for one case: checks earlier in the list leave findings for later ones. Not part of
 * the report.
 */
public final class CaseState {
  private final Map<ExpectedFact, List<Claim>> covering = new LinkedHashMap<>();

  public void setCovering(ExpectedFact fact, List<Claim> claims) {
    covering.put(fact, List.copyOf(claims));
  }

  public List<Claim> covering(ExpectedFact fact) {
    return covering.getOrDefault(fact, List.of());
  }
}

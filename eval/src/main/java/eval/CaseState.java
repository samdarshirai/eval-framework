package eval;

import java.util.*;

/**
 * Scratch space shared by all the checks for one eval case: {@code CaseRunner} creates one per case
 * and passes the same instance to every check, in list order, so earlier checks leave findings for
 * later ones. Today only Coverage writes (the covering claims per fact), and only Source reads,
 * so Source must run after Coverage (Checks enforces it). Not part of the report.
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

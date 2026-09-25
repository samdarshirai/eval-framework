package eval.checks;

import eval.Judge;
import eval.KnowledgeBase;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The single registration list. Add a check: one class plus one line here. Order is run order. */
public final class Checks {
  /** A check that reads what another check stored in CaseState: it cannot run without it (D23). */
  private static final Map<String, String> REQUIRES = Map.of("Source", "Coverage");

  /**
   * App types (D23): the least checks each type must run. An app adds to its type with {@code
   * addChecks} and cannot remove from it; lowering a floor is a change here, i.e. a platform PR.
   */
  private static final Map<String, List<String>> APP_TYPES =
      Map.of(
          "cited", List.of("Refusal", "Citation integrity", "Coverage", "Groundedness", "Source"),
          "uncited", List.of("Refusal", "Coverage"),
          "smoke", List.of("Refusal", "Citation integrity"));

  /**
   * The check names to run: the app type's floor plus {@code addChecks}, or the explicit {@code
   * checks} list, or null (all six) when none is set. Setting both, an unknown app type, or {@code
   * addChecks} without an app type throws IllegalArgumentException.
   */
  public static List<String> requested(String appType, List<String> addChecks, List<String> checks) {
    if (appType == null) {
      if (addChecks != null) {
        throw new IllegalArgumentException("'addChecks' needs an 'appType'");
      }
      return checks;
    }
    if (checks != null) {
      throw new IllegalArgumentException("set 'appType' or 'checks', not both");
    }
    List<String> floor =
        APP_TYPES.entrySet().stream()
            .filter(entry -> entry.getKey().equalsIgnoreCase(appType.trim()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "unknown appType '"
                            + appType
                            + "' (known: "
                            + String.join(", ", new java.util.TreeSet<>(APP_TYPES.keySet()))
                            + ")"));
    List<String> requested = new java.util.ArrayList<>(floor);
    if (addChecks != null) {
      requested.addAll(addChecks);
    }
    return requested;
  }

  public static List<Registered> registered(KnowledgeBase kb, Judge judge) {
    return List.of(
        new Registered(new RefusalCheck(), true),
        new Registered(new CitationIntegrityCheck(kb), true),
        new Registered(new CoverageCheck(judge), true),
        new Registered(new GroundednessCheck(kb, judge), true),
        new Registered(new SourceCheck(), true),
        new Registered(new RelevanceCheck(judge), false));
  }

  /**
   * The registered checks named in {@code enabled} (case-insensitive), in registration order; all
   * of them when {@code enabled} is null. A check's required checks are added even when not named.
   * Throws IllegalArgumentException for an unknown name, an empty list, or no gating check.
   */
  public static List<Registered> registered(KnowledgeBase kb, Judge judge, List<String> enabled) {
    List<Registered> all = registered(kb, judge);
    if (enabled == null) {
      return all;
    }
    if (enabled.isEmpty()) {
      throw new IllegalArgumentException("'checks' must name at least one check");
    }
    List<String> known = all.stream().map(registered -> registered.check().name()).toList();
    Set<String> wanted = new LinkedHashSet<>();
    List<String> unknown = new java.util.ArrayList<>();
    for (String name : enabled) {
      String match =
          known.stream().filter(k -> k.equalsIgnoreCase(name.trim())).findFirst().orElse(null);
      if (match == null) {
        unknown.add(name);
      } else {
        wanted.add(match);
      }
    }
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "unknown check(s): "
              + String.join(", ", unknown)
              + " (known: "
              + String.join(", ", known)
              + ")");
    }
    // A check that needs another pulls it in: dependent checks run all or none (D23).
    REQUIRES.forEach(
        (check, needed) -> {
          if (wanted.contains(check)) {
            wanted.add(needed);
          }
        });
    List<Registered> selected =
        all.stream().filter(registered -> wanted.contains(registered.check().name())).toList();
    if (selected.stream().noneMatch(Registered::gating)) {
      throw new IllegalArgumentException(
          "'checks' needs at least one gating check, otherwise every case passes");
    }
    return selected;
  }
}

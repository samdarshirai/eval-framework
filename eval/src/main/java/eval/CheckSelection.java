package eval;

import eval.checks.Checks;
import eval.checks.Registered;
import java.io.PrintStream;
import java.util.List;

/**
 * Which checks this run executes (D54): resolves the config's {@code appType}, {@code addChecks}
 * and {@code checks}, validates the result against the cases, and tells the console what is off,
 * what was pulled in and whether the baseline ran a different set. Throws IllegalArgumentException
 * for anything invalid, so the run exits 2 before any call.
 */
record CheckSelection(List<Registered> checks) {

  static CheckSelection resolve(
      EvalConfig config,
      KnowledgeBase kb,
      Judge judge,
      List<EvalCase> cases,
      Baseline baseline,
      PrintStream out) {
    List<String> requested = Checks.requested(config.appType(), config.addChecks(), config.checks());
    CheckSelection selection = new CheckSelection(Checks.registered(kb, judge, requested));
    List<String> names = selection.names();
    if (!names.contains("Refusal")
        && cases.stream().anyMatch(c -> SuiteReport.OUT_OF_SCOPE.equals(c.category()))) {
      throw new IllegalArgumentException(
          "'Refusal' is required in 'checks': out-of-scope cases pass only through it");
    }
    if (requested != null) {
      List<String> pulledIn =
          names.stream().filter(name -> requested.stream().noneMatch(name::equalsIgnoreCase)).toList();
      if (!pulledIn.isEmpty()) {
        out.println("Checks added because another check needs them: " + String.join(", ", pulledIn));
      }
    }
    List<String> off =
        Checks.registered(kb, judge).stream()
            .map(registered -> registered.check().name())
            .filter(name -> !names.contains(name))
            .toList();
    if (!off.isEmpty()) {
      out.println("Checks off: " + String.join(", ", off));
    }
    String warning = baseline == null ? null : baseline.checkSetWarning(names);
    if (warning != null) {
      out.println("WARNING: " + warning);
    }
    return selection;
  }

  List<String> names() {
    return checks.stream().map(registered -> registered.check().name()).toList();
  }
}

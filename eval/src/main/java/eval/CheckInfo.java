package eval;

import eval.checks.Registered;
import java.util.List;

/** A registered check as it ran in this suite; the same for every case. */
public record CheckInfo(String name, boolean gating) {
  public static List<CheckInfo> of(List<Registered> checks) {
    return checks.stream()
        .map(registered -> new CheckInfo(registered.check().name(), registered.gating()))
        .toList();
  }
}

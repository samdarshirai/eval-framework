package eval.calibration;

import eval.Claim;
import eval.Judge;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/** Runs the negation traps against the judge (D24) and returns one result per pair. */
public final class TrapPairs {
  /** {@code detail} is null when the judge got the pair right. */
  public record TrapResult(String name, boolean passed, String detail) {}

  /** A judge that throws on a pair counts as wrong on it. */
  public static List<TrapResult> run(Path file, Judge judge) throws java.io.IOException {
    List<Map<String, Object>> pairs = new Yaml().load(Files.readString(file));
    List<TrapResult> results = new ArrayList<>();
    for (Map<String, Object> pair : pairs) {
      String problem; // null means the judge got it right
      try {
        problem = pair.containsKey("claims") ? checkCovering(pair, judge) : checkAgree(pair, judge);
      } catch (IllegalStateException e) {
        problem = "error: " + e.getMessage();
      }
      results.add(new TrapResult((String) pair.get("name"), problem == null, problem));
    }
    return results;
  }

  private static String checkAgree(Map<String, Object> pair, Judge judge) {
    boolean expected = (Boolean) pair.get("agree");
    boolean actual = judge.agree((String) pair.get("fact"), (String) pair.get("claim"));
    return expected == actual ? null : "expected " + (expected ? "YES" : "NO");
  }

  @SuppressWarnings("unchecked")
  private static String checkCovering(Map<String, Object> pair, Judge judge) {
    List<Map<String, Object>> claimEntries = (List<Map<String, Object>>) pair.get("claims");
    List<Claim> claims =
        claimEntries.stream()
            .map(entry -> new Claim((String) entry.get("text"), List.of("x#y")))
            .toList(); // citations are unused by the judge
    Set<Integer> expected = new TreeSet<>();
    for (int i = 0; i < claimEntries.size(); i++) {
      if ((Boolean) claimEntries.get(i).get("states")) {
        expected.add(i + 1);
      }
    }
    Set<Integer> actual = new TreeSet<>();
    for (int index : judge.covering((String) pair.get("fact"), claims)) {
      actual.add(index + 1);
    }
    return expected.equals(actual)
        ? null
        : "expected claims " + expected + ", judge said " + actual;
  }
}

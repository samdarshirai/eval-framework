package eval.calibration;

import eval.Claim;
import eval.Judge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/**
 * Asks the judge trick questions whose answer we already know (D24) and returns one result per
 * pair. A trap is a fact and a claim that looks like it agrees but does not (fact "Safari 14 or
 * later is supported", claim "All Safari versions except 14 are supported"): the judge must say no.
 * A few correct rewordings are mixed in, so a judge that always says no also fails.
 */
public final class TrapPairs {
  /** {@code detail} is null when the judge got the pair right. */
  public record TrapResult(String name, boolean passed, String detail) {}

  private static final String BUNDLED = "/calibration/trap-pairs.yaml";

  /** Runs the trap pairs shipped inside the jar. */
  public static List<TrapResult> runBundled(Judge judge) throws java.io.IOException {
    try (Reader reader =
        new InputStreamReader(
            TrapPairs.class.getResourceAsStream(BUNDLED), StandardCharsets.UTF_8)) {
      return run(reader, judge);
    }
  }

  public static List<TrapResult> run(Path file, Judge judge) throws java.io.IOException {
    try (Reader reader = Files.newBufferedReader(file)) {
      return run(reader, judge);
    }
  }

  /** A judge that throws on a pair counts as wrong on it. */
  public static List<TrapResult> run(Reader source, Judge judge) {
    List<Map<String, Object>> pairs = new Yaml().load(source);
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

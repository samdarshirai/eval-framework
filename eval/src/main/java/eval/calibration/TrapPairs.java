package eval.calibration;

import eval.Claim;
import eval.Judge;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;
import llm.OpenRouterLlm;
import org.yaml.snakeyaml.Yaml;

/**
 * Runs the negation traps against the judge and reports every miss (D24). Run: java -cp
 * eval/target/eval.jar eval.calibration.TrapPairs
 */
public final class TrapPairs {
  public static void main(String[] args) throws Exception {
    Map<String, Object> cfg = new Yaml().load(Files.readString(Path.of("eval/config.yaml")));
    Judge judge = new Judge(OpenRouterLlm.fromEnv((String) cfg.get("judgeModel"), "low"));
    System.exit(run(Path.of("calibration/trap-pairs.yaml"), judge, System.out) == 0 ? 0 : 1);
  }

  /**
   * Returns the number of pairs the judge got wrong; a judge that throws on a pair counts as wrong
   * on it.
   */
  public static int run(Path file, Judge judge, PrintStream out) throws java.io.IOException {
    List<Map<String, Object>> pairs = new Yaml().load(Files.readString(file));
    int misses = 0;
    for (Map<String, Object> p : pairs) {
      String problem; // null means the judge got it right
      try {
        problem = p.containsKey("claims") ? checkCovering(p, judge) : checkAgree(p, judge);
      } catch (IllegalStateException e) {
        problem = "error: " + e.getMessage();
      }
      if (problem != null) {
        misses++;
      }
      out.println(
          problem == null
              ? "ok   " + p.get("name")
              : "MISS " + p.get("name") + " (" + problem + ")");
    }
    out.println(misses + " miss(es) out of " + pairs.size());
    return misses;
  }

  private static String checkAgree(Map<String, Object> p, Judge judge) {
    boolean expected = (Boolean) p.get("agree");
    boolean actual = judge.agree((String) p.get("fact"), (String) p.get("claim"));
    return expected == actual ? null : "expected " + (expected ? "YES" : "NO");
  }

  @SuppressWarnings("unchecked")
  private static String checkCovering(Map<String, Object> p, Judge judge) {
    List<Map<String, Object>> raw = (List<Map<String, Object>>) p.get("claims");
    List<Claim> claims =
        raw.stream()
            .map(c -> new Claim((String) c.get("text"), List.of("x#y")))
            .toList(); // citations are unused by the judge
    Set<Integer> expected = new TreeSet<>();
    for (int i = 0; i < raw.size(); i++) {
      if ((Boolean) raw.get(i).get("states")) {
        expected.add(i + 1);
      }
    }
    Set<Integer> actual = new TreeSet<>();
    for (int index : judge.covering((String) p.get("fact"), claims)) {
      actual.add(index + 1);
    }
    return expected.equals(actual)
        ? null
        : "expected claims " + expected + ", judge said " + actual;
  }
}

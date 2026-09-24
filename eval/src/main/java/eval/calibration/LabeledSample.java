package eval.calibration;

import com.fasterxml.jackson.annotation.JsonProperty;
import eval.Judge;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

/**
 * Measures the Groundedness judge against hand-labeled claim and passage pairs (D16). The two kinds
 * of miss are not equal: a false "supported" lets a wrong claim through silently, so it fails the
 * run; a false "unsupported" only fails a good answer, so it is counted and reported, not gated.
 */
public final class LabeledSample {
  private static final String BUNDLED = "/calibration/labeled-sample.yaml";

  /** {@code judged} is null when the judge threw or answered unclearly: never an agreement. */
  public record PairResult(
      String name, String kind, boolean labeledSupported, Boolean judged, String detail) {
    @JsonProperty("agreed")
    public boolean agreed() {
      return judged != null && judged == labeledSupported;
    }
  }

  public record Result(List<PairResult> pairs) {
    public static final Result NONE = new Result(List.of());

    public long agreed() {
      return pairs.stream().filter(PairResult::agreed).count();
    }

    @JsonProperty("agreement")
    public double agreement() {
      return pairs.isEmpty() ? 1.0 : agreed() / (double) pairs.size();
    }

    @JsonProperty("unsupportedPairs")
    public long unsupportedPairs() {
      return pairs.stream().filter(pair -> !pair.labeledSupported()).count();
    }

    /** Labeled unsupported, judged supported: the miss that lets a wrong claim through. */
    @JsonProperty("falseSupported")
    public long falseSupported() {
      return pairs.stream()
          .filter(pair -> !pair.labeledSupported() && Boolean.TRUE.equals(pair.judged()))
          .count();
    }

    @JsonProperty("falseUnsupported")
    public long falseUnsupported() {
      return pairs.stream()
          .filter(pair -> pair.labeledSupported() && Boolean.FALSE.equals(pair.judged()))
          .count();
    }

    /** At least 90% agreement; integer maths so 18 of 20 is exactly on the line. */
    public boolean agreementMet() {
      return agreed() * 10 >= pairs.size() * 9L;
    }
  }

  /** Runs the sample shipped inside the jar. */
  public static Result runBundled(Judge judge) throws IOException {
    try (Reader reader =
        new InputStreamReader(
            LabeledSample.class.getResourceAsStream(BUNDLED), StandardCharsets.UTF_8)) {
      return run(reader, judge);
    }
  }

  public static Result run(Path file, Judge judge) throws IOException {
    try (Reader reader = Files.newBufferedReader(file)) {
      return run(reader, judge);
    }
  }

  /** A judge that throws on a pair counts as not agreeing on it. */
  public static Result run(Reader source, Judge judge) {
    List<Map<String, Object>> entries = new Yaml().load(source);
    List<PairResult> results = new ArrayList<>();
    for (Map<String, Object> entry : entries == null ? List.<Map<String, Object>>of() : entries) {
      boolean labeledSupported = (Boolean) entry.get("supported");
      Boolean judged;
      String detail = null;
      try {
        judged = judge.supports((String) entry.get("claim"), (String) entry.get("passage"));
        if (judged != labeledSupported) {
          detail = "labeled " + word(labeledSupported) + ", judge said " + word(judged);
        }
      } catch (IllegalStateException e) {
        judged = null;
        detail = "error: " + e.getMessage();
      }
      results.add(
          new PairResult(
              (String) entry.get("name"),
              (String) entry.get("kind"),
              labeledSupported,
              judged,
              detail));
    }
    return new Result(results);
  }

  private static String word(boolean supported) {
    return supported ? "SUPPORTED" : "UNSUPPORTED";
  }
}

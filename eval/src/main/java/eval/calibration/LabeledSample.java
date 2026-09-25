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

  /**
   * {@code expectedSupported} is the hand label; {@code actualSupported} is what the judge said,
   * null when it threw or answered unclearly: never an agreement.
   */
  public record PairResult(
      String name, String kind, boolean expectedSupported, Boolean actualSupported, String detail) {
    @JsonProperty("agreed")
    public boolean agreed() {
      return actualSupported != null && actualSupported == expectedSupported;
    }
  }

  public record Result(List<PairResult> pairs) {
    public static final Result NONE = new Result(List.of());

    @JsonProperty("agreed")
    public long agreed() {
      return pairs.stream().filter(PairResult::agreed).count();
    }

    /** Pairs where the judge threw or answered unclearly: each fails the run. */
    @JsonProperty("errors")
    public long errors() {
      return pairs.stream().filter(pair -> pair.actualSupported() == null).count();
    }

    @JsonProperty("agreement")
    public double agreement() {
      return pairs.isEmpty() ? 1.0 : agreed() / (double) pairs.size();
    }

    @JsonProperty("unsupportedPairs")
    public long unsupportedPairs() {
      return pairs.stream().filter(pair -> !pair.expectedSupported()).count();
    }

    /** Labeled unsupported, actualSupported supported: the miss that lets a wrong claim through. */
    @JsonProperty("falseSupported")
    public long falseSupported() {
      return pairs.stream()
          .filter(pair -> !pair.expectedSupported() && Boolean.TRUE.equals(pair.actualSupported()))
          .count();
    }

    @JsonProperty("falseUnsupported")
    public long falseUnsupported() {
      return pairs.stream()
          .filter(pair -> pair.expectedSupported() && Boolean.FALSE.equals(pair.actualSupported()))
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

  /**
   * Reads either a plain list of pairs or a map with a {@code pairs} list (and optionally a {@code
   * passages} section of anchors to reuse). A judge that throws on a pair counts as not agreeing on
   * it.
   */
  public static Result run(Reader source, Judge judge) {
    Object loaded = new Yaml().load(source);
    Object list = loaded instanceof Map<?, ?> map ? map.get("pairs") : loaded;
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> entries = list == null ? List.of() : (List<Map<String, Object>>) list;
    List<PairResult> results = new ArrayList<>();
    for (Map<String, Object> entry : entries) {
      boolean expectedSupported = (Boolean) entry.get("supported");
      Boolean actualSupported;
      String detail = null;
      try {
        actualSupported =
            judge.supports((String) entry.get("claim"), (String) entry.get("passage"));
        if (actualSupported != expectedSupported) {
          detail = "labeled " + word(expectedSupported) + ", judge said " + word(actualSupported);
        }
      } catch (IllegalStateException e) {
        actualSupported = null;
        detail = "error: " + e.getMessage();
      }
      results.add(
          new PairResult(
              (String) entry.get("name"),
              (String) entry.get("kind"),
              expectedSupported,
              actualSupported,
              detail));
    }
    return new Result(results);
  }

  private static String word(boolean supported) {
    return supported ? "SUPPORTED" : "UNSUPPORTED";
  }
}

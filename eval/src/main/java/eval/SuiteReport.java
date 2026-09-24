package eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import eval.calibration.LabeledSample;
import eval.calibration.TrapPairs.TrapResult;
import java.util.*;

public record SuiteReport(
    String runId,
    String endpoint,
    double passFloor,
    List<CheckInfo> checks,
    Calibration calibration,
    List<CaseResult> cases) {
  /** Judge trap pairs and the Groundedness sample: {@code ran} false means --skip-calibration. */
  public record Calibration(
      boolean ran, List<TrapResult> pairs, LabeledSample.Result groundedness) {
    public static final Calibration SKIPPED =
        new Calibration(false, List.of(), LabeledSample.Result.NONE);

    public Calibration(boolean ran, List<TrapResult> pairs) {
      this(ran, pairs, LabeledSample.Result.NONE);
    }

    @JsonProperty("misses")
    public long misses() {
      return pairs.stream().filter(pair -> !pair.passed()).count();
    }
  }

  public static final String OUT_OF_SCOPE = "out-of-scope";

  /** Whether the named check gates a case; unknown names count as gating. */
  public boolean isGating(String check) {
    return checks.stream()
        .filter(c -> c.name().equals(check))
        .findFirst()
        .map(CheckInfo::gating)
        .orElse(true);
  }

  public long passed() {
    return cases.stream().filter(CaseResult::passed).count();
  }

  @JsonProperty("passRate")
  public double passRate() {
    return cases.isEmpty() ? 0 : passed() / (double) cases.size();
  }

  @JsonProperty("exitReasons")
  public List<String> exitReasons() {
    List<String> reasons = new ArrayList<>();
    if (passRate() < passFloor)
      reasons.add(
          String.format(
              "pass rate %.1f%% is below floor %.1f%%", passRate() * 100, passFloor * 100));
    List<String> oos =
        cases.stream()
            .filter(c -> !c.passed() && OUT_OF_SCOPE.equals(c.category()))
            .map(CaseResult::id)
            .toList();
    if (!oos.isEmpty()) reasons.add("out-of-scope case failed: " + String.join(", ", oos));
    if (calibration.misses() > 0)
      reasons.add("judge calibration: " + calibration.misses() + " trap pair miss(es)");
    LabeledSample.Result sample = calibration.groundedness();
    if (sample.falseSupported() > 0) {
      reasons.add(
          "groundedness calibration: "
              + sample.falseSupported()
              + " of "
              + sample.unsupportedPairs()
              + " unsupported pair(s) judged supported");
    }
    if (!sample.agreementMet()) {
      reasons.add(
          String.format(
              "groundedness calibration: agreement %.1f%% is below the 90%% target",
              sample.agreement() * 100));
    }
    return reasons;
  }

  public int exitCode() {
    return exitReasons().isEmpty() ? 0 : 1;
  }
}

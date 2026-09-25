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
    List<CaseResult> cases,
    Comparison baseline) {

  public SuiteReport(
      String runId,
      String endpoint,
      double passFloor,
      List<CheckInfo> checks,
      Calibration calibration,
      List<CaseResult> cases) {
    this(runId, endpoint, passFloor, checks, calibration, cases, null);
  }

  /**
   * The run compared with a baseline (unit 18): every case that passed there and failed on the
   * first attempt was re-run once (unit 19). {@code passedOnRerun} false means it failed both
   * attempts, which is a regression. {@code improved} lists the cases that failed in the baseline
   * and pass now: worth a new baseline, never a reason to change the exit code.
   */
  public record Comparison(String file, List<Rerun> reruns, List<String> improved) {
    public Comparison(String file, List<Rerun> reruns) {
      this(file, reruns, List.of());
    }

    public record Rerun(String id, boolean passedOnRerun) {}

    @JsonProperty("regressions")
    public List<String> regressions() {
      return reruns.stream().filter(rerun -> !rerun.passedOnRerun()).map(Rerun::id).toList();
    }
  }

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
    if (passRate() < passFloor) {
      reasons.add(
          String.format(
              "pass rate %.1f%% is below floor %.1f%%", passRate() * 100, passFloor * 100));
    }
    List<String> oos =
        cases.stream()
            .filter(c -> !c.passed() && OUT_OF_SCOPE.equals(c.category()))
            .map(CaseResult::id)
            .toList();
    if (!oos.isEmpty()) {
      reasons.add("out-of-scope case failed: " + String.join(", ", oos));
    }
    if (baseline != null && !baseline.regressions().isEmpty()) {
      reasons.add(
          "regression vs baseline ("
              + baseline.file()
              + "): "
              + String.join(", ", baseline.regressions()));
    }
    if (calibration.misses() > 0) {
      reasons.add("judge calibration: " + calibration.misses() + " trap pair miss(es)");
    }
    LabeledSample.Result sample = calibration.groundedness();
    if (sample.falseSupported() > 0) {
      reasons.add(
          "groundedness calibration: "
              + sample.falseSupported()
              + " of "
              + sample.unsupportedPairs()
              + " unsupported pair(s) judged supported");
    }
    if (sample.errors() > 0) {
      reasons.add(
          "groundedness calibration: "
              + sample.errors()
              + " pair(s) errored (the judge gave no usable answer)");
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

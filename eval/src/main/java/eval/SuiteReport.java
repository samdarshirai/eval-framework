package eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import eval.calibration.LabeledSample;
import eval.calibration.TrapPairs.TrapResult;
import java.util.*;
import java.util.function.Function;

public record SuiteReport(
    String runId,
    String endpoint,
    double passFloor,
    List<CheckInfo> checks,
    Calibration calibration,
    List<CaseResult> cases,
    Comparison baseline,
    Usage usage) {

  public SuiteReport(
      String runId,
      String endpoint,
      double passFloor,
      List<CheckInfo> checks,
      Calibration calibration,
      List<CaseResult> cases,
      Comparison baseline) {
    this(runId, endpoint, passFloor, checks, calibration, cases, baseline, null);
  }

  public SuiteReport(
      String runId,
      String endpoint,
      double passFloor,
      List<CheckInfo> checks,
      Calibration calibration,
      List<CaseResult> cases) {
    this(runId, endpoint, passFloor, checks, calibration, cases, null, null);
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

  /**
   * What the run measured (D17). The judge is metered through its {@code Llm}; the assistant is a
   * black box over HTTP (D28), so only its calls and time are known, never its tokens. {@code
   * judgeCostUsd} is tokens times the configured prices, null when none are configured.
   */
  public record Usage(
      long wallClockMillis,
      int assistantCalls,
      long assistantMillis,
      List<CheckUsage> byCheck,
      Double judgeCostUsd) {
    public record CheckUsage(String check, int calls, long promptTokens, long completionTokens) {}

    @JsonProperty("judgeCalls")
    public int judgeCalls() {
      return byCheck.stream().mapToInt(CheckUsage::calls).sum();
    }

    @JsonProperty("judgePromptTokens")
    public long judgePromptTokens() {
      return byCheck.stream().mapToLong(CheckUsage::promptTokens).sum();
    }

    @JsonProperty("judgeCompletionTokens")
    public long judgeCompletionTokens() {
      return byCheck.stream().mapToLong(CheckUsage::completionTokens).sum();
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

  /**
   * For each advisory check (D12), in registration order, the number of cases where it failed. A
   * {@code check error} counts, so a broken advisory judge is visible. Never part of pass/fail.
   */
  @JsonProperty("advisoryFlags")
  public Map<String, Long> advisoryFlags() {
    Map<String, Long> flags = new LinkedHashMap<>();
    for (CheckInfo info : checks) {
      if (info.gating()) {
        continue;
      }
      long flaggedCases =
          cases.stream()
              .filter(
                  caseResult ->
                      caseResult.checks().stream()
                          .anyMatch(
                              outcome ->
                                  outcome.check().equals(info.name()) && !outcome.passed()))
              .count();
      flags.put(info.name(), flaggedCases);
    }
    return flags;
  }

  public long passed() {
    return cases.stream().filter(CaseResult::passed).count();
  }

  @JsonProperty("passRate")
  public double passRate() {
    return cases.isEmpty() ? 0 : passed() / (double) cases.size();
  }

  /** Passed and total for one group of cases. */
  public record Rollup(int passed, int total) {}

  /** Pass counts per category, in the order the categories first appear. */
  @JsonProperty("byCategory")
  public Map<String, Rollup> byCategory() {
    return rollup(cases, CaseResult::category);
  }

  /**
   * Pass counts for the out-of-scope cases per subtype (D4), so the report shows which kind of bait
   * the assistant falls for. A case with no subtype is counted as {@code untagged}.
   */
  @JsonProperty("outOfScopeBySubtype")
  public Map<String, Rollup> outOfScopeBySubtype() {
    List<CaseResult> outOfScopeCases =
        cases.stream().filter(caseResult -> OUT_OF_SCOPE.equals(caseResult.category())).toList();
    return rollup(
        outOfScopeCases,
        caseResult -> caseResult.subtype() == null ? "untagged" : caseResult.subtype());
  }

  private static Map<String, Rollup> rollup(
      List<CaseResult> results, Function<CaseResult, String> groupOf) {
    Map<String, int[]> passedAndTotal = new LinkedHashMap<>();
    for (CaseResult result : results) {
      int[] counts = passedAndTotal.computeIfAbsent(groupOf.apply(result), group -> new int[2]);
      if (result.passed()) {
        counts[0]++;
      }
      counts[1]++;
    }
    Map<String, Rollup> rollups = new LinkedHashMap<>();
    passedAndTotal.forEach((group, counts) -> rollups.put(group, new Rollup(counts[0], counts[1])));
    return rollups;
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

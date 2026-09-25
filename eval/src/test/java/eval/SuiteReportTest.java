package eval;

import static org.junit.jupiter.api.Assertions.*;

import eval.calibration.LabeledSample;
import java.util.*;
import org.junit.jupiter.api.Test;

class SuiteReportTest {
  /** n cases; ids in failing set fail; ids starting "oos" get category out-of-scope. */
  private SuiteReport report(int n, Set<Integer> failing, Set<Integer> oos) {
    List<CaseResult> cs = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      cs.add(
          new CaseResult(
              (oos.contains(i) ? "oos-" : "c-") + i,
              "q",
              oos.contains(i) ? "out-of-scope" : "single-source",
              null,
              new Expected(false, List.of()),
              !failing.contains(i),
              null,
              null,
              List.of()));
    }
    return new SuiteReport("r", "http://x", 0.90, List.of(), SuiteReport.Calibration.SKIPPED, cs);
  }

  @Test
  void twentySixOfTwentyEightMeetsFloor() {
    var r = report(28, Set.of(0, 1), Set.of());
    assertEquals(0, r.exitCode());
  }

  @Test
  void twentyFiveOfTwentyEightMissesFloorAndSaysWhy() {
    var r = report(28, Set.of(0, 1, 2), Set.of());
    assertEquals(1, r.exitCode());
    assertTrue(
        r.exitReasons().get(0).contains("89.3%") && r.exitReasons().get(0).contains("90.0%"));
  }

  @Test
  void nineOfTenIsExactlyOnTheFloor() {
    assertEquals(0, report(10, Set.of(0), Set.of()).exitCode());
  }

  @Test
  void oneFailingOutOfScopeFailsRunEvenWhenRateIsHigh() {
    var r = report(28, Set.of(27), Set.of(27));
    assertEquals(1, r.exitCode());
    assertEquals(List.of("out-of-scope case failed: oos-27"), r.exitReasons());
  }

  @Test
  void nonOutOfScopeFailuresAloneDoNotTriggerTheOutOfScopeRule() {
    assertEquals(List.of(), report(28, Set.of(5), Set.of(27)).exitReasons());
  }

  @Test
  void calibrationMissesFailTheRunEvenWhenAllCasesPass() {
    var miss = new eval.calibration.TrapPairs.TrapResult("t", false, "expected NO");
    var r =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(),
            new SuiteReport.Calibration(true, List.of(miss)),
            List.of());
    assertTrue(r.exitReasons().contains("judge calibration: 1 trap pair miss(es)"));
    assertEquals(1, r.exitCode());
  }

  @Test
  void anUnsupportedPairJudgedSupportedFailsTheRun() {
    var missed =
        new LabeledSample.PairResult(
            "u1", "unsupported", false, true, "labeled UNSUPPORTED, judge said SUPPORTED");
    var report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(),
            new SuiteReport.Calibration(true, List.of(), new LabeledSample.Result(List.of(missed))),
            List.of());
    assertTrue(
        report.exitReasons().stream()
            .anyMatch(
                reason ->
                    reason.contains(
                        "groundedness calibration: 1 of 1 unsupported pair(s) judged supported")),
        report.exitReasons().toString());
  }

  @Test
  void agreementBelowNinetyPercentFailsTheRun() {
    var agreeing = new LabeledSample.PairResult("a", "supported", true, true, null);
    var disagreeing =
        new LabeledSample.PairResult(
            "b", "supported", true, false, "labeled SUPPORTED, judge said UNSUPPORTED");
    var report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(),
            new SuiteReport.Calibration(
                true, List.of(), new LabeledSample.Result(List.of(agreeing, disagreeing))),
            List.of());
    assertTrue(
        report.exitReasons().stream()
            .anyMatch(reason -> reason.contains("groundedness calibration: agreement 50.0%")),
        report.exitReasons().toString());
  }

  @Test
  void anErroredPairFailsTheRunEvenWhenAgreementIsAboveTarget() {
    var pairs = new ArrayList<LabeledSample.PairResult>();
    for (int i = 0; i < 19; i++) {
      pairs.add(new LabeledSample.PairResult("a" + i, "supported", true, true, null));
    }
    pairs.add(new LabeledSample.PairResult("e", "unsupported", false, null, "error: unclear"));
    var report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(),
            new SuiteReport.Calibration(true, List.of(), new LabeledSample.Result(pairs)),
            List.of());
    assertTrue(
        report
            .exitReasons()
            .contains(
                "groundedness calibration: 1 pair(s) errored (the judge gave no usable answer)"),
        report.exitReasons().toString());
    assertTrue(report.exitReasons().stream().noneMatch(r -> r.contains("agreement")));
  }

  @Test
  void aSkippedOrEmptyGroundednessSampleAddsNoExitReason() {
    var report =
        new SuiteReport(
            "r", "http://x", 0.90, List.of(), SuiteReport.Calibration.SKIPPED, List.of());
    assertTrue(report.exitReasons().stream().noneMatch(reason -> reason.contains("groundedness")));
  }

  private static SuiteReport withBaseline(SuiteReport base, SuiteReport.Comparison comparison) {
    return new SuiteReport(
        base.runId(),
        base.endpoint(),
        base.passFloor(),
        base.checks(),
        base.calibration(),
        base.cases(),
        comparison);
  }

  @Test
  void aRegressionFailsTheRunEvenWhenTheRateIsAboveTheFloor() {
    var comparison =
        new SuiteReport.Comparison(
            "baseline.json", List.of(new SuiteReport.Comparison.Rerun("c-3", false)));
    var run = withBaseline(report(28, Set.of(3), Set.of()), comparison);
    assertEquals(1, run.exitCode());
    assertEquals(List.of("regression vs baseline (baseline.json): c-3"), run.exitReasons());
  }

  @Test
  void aCaseThatPassedOnTheRerunIsNotARegression() {
    var comparison =
        new SuiteReport.Comparison(
            "baseline.json", List.of(new SuiteReport.Comparison.Rerun("c-3", true)));
    var run = withBaseline(report(28, Set.of(), Set.of()), comparison);
    assertEquals(0, run.exitCode());
    assertEquals(List.of(), comparison.regressions());
  }

  @Test
  void regressionsListOnlyTheCasesThatFailedTheRerunInOrder() {
    var comparison =
        new SuiteReport.Comparison(
            "b.json",
            List.of(
                new SuiteReport.Comparison.Rerun("x", false),
                new SuiteReport.Comparison.Rerun("y", true),
                new SuiteReport.Comparison.Rerun("z", false)));
    assertEquals(List.of("x", "z"), comparison.regressions());
    assertEquals(
        List.of("regression vs baseline (b.json): x, z"),
        withBaseline(report(28, Set.of(), Set.of()), comparison).exitReasons());
  }

  @Test
  void noBaselineMeansNoComparisonAndNoRegressionReason() {
    assertNull(report(28, Set.of(5), Set.of()).baseline());
    assertEquals(List.of(), report(28, Set.of(5), Set.of()).exitReasons());
  }
}

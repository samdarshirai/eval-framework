package eval;

import eval.calibration.LabeledSample;
import eval.calibration.TrapPairs.TrapResult;
import java.io.PrintStream;
import java.util.Locale;

/** Prints a finished suite report to the console. */
final class ConsoleReport {
  private ConsoleReport() {}

  static void print(SuiteReport report, PrintStream out) {
    out.println("Endpoint: " + report.endpoint() + "  Run: " + report.runId());
    printCalibration(report.calibration(), out);
    for (CaseResult caseResult : report.cases()) {
      out.printf(
          "%-4s %-22s %-14s%n",
          caseResult.passed() ? "PASS" : "FAIL", caseResult.id(), caseResult.category());
      if (caseResult.error() != null) {
        out.println("       assistant error: " + caseResult.error());
      }
      for (CheckOutcome outcome : caseResult.checks()) {
        if (!outcome.passed()) {
          out.println(
              "       "
                  + outcome.check()
                  + (report.isGating(outcome.check()) ? "" : " (advisory)")
                  + ": "
                  + outcome.reason());
        }
      }
    }
    printBaseline(report.baseline(), out);
    printRollups(report, out);
    printAdvisory(report, out);
    printUsage(report.usage(), out);
    out.printf(
        "%nPass rate: %d/%d (%.1f%%), floor %.1f%%%n",
        report.passed(), report.cases().size(), report.passRate() * 100, report.passFloor() * 100);
    if (report.exitCode() == 0) {
      out.println("RESULT: OK");
    } else {
      report.exitReasons().forEach(reason -> out.println("RESULT: FAIL - " + reason));
    }
  }

  private static void printAdvisory(SuiteReport report, PrintStream out) {
    report
        .advisoryFlags()
        .forEach(
            (check, flaggedCases) ->
                out.printf(
                    "Advisory %s (never fails a case): flagged on %d of %d case(s)%n",
                    check, flaggedCases, report.cases().size()));
  }

  private static void printUsage(SuiteReport.Usage usage, PrintStream out) {
    if (usage == null) {
      return;
    }
    out.println("Cost and time");
    out.println(
        String.format(Locale.ROOT, "  wall-clock: %.1f s", usage.wallClockMillis() / 1000.0));
    out.println(
        String.format(
            Locale.ROOT,
            "  assistant: %d calls, %.1f s (its tokens are not visible over HTTP)",
            usage.assistantCalls(),
            usage.assistantMillis() / 1000.0));
    String cost;
    if (usage.judgeCostUsd() != null) {
      cost = String.format(Locale.ROOT, "est. $%.4f", usage.judgeCostUsd());
    } else if (usage.judgeCalls() > 0
        && usage.judgePromptTokens() + usage.judgeCompletionTokens() == 0) {
      cost = "tokens not reported, cost not estimated";
    } else {
      cost = "cost not estimated (set judgePricing in the config)";
    }
    out.println(
        String.format(
            Locale.ROOT,
            "  judge: %d calls, %d prompt + %d completion tokens, %s",
            usage.judgeCalls(),
            usage.judgePromptTokens(),
            usage.judgeCompletionTokens(),
            cost));
    for (SuiteReport.Usage.CheckUsage byCheck : usage.byCheck()) {
      out.println(
          String.format(
              Locale.ROOT,
              "    %-20s %d calls, %d + %d tokens",
              byCheck.check(),
              byCheck.calls(),
              byCheck.promptTokens(),
              byCheck.completionTokens()));
    }
  }

  private static void printRollups(SuiteReport report, PrintStream out) {
    out.println("By category");
    report
        .byCategory()
        .forEach(
            (category, rollup) ->
                out.printf("  %-24s %d/%d%n", category, rollup.passed(), rollup.total()));
    if (report.outOfScopeBySubtype().isEmpty()) {
      return;
    }
    out.println("Out-of-scope by subtype");
    report
        .outOfScopeBySubtype()
        .forEach(
            (subtype, rollup) ->
                out.printf("  %-24s %d/%d%n", subtype, rollup.passed(), rollup.total()));
  }

  private static void printBaseline(SuiteReport.Comparison baseline, PrintStream out) {
    if (baseline == null) {
      return;
    }
    out.println("Baseline: " + baseline.file());
    if (baseline.reruns().isEmpty()) {
      out.println("  no case that passed there failed now");
    } else {
      out.println("  re-run once: " + baseline.reruns().size());
      for (SuiteReport.Comparison.Rerun rerun : baseline.reruns()) {
        out.println(
            rerun.passedOnRerun()
                ? "  flaky      " + rerun.id() + " (failed, then passed on the re-run)"
                : "  REGRESSION " + rerun.id() + " (passed in the baseline, failed twice now)");
      }
    }
    if (!baseline.improved().isEmpty()) {
      out.println(
          "  improved since baseline: "
              + String.join(", ", baseline.improved())
              + " (promote a newer report to guard them)");
    }
  }

  private static void printCalibration(SuiteReport.Calibration calibration, PrintStream out) {
    if (!calibration.ran()) {
      out.println("Judge calibration: skipped");
      return;
    }
    out.println("Judge calibration");
    for (TrapResult pair : calibration.pairs()) {
      out.println(
          pair.passed()
              ? "  ok   " + pair.name()
              : "  MISS " + pair.name() + " (" + pair.detail() + ")");
    }
    out.println(
        "  "
            + (calibration.pairs().size() - calibration.misses())
            + "/"
            + calibration.pairs().size()
            + " trap pairs ok");
    LabeledSample.Result sample = calibration.groundedness();
    if (sample.pairs().isEmpty()) {
      return;
    }
    out.println("Groundedness calibration");
    for (LabeledSample.PairResult pair : sample.pairs()) {
      if (!pair.agreed()) {
        out.println("  MISS " + pair.name() + " (" + pair.detail() + ")");
      }
    }
    out.printf(
        "  agreement %d/%d (%.0f%%), target 90%%%n",
        sample.agreed(), sample.pairs().size(), sample.agreement() * 100);
    out.printf(
        "  unsupported judged supported: %d of %d (target 0)%n",
        sample.falseSupported(), sample.unsupportedPairs());
    out.printf("  supported judged unsupported: %d (no target)%n", sample.falseUnsupported());
    if (sample.errors() > 0) {
      out.println("  errored pairs: " + sample.errors() + " (each fails the run)");
    }
  }
}

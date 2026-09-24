package eval;

import eval.calibration.LabeledSample;
import eval.calibration.TrapPairs.TrapResult;
import java.io.PrintStream;

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
    out.printf(
        "%nPass rate: %d/%d (%.1f%%), floor %.1f%%%n",
        report.passed(), report.cases().size(), report.passRate() * 100, report.passFloor() * 100);
    if (report.exitCode() == 0) {
      out.println("RESULT: OK");
    } else {
      report.exitReasons().forEach(reason -> out.println("RESULT: FAIL - " + reason));
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

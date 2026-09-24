package eval;

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
  }
}

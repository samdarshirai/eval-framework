package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsoleReportTest {
  private static CaseResult result(String id, String category, String subtype, boolean passed) {
    return new CaseResult(
        id, "q", category, subtype, new Expected(false, List.of()), passed, null, null, List.of());
  }

  private static String print(CaseResult... results) {
    SuiteReport report =
        new SuiteReport(
            "r", "http://x", 0.90, List.of(), SuiteReport.Calibration.SKIPPED, List.of(results));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ConsoleReport.print(report, new PrintStream(buffer));
    return buffer.toString();
  }

  @Test
  void printsTheCategoryAndSubtypeRollupsBeforeThePassRate() {
    String output =
        print(
            result("a", "single-source", null, true),
            result("b", "single-source", null, false),
            result("c", "out-of-scope", "unrelated", true));
    assertTrue(output.contains("By category"), output);
    assertTrue(output.matches("(?s).*single-source\\s+1/2.*"), output);
    assertTrue(output.contains("Out-of-scope by subtype"), output);
    assertTrue(output.matches("(?s).*unrelated\\s+1/1.*"), output);
    assertTrue(output.indexOf("By category") < output.indexOf("Pass rate:"), output);
  }

  @Test
  void leavesOutTheSubtypeSectionWhenThereIsNoOutOfScopeCase() {
    String output = print(result("a", "single-source", null, true));
    assertTrue(output.contains("By category"), output);
    assertFalse(output.contains("Out-of-scope by subtype"), output);
  }

  @Test
  void printsHowManyCasesEachAdvisoryCheckFlaggedBeforeThePassRate() {
    CaseResult flagged =
        new CaseResult(
            "a",
            "q",
            "single-source",
            null,
            new Expected(false, List.of()),
            true,
            null,
            null,
            List.of(new CheckOutcome("Relevance", false, "off-topic claim(s): \"x\"")));
    SuiteReport report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(new CheckInfo("Relevance", false)),
            SuiteReport.Calibration.SKIPPED,
            List.of(flagged, result("b", "single-source", null, true)));
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ConsoleReport.print(report, new PrintStream(buffer));
    String output = buffer.toString();
    assertTrue(output.contains("Relevance (advisory): off-topic claim(s)"), output);
    assertTrue(
        output.contains("Advisory Relevance (never fails a case): flagged on 1 of 2 case(s)"),
        output);
    assertTrue(output.indexOf("Advisory Relevance") < output.indexOf("Pass rate:"), output);
    assertTrue(output.contains("RESULT: OK"), output);
  }

  private static SuiteReport.Usage usage(Double cost) {
    return new SuiteReport.Usage(
        65_400,
        2,
        1_500,
        List.of(
            new SuiteReport.Usage.CheckUsage("Coverage", 3, 3000, 30),
            new SuiteReport.Usage.CheckUsage("Relevance", 2, 1000, 10)),
        cost);
  }

  private static String printWithUsage(SuiteReport.Usage usage) {
    SuiteReport report =
        new SuiteReport(
            "r",
            "http://x",
            0.90,
            List.of(),
            SuiteReport.Calibration.SKIPPED,
            List.of(result("a", "single-source", null, true)),
            null,
            usage);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    ConsoleReport.print(report, new PrintStream(buffer));
    return buffer.toString();
  }

  @Test
  void printsWallClockAssistantAndJudgeUsageWithAnEstimatedCost() {
    String output = printWithUsage(usage(0.02));
    assertTrue(output.contains("Cost and time"), output);
    assertTrue(output.contains("wall-clock: 65.4 s"), output);
    assertTrue(output.contains("assistant: 2 calls, 1.5 s"), output);
    assertTrue(
        output.contains("judge: 5 calls, 4000 prompt + 40 completion tokens, est. $0.0200"),
        output);
    assertTrue(output.matches("(?s).*Coverage\\s+3 calls, 3000 \\+ 30 tokens.*"), output);
    assertTrue(output.indexOf("Cost and time") < output.indexOf("Pass rate:"), output);
  }

  @Test
  void withNoPricesItSaysTheCostWasNotEstimatedInsteadOfPrintingZero() {
    String output = printWithUsage(usage(null));
    assertTrue(output.contains("cost not estimated"), output);
    assertFalse(output.contains("$0.0000"), output);
  }

  @Test
  void aReportWithNoUsageLeavesTheBlockOut() {
    assertFalse(printWithUsage(null).contains("Cost and time"));
  }

  @Test
  void callsWithNoReportedTokensSayTokensNotReportedInsteadOfAZeroCost() {
    String output =
        printWithUsage(
            new SuiteReport.Usage(
                1000,
                1,
                100,
                List.of(new SuiteReport.Usage.CheckUsage("Coverage", 3, 0, 0)),
                null));
    assertTrue(output.contains("tokens not reported"), output);
    assertFalse(output.contains("$0.0000"), output);
    assertFalse(output.contains("set judgePricing"), output);
  }
}

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
}

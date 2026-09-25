package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class UsageMeterTest {
  @Test
  void judgeCallsAreCountedPerTheCheckThatWasRunning() {
    UsageMeter meter = new UsageMeter();
    meter.setCheck("Coverage");
    meter.judgeCall(100, 5);
    meter.judgeCall(200, 10);
    meter.setCheck("Relevance");
    meter.judgeCall(50, 1);
    SuiteReport.Usage usage = meter.usage(1000, null);
    assertEquals(
        List.of(
            new SuiteReport.Usage.CheckUsage("Coverage", 2, 300, 15),
            new SuiteReport.Usage.CheckUsage("Relevance", 1, 50, 1)),
        usage.byCheck());
    assertEquals(3, usage.judgeCalls());
    assertEquals(350, usage.judgePromptTokens());
    assertEquals(16, usage.judgeCompletionTokens());
  }

  @Test
  void aCallOutsideAnyCheckIsStillCounted() {
    UsageMeter meter = new UsageMeter();
    meter.setCheck("Coverage");
    meter.setCheck(null);
    meter.judgeCall(10, 1);
    SuiteReport.Usage usage = meter.usage(0, null);
    assertEquals(1, usage.judgeCalls());
    assertEquals("outside checks", usage.byCheck().get(0).check());
  }

  @Test
  void assistantCallsAndTimeAreSummed() {
    UsageMeter meter = new UsageMeter();
    meter.assistantCall(1500);
    meter.assistantCall(500);
    SuiteReport.Usage usage = meter.usage(9000, null);
    assertEquals(2, usage.assistantCalls());
    assertEquals(2000, usage.assistantMillis());
    assertEquals(9000, usage.wallClockMillis());
  }

  @Test
  void costIsTokensTimesThePricePerMillion() {
    UsageMeter meter = new UsageMeter();
    meter.setCheck("Coverage");
    meter.judgeCall(1_000_000, 1_000_000);
    SuiteReport.Usage usage = meter.usage(0, new UsageMeter.Pricing(5.0, 25.0));
    assertEquals(30.0, usage.judgeCostUsd(), 1e-9);
  }

  @Test
  void withoutPricesTheCostIsNullNotZero() {
    UsageMeter meter = new UsageMeter();
    meter.judgeCall(100, 5);
    assertNull(meter.usage(0, null).judgeCostUsd());
  }

  @Test
  void aRunWithNoCallsIsAllZeroesWithoutFailing() {
    SuiteReport.Usage usage = new UsageMeter().usage(0, new UsageMeter.Pricing(5.0, 25.0));
    assertEquals(0, usage.judgeCalls());
    assertEquals(0.0, usage.judgeCostUsd(), 1e-9);
    assertTrue(usage.byCheck().isEmpty());
  }

  @Test
  void callsThatReportedNoTokensGiveNoCostRatherThanZero() {
    UsageMeter meter = new UsageMeter();
    meter.judgeCall(0, 0);
    assertNull(meter.usage(0, new UsageMeter.Pricing(5.0, 25.0)).judgeCostUsd());
  }
}

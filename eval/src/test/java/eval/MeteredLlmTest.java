package eval;

import static org.junit.jupiter.api.Assertions.*;

import llm.Completion;
import llm.Llm;
import org.junit.jupiter.api.Test;

class MeteredLlmTest {
  private static final Llm REPORTING =
      new Llm() {
        @Override
        public String complete(String system, String user) {
          return "unused";
        }

        @Override
        public Completion completeWithUsage(String system, String user) {
          return new Completion("YES", 100, 5);
        }
      };

  @Test
  void returnsTheTextAndRecordsTheCallUnderTheRunningCheck() {
    UsageMeter meter = new UsageMeter();
    MeteredLlm metered = new MeteredLlm(REPORTING, meter);
    meter.setCheck("Coverage");
    assertEquals("YES", metered.complete("s", "u"));
    SuiteReport.Usage usage = meter.usage(0, null);
    assertEquals(1, usage.judgeCalls());
    assertEquals(100, usage.judgePromptTokens());
    assertEquals(5, usage.judgeCompletionTokens());
    assertEquals("Coverage", usage.byCheck().get(0).check());
  }

  @Test
  void anLlmThatReportsNoTokensStillCountsTheCall() {
    UsageMeter meter = new UsageMeter();
    new MeteredLlm((system, user) -> "YES", meter).complete("s", "u");
    SuiteReport.Usage usage = meter.usage(0, null);
    assertEquals(1, usage.judgeCalls());
    assertEquals(0, usage.judgePromptTokens());
  }

  @Test
  void aFailedCallIsNotCountedAndTheFailureReachesTheCaller() {
    UsageMeter meter = new UsageMeter();
    MeteredLlm metered =
        new MeteredLlm(
            (system, user) -> {
              throw new IllegalStateException("boom");
            },
            meter);
    assertThrows(IllegalStateException.class, () -> metered.complete("s", "u"));
    assertEquals(0, meter.usage(0, null).judgeCalls());
  }
}

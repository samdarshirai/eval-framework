package eval;

import static org.junit.jupiter.api.Assertions.*;

import eval.checks.Check;
import eval.checks.CheckResult;
import eval.checks.Registered;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CaseRunnerTest {
  private static final Answer ANSWER = new Answer(false, List.of());
  private static final ExpectedFact FACT = new ExpectedFact("f", List.of("d#a"), List.of());

  private static EvalCase evalCase(String id) {
    return new EvalCase(id, "q " + id, "cat", null, "answer", List.of(), "src", "me", "2026-01-01");
  }

  private static Assistant answering() {
    return (question, runId) -> ANSWER;
  }

  private static Check check(String name, CheckResult result) {
    return new Check() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
        return result;
      }
    };
  }

  private static Check throwing(String name, RuntimeException failure) {
    return new Check() {
      @Override
      public String name() {
        return name;
      }

      @Override
      public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
        throw failure;
      }
    };
  }

  @Test
  void gatingFailureFailsTheCase() {
    var runner =
        new CaseRunner(
            answering(), List.of(new Registered(check("g", CheckResult.fail("bad")), true)));
    var result = runner.run(evalCase("a"), "run");
    assertFalse(result.passed());
    assertNull(result.error());
    assertSame(ANSWER, result.actual());
    assertEquals(List.of(new CheckOutcome("g", false, "bad")), result.checks());
  }

  @Test
  void advisoryFailureDoesNotFailTheCase() {
    var runner =
        new CaseRunner(
            answering(),
            List.of(
                new Registered(check("adv", CheckResult.fail("meh")), false),
                new Registered(check("g", CheckResult.ok()), true)));
    var result = runner.run(evalCase("a"), "run");
    assertTrue(result.passed());
    assertEquals(2, result.checks().size());
    assertFalse(result.checks().get(0).passed());
  }

  @Test
  void throwingCheckIsAFailedOutcomeAndLaterChecksStillRun() {
    var runner =
        new CaseRunner(
            answering(),
            List.of(
                new Registered(throwing("boom", new IllegalStateException("kaput")), true),
                new Registered(check("after", CheckResult.ok()), true)));
    var result = runner.run(evalCase("a"), "run");
    assertFalse(result.passed());
    assertEquals(
        List.of(
            new CheckOutcome("boom", false, "check error: kaput"),
            new CheckOutcome("after", true, null)),
        result.checks());
  }

  @Test
  void throwWithoutMessageUsesToString() {
    var failure = new IllegalStateException();
    var runner =
        new CaseRunner(answering(), List.of(new Registered(throwing("boom", failure), false)));
    var result = runner.run(evalCase("a"), "run");
    assertEquals("check error: " + failure, result.checks().get(0).reason());
    assertTrue(result.passed());
  }

  @Test
  void assistantExceptionFailsTheCaseWithNoChecks() {
    Assistant failing =
        (question, runId) -> {
          throw new IOException("HTTP 500");
        };
    var runner =
        new CaseRunner(failing, List.of(new Registered(check("g", CheckResult.ok()), true)));
    var result = runner.run(evalCase("a"), "run");
    assertFalse(result.passed());
    assertEquals("HTTP 500", result.error());
    assertNull(result.actual());
    assertEquals(List.of(), result.checks());
  }

  @Test
  void interruptionSetsTheFlagAndReportsInterrupted() {
    Assistant interrupted =
        (question, runId) -> {
          throw new InterruptedException();
        };
    var runner =
        new CaseRunner(interrupted, List.of(new Registered(check("g", CheckResult.ok()), true)));
    try {
      var result = runner.run(evalCase("a"), "run");
      assertFalse(result.passed());
      assertEquals("interrupted", result.error());
      assertEquals(List.of(), result.checks());
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void caseStateIsSharedWithinACaseButNotAcrossCases() {
    var claim = new Claim("x", List.of("d#a"));
    List<Integer> seenByReader = new ArrayList<>();
    Check writer =
        new Check() {
          @Override
          public String name() {
            return "writer";
          }

          @Override
          public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
            state.setCovering(FACT, List.of(claim));
            return CheckResult.ok();
          }
        };
    Check reader =
        new Check() {
          @Override
          public String name() {
            return "reader";
          }

          @Override
          public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
            seenByReader.add(state.covering(FACT).size());
            return CheckResult.ok();
          }
        };
    Check readerFirst =
        new Check() {
          @Override
          public String name() {
            return "readerFirst";
          }

          @Override
          public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
            seenByReader.add(state.covering(FACT).size());
            return CheckResult.ok();
          }
        };
    var runner =
        new CaseRunner(
            answering(),
            List.of(
                new Registered(readerFirst, true),
                new Registered(writer, true),
                new Registered(reader, true)));
    runner.run(evalCase("a"), "run");
    runner.run(evalCase("b"), "run");
    // per case: readerFirst sees nothing (fresh state), reader sees the writer's claim
    assertEquals(List.of(0, 1, 0, 1), seenByReader);
  }

  @Test
  void everyAssistantCallIsCountedIncludingOnesThatFail() {
    UsageMeter meter = new UsageMeter();
    Assistant failing =
        (question, runId) -> {
          throw new IOException("HTTP 500");
        };
    new CaseRunner(answering(), List.of(), meter).run(evalCase("a"), "run");
    new CaseRunner(failing, List.of(), meter).run(evalCase("b"), "run");
    assertEquals(2, meter.usage(0, null).assistantCalls());
  }

  @Test
  void theMeterKnowsWhichCheckIsRunningAndForgetsItAfterwards() {
    UsageMeter meter = new UsageMeter();
    AtomicReference<String> insideFirst = new AtomicReference<>();
    Check probing =
        new Check() {
          @Override
          public String name() {
            return "probe";
          }

          @Override
          public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
            meter.judgeCall(10, 1);
            insideFirst.set("ran");
            return CheckResult.ok();
          }
        };
    new CaseRunner(answering(), List.of(new Registered(probing, true)), meter)
        .run(evalCase("a"), "run");
    meter.judgeCall(1, 1); // after the case: not attributed to "probe"
    SuiteReport.Usage usage = meter.usage(0, null);
    assertEquals("ran", insideFirst.get());
    assertEquals("probe", usage.byCheck().get(0).check());
    assertEquals(1, usage.byCheck().get(0).calls());
    assertEquals("outside checks", usage.byCheck().get(1).check());
  }
}

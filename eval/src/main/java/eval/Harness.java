package eval;

import eval.checks.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;
import llm.*;

public final class Harness {

  public static void main(String[] args) throws Exception {
    System.exit(run(args, Path.of(".")));
  }

  static int run(String[] args, Path root) throws Exception {
    return run(args, root, System.out, model -> OpenRouterLlm.fromEnv(model, "low"));
  }

  static int run(String[] args, Path root, PrintStream out, Function<String, Llm> judgeLlm)
      throws Exception {
    try {
      return runInner(args, root, out, judgeLlm);
    } catch (IOException | RuntimeException e) {
      out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : e));
      return 2;
    }
  }

  /** Wiring only: parse args, load config and cases, preflight, run each case, report. */
  private static int runInner(
      String[] args, Path root, PrintStream out, Function<String, Llm> judgeLlm) throws Exception {
    EvalConfig config = EvalConfig.from(root, CliArgs.parse(args));
    Baseline baseline = Baseline.resolve(config, out);
    CalibrationRun.preflight(config);

    // Validate cases against the docs BEFORE contacting the assistant.
    KnowledgeBase kb = KnowledgeBase.from(config);
    List<EvalCase> cases = EvalCaseLoader.load(config.casesDir(), kb, config.categories());
    if (baseline != null) {
      baseline.requireAnyOf(cases);
    }
    for (String warning : StaleCases.warnings(cases, kb)) {
      out.println("WARNING: " + warning);
    }

    // Throws with the export hint if the API key is missing.
    Judge judge = new Judge(judgeLlm.apply(config.requireJudgeModel()));
    List<Registered> checks = Checks.registered(kb, judge);
    AssistantClient client = new AssistantClient(config.endpoint());
    client.requireReachable();
    SuiteReport.Calibration calibration = CalibrationRun.run(config, judge);

    String runId = ReportWriter.newRunId();
    SuiteRunner.Outcome outcome =
        new SuiteRunner(new CaseRunner(client, checks)).run(cases, runId, baseline);
    SuiteReport report =
        new SuiteReport(
            runId,
            config.endpoint(),
            config.passFloor(),
            CheckInfo.of(checks),
            calibration,
            outcome.results(),
            outcome.comparison());
    ConsoleReport.print(report, out);
    ReportWriter.writeAndAnnounce(config.outputDir(), report, root, out);
    return report.exitCode();
  }
}

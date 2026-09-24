package eval;

import eval.calibration.TrapPairs;
import eval.checks.*;
import eval.knowledge.KnowledgeSources;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.util.*;
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
    String endpointArg = null;
    boolean skipCalibration = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--endpoint") && i + 1 < args.length && !args[i + 1].startsWith("--")) {
        endpointArg = args[++i];
      } else if (args[i].equals("--skip-calibration")) {
        skipCalibration = true;
      } else {
        out.println(
            "ERROR: "
                + (args[i].equals("--endpoint")
                    ? "--endpoint needs a value"
                    : "unknown argument '" + args[i] + "'"));
        out.println("Usage: Harness [--endpoint <url>] [--skip-calibration]");
        return 2;
      }
    }
    EvalConfig config = EvalConfig.load(root, endpointArg);
    Path trapFile = root.resolve("calibration/trap-pairs.yaml");
    if (!skipCalibration && !Files.isReadable(trapFile)) {
      out.println("ERROR: cannot read the judge trap pairs at " + trapFile);
      out.println("Restore the file, or pass --skip-calibration to run without them.");
      return 2;
    }

    // Validate cases against the docs BEFORE contacting the assistant.
    KnowledgeBase kb = getKnowledgeBase(root, out, config);
    if (kb == null) {
      return 2;
    }
    List<EvalCase> cases = getEvalCases(root, kb, config);

    String judgeModel = config.requireJudgeModel();
    Judge judge =
        new Judge(
            judgeLlm.apply(judgeModel)); // throws with the export hint if the API key is missing
    List<Registered> checks = Checks.registered(kb, judge);
    AssistantClient client = new AssistantClient(config.endpoint());
    if (!client.reachable()) {
      logErrorBeforeExit(out, config);
      return 2;
    }

    SuiteReport.Calibration calibration =
        skipCalibration
            ? SuiteReport.Calibration.SKIPPED
            : new SuiteReport.Calibration(true, TrapPairs.run(trapFile, judge));

    String runId = ReportWriter.newRunId();
    CaseRunner runner = new CaseRunner(client, checks);
    List<CaseResult> caseResults = new ArrayList<>();
    for (EvalCase evalCase : cases) {
      caseResults.add(runner.run(evalCase, runId));
    }
    SuiteReport report =
        new SuiteReport(
            runId,
            config.endpoint(),
            config.passFloor(),
            checkInfos(checks),
            calibration,
            caseResults);

    ConsoleReport.print(report, out);
    ReportWriter.write(root, report);
    out.println("Report: caseResults/" + runId + ".json");
    return report.exitCode();
  }

  private static List<EvalCase> getEvalCases(Path root, KnowledgeBase kb, EvalConfig config)
      throws IOException {
    return EvalCaseLoader.load(root.resolve("eval/cases"), kb, config.categories());
  }

  private static KnowledgeBase getKnowledgeBase(Path root, PrintStream out, EvalConfig config)
      throws IOException {
    KnowledgeBase kb;
    try {
      kb = new KnowledgeBase(KnowledgeSources.from(config.raw(), root));

    } catch (IllegalArgumentException e) {
      out.println("ERROR: " + e.getMessage());
      return null;
    }
    return kb;
  }

  private static void logErrorBeforeExit(PrintStream out, EvalConfig config) {
    out.println("ERROR: cannot reach the assistant at " + config.endpoint());
    out.println("Start the stub in another terminal first:");
    out.println("  export OPENROUTER_API_KEY=...");
    out.println("  mvn -q -DskipTests package");
    out.println("  java -jar assistant/target/assistant.jar");
  }

  private static List<CheckInfo> checkInfos(List<Registered> checks) {
    return checks.stream()
        .map(registered -> new CheckInfo(registered.check().name(), registered.gating()))
        .toList();
  }
}

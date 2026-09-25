package eval;

import eval.calibration.LabeledSample;
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
    String configArg = null;
    boolean skipCalibration = false;
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--endpoint") && i + 1 < args.length && !args[i + 1].startsWith("--")) {
        endpointArg = args[++i];
      } else if (args[i].equals("--config")
          && i + 1 < args.length
          && !args[i + 1].startsWith("--")) {
        configArg = args[++i];
      } else if (args[i].equals("--skip-calibration")) {
        skipCalibration = true;
      } else {
        out.println(
            "ERROR: "
                + (args[i].equals("--endpoint") || args[i].equals("--config")
                    ? args[i] + " needs a value"
                    : "unknown argument '" + args[i] + "'"));
        out.println("Usage: Harness [--config <file>] [--endpoint <url>] [--skip-calibration]");
        return 2;
      }
    }
    EvalConfig config;
    if (configArg == null) {
      config = EvalConfig.load(root, endpointArg);
    } else {
      Path configFile = root.resolve(configArg);
      if (!Files.isRegularFile(configFile)) {
        out.println("ERROR: config file not found: " + configArg);
        return 2;
      }
      config = EvalConfig.loadFile(configFile, endpointArg);
    }
    Path trapFile = config.trapPairsFile(); // null: use the trap pairs bundled in the jar
    if (!skipCalibration && trapFile != null && !Files.isReadable(trapFile)) {
      out.println("ERROR: cannot read the judge trap pairs at " + trapFile);
      out.println("Restore the file, or pass --skip-calibration to run without them.");
      return 2;
    }
    Path sampleFile = config.labeledSampleFile(); // null: use the sample bundled in the jar
    if (!skipCalibration && sampleFile != null && !Files.isReadable(sampleFile)) {
      out.println("ERROR: cannot read the labeled Groundedness sample at " + sampleFile);
      out.println("Restore the file, or pass --skip-calibration to run without it.");
      return 2;
    }

    // Validate cases against the docs BEFORE contacting the assistant.
    KnowledgeBase kb = getKnowledgeBase(out, config);
    if (kb == null) {
      return 2;
    }
    List<EvalCase> cases = getEvalCases(kb, config);

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

    SuiteReport.Calibration calibration = SuiteReport.Calibration.SKIPPED;
    if (!skipCalibration) {
      List<TrapPairs.TrapResult> trapResults =
          trapFile == null ? TrapPairs.runBundled(judge) : TrapPairs.run(trapFile, judge);
      LabeledSample.Result groundedness =
          sampleFile == null
              ? LabeledSample.runBundled(judge)
              : LabeledSample.run(sampleFile, judge);
      calibration = new SuiteReport.Calibration(true, trapResults, groundedness);
    }

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
    Path reportFile = ReportWriter.write(config.outputDir(), report).normalize();
    Path rootDir = root.normalize();
    out.println(
        "Report: "
            + (reportFile.startsWith(rootDir) ? rootDir.relativize(reportFile) : reportFile));
    return report.exitCode();
  }

  private static List<EvalCase> getEvalCases(KnowledgeBase kb, EvalConfig config)
      throws IOException {
    return EvalCaseLoader.load(config.casesDir(), kb, config.categories());
  }

  private static KnowledgeBase getKnowledgeBase(PrintStream out, EvalConfig config)
      throws IOException {
    KnowledgeBase kb;
    try {
      kb =
          new KnowledgeBase(
              KnowledgeSources.from(config.raw(), config.baseDir(), config.fileName()));

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

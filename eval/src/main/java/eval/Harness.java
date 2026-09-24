package eval;

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
    for (int i = 0; i < args.length; i++) {
      if (args[i].equals("--endpoint") && i + 1 < args.length && !args[i + 1].startsWith("--")) {
        endpointArg = args[++i];
      } else {
        out.println(
            "ERROR: "
                + (args[i].equals("--endpoint")
                    ? "--endpoint needs a value"
                    : "unknown argument '" + args[i] + "'"));
        out.println("Usage: Harness [--endpoint <url>]");
        return 2;
      }
    }
    EvalConfig config = EvalConfig.load(root, endpointArg);

    // Validate cases against the docs BEFORE contacting the assistant.
    KnowledgeBase kb;
    List<EvalCase> cases;
    try {
      kb = new KnowledgeBase(KnowledgeSources.from(config.raw(), root));
      cases = EvalCaseLoader.load(root.resolve("eval/cases"), kb, config.categories());
    } catch (IllegalArgumentException e) {
      out.println("ERROR: " + e.getMessage());
      return 2;
    }

    String judgeModel = config.requireJudgeModel();
    Judge judge =
        new Judge(
            judgeLlm.apply(judgeModel)); // throws with the export hint if the API key is missing
    List<Registered> checks = Checks.registered(kb, judge);
    AssistantClient client = new AssistantClient(config.endpoint());
    if (!client.reachable()) {
      out.println("ERROR: cannot reach the assistant at " + config.endpoint());
      out.println("Start the stub in another terminal first:");
      out.println("  export OPENROUTER_API_KEY=...");
      out.println("  mvn -q -DskipTests package");
      out.println("  java -jar assistant/target/assistant.jar");
      return 2;
    }

    String runId = ReportWriter.newRunId();
    CaseRunner runner = new CaseRunner(client, checks);
    List<CaseResult> caseResults = new ArrayList<>();
    for (EvalCase evalCase : cases) {
      caseResults.add(runner.run(evalCase, runId));
    }
    SuiteReport report = new SuiteReport(runId, config.endpoint(), config.passFloor(), checkInfos(checks), caseResults);

    ConsoleReport.print(report, out);
    ReportWriter.write(root, report);
    out.println("Report: caseResults/" + runId + ".json");
    return report.exitCode();
  }

  private static List<CheckInfo> checkInfos(List<Registered> checks) {
    return checks.stream()
        .map(registered -> new CheckInfo(registered.check().name(), registered.gating()))
        .toList();
  }
}

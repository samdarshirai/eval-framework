package eval;

import eval.checks.*;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  /** The cases named by ids, in file order; all of them when ids is empty. Unknown id throws. */
  static List<EvalCase> selectCases(List<EvalCase> cases, List<String> ids) {
    if (ids.isEmpty()) {
      return cases;
    }
    List<String> known = cases.stream().map(EvalCase::id).toList();
    List<String> unknown = ids.stream().filter(id -> !known.contains(id)).toList();
    if (!unknown.isEmpty()) {
      throw new IllegalArgumentException(
          "unknown case id(s): "
              + String.join(", ", unknown)
              + " (known: "
              + String.join(", ", known)
              + ")");
    }
    return cases.stream().filter(evalCase -> ids.contains(evalCase.id())).toList();
  }

  /** Wiring only: parse args, load config and cases, preflight, run each case, report. */
  private static int runInner(
      String[] args, Path root, PrintStream out, Function<String, Llm> judgeLlm) throws Exception {
    long startedNanos = System.nanoTime();
    UsageMeter meter = new UsageMeter();
    EvalConfig config = EvalConfig.from(root, CliArgs.parse(args));
    DebugLog debug = config.debug() ? new DebugLog(out) : DebugLog.OFF;
    debug.log(
        "config: endpoint="
            + config.endpoint()
            + ", judgeModel="
            + config.raw().get("judgeModel")
            + ", passFloor="
            + config.passFloor()
            + ", categories="
            + config.categories()
            + ", cases="
            + config.casesDir()
            + ", outputDir="
            + config.outputDir()
            + ", skipCalibration="
            + config.skipCalibration()
            + ", baseline="
            + config.baselineFile());
    UsageMeter.Pricing pricing = config.judgePricing(); // a bad block exits 2 before any call
    Baseline baseline = Baseline.resolve(config, out);
    CalibrationRun.preflight(config);

    // Validate cases against the docs BEFORE contacting the assistant.
    KnowledgeBase kb = KnowledgeBase.from(config);
    debug.log("knowledge base: " + kb.ids().size() + " chunks");
    List<EvalCase> allCases = EvalCaseLoader.load(config.casesDir(), kb, config.categories());
    List<EvalCase> cases = selectCases(allCases, config.caseIds());
    if (cases.size() < allCases.size()) {
      String partial =
          "Partial run: " + cases.size() + " of " + allCases.size() + " cases " + config.caseIds();
      out.println(partial);
      debug.log(partial);
    }
    debug.log(
        "cases: "
            + cases.size()
            + " loaded, by category "
            + cases.stream()
                .collect(
                    Collectors.groupingBy(
                        EvalCase::category, TreeMap::new, Collectors.counting())));
    if (baseline != null) {
      baseline.requireAnyOf(cases);
    }
    for (String warning : StaleCases.warnings(cases, kb)) {
      out.println("WARNING: " + warning);
    }

    // Throws with the export hint if the API key is missing.
    Judge judge =
        new Judge(new MeteredLlm(judgeLlm.apply(config.requireJudgeModel()), meter, debug));
    List<Registered> checks = Checks.registered(kb, judge);
    AssistantClient client = new AssistantClient(config.endpoint(), debug);
    client.requireReachable();
    meter.setCheck("calibration");
    debug.log("calibration " + (config.skipCalibration() ? "skipped" : "start"));
    SuiteReport.Calibration calibration = CalibrationRun.run(config, judge);
    meter.setCheck(null);
    debug.log("calibration done");

    String runId = ReportWriter.newRunId();
    debug.log("run " + runId + " start");
    SuiteRunner.Outcome outcome =
        new SuiteRunner(new CaseRunner(client, checks, meter, debug), debug)
            .run(cases, runId, baseline);
    SuiteReport.Usage usage = meter.usage((System.nanoTime() - startedNanos) / 1_000_000, pricing);
    SuiteReport report =
        new SuiteReport(
            runId,
            config.endpoint(),
            config.passFloor(),
            CheckInfo.of(checks),
            calibration,
            outcome.results(),
            outcome.comparison(),
            usage);
    ConsoleReport.print(report, out);
    ReportWriter.writeAndAnnounce(config.outputDir(), report, root, out);
    return report.exitCode();
  }
}

package eval;

import com.fasterxml.jackson.databind.*;
import eval.checks.*;
import eval.knowledge.KnowledgeSources;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import llm.*;
import org.yaml.snakeyaml.Yaml;

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
    Map<String, Object> cfg = new Yaml().load(Files.readString(root.resolve("eval/config.yaml")));
    String endpoint = endpointArg != null ? endpointArg : (String) cfg.get("endpoint");
    double floor = ((Number) cfg.get("passFloor")).doubleValue();

    List<String> categories = categoriesFrom(cfg);

    // Validate cases against the docs BEFORE contacting the assistant.
    KnowledgeBase kb;
    List<EvalCase> cases;
    try {
      kb = new KnowledgeBase(KnowledgeSources.from(cfg, root));
      cases = EvalCaseLoader.load(root.resolve("eval/cases"), kb, categories);
    } catch (IllegalArgumentException e) {
      out.println("ERROR: " + e.getMessage());
      return 2;
    }

    if (!(cfg.get("judgeModel") instanceof String judgeModel) || judgeModel.isBlank()) {
      throw new IllegalArgumentException(
          "eval/config.yaml: 'judgeModel' is required (an OpenRouter model slug)");
    }
    Judge judge =
        new Judge(
            judgeLlm.apply(judgeModel)); // throws with the export hint if the API key is missing
    List<Registered> checks = Checks.registered(kb, judge);
    AssistantClient client = new AssistantClient(endpoint);
    if (!client.reachable()) {
      out.println("ERROR: cannot reach the assistant at " + endpoint);
      out.println("Start the stub in another terminal first:");
      out.println("  export OPENROUTER_API_KEY=...");
      out.println("  mvn -q -DskipTests package");
      out.println("  java -jar assistant/target/assistant.jar");
      return 2;
    }

    String runId =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now());
    List<CaseResult> caseResults = new ArrayList<>();
    for (EvalCase evalCase : cases) {
      caseResults.add(runCase(evalCase, client, runId, checks));
    }
    SuiteReport report = new SuiteReport(runId, endpoint, floor, checkInfos(checks), caseResults);

    print(report, out);
    Files.createDirectories(root.resolve("caseResults"));
    new ObjectMapper()
        .writerWithDefaultPrettyPrinter()
        .writeValue(root.resolve("caseResults/" + runId + ".json").toFile(), report);
    out.println("Report: caseResults/" + runId + ".json");
    return report.exitCode();
  }

  /**
   * The allowed case categories from config; 'out-of-scope' must stay because the exit rule depends
   * on it.
   */
  private static List<String> categoriesFrom(Map<String, Object> cfg) {
    if (!(cfg.get("categories") instanceof List<?> raw) || raw.isEmpty()) {
      throw new IllegalArgumentException("eval/config.yaml: 'categories' must be a non-empty list");
    }
    List<String> categories = raw.stream().map(String::valueOf).toList();
    if (!categories.contains(SuiteReport.OUT_OF_SCOPE)) {
      throw new IllegalArgumentException(
          "eval/config.yaml: 'categories' must include '"
              + SuiteReport.OUT_OF_SCOPE
              + "' (the out-of-scope exit rule depends on it)");
    }
    return categories;
  }

  private static List<CheckInfo> checkInfos(List<Registered> checks) {
    return checks.stream()
        .map(registered -> new CheckInfo(registered.check().name(), registered.gating()))
        .toList();
  }

  /** The case's expectation in the assistant's response shape, for the report. */
  private static Expected expected(EvalCase evalCase) {
    return new Expected(
        evalCase.expectedBehavior().equals("refuse"),
        evalCase.facts().stream()
            .map(fact -> new Expected.ExpectedClaim(fact.fact(), fact.chunks(), fact.keywords()))
            .toList());
  }

  private static CaseResult runCase(
      EvalCase evalCase, AssistantClient client, String runId, List<Registered> checks) {
    Answer answer;
    try {
      answer = client.ask(evalCase.question(), runId);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return new CaseResult(
          evalCase.id(),
          evalCase.question(),
          evalCase.category(),
          evalCase.subtype(),
          expected(evalCase),
          false,
          "interrupted",
          null,
          List.of());
    } catch (Exception e) {
      return new CaseResult(
          evalCase.id(),
          evalCase.question(),
          evalCase.category(),
          evalCase.subtype(),
          expected(evalCase),
          false,
          e.getMessage(),
          null,
          List.of());
    }
    List<CheckOutcome> outcomes = new ArrayList<>();
    boolean passed = true;
    // Checks.registered(kb, judge) (eval/checks/Checks.java) is the single list of every check the
    // harness runs, in run order.
    // A check is a small class implementing Check: it takes the eval case, the assistant's answer
    // and the per-case
    // CaseState and returns pass/fail plus a reason (currently Refusal, Citation integrity and
    // Coverage).
    // Each entry is wrapped in Registered with a gating flag: a failing gating check fails the
    // case, an advisory
    // one is only reported. Nothing here names a specific check, so adding one is a new class plus
    // one line in Checks.
    CaseState state = new CaseState();
    for (Registered registered : checks) {
      CheckResult checkResult;
      try {
        checkResult = registered.check().run(evalCase, answer, state);
      } catch (RuntimeException e) {
        checkResult =
            CheckResult.fail(
                "check error: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
      }
      outcomes.add(
          new CheckOutcome(registered.check().name(), checkResult.passed(), checkResult.reason()));
      if (registered.gating() && !checkResult.passed()) {
        passed = false;
      }
    }
    return new CaseResult(
        evalCase.id(),
        evalCase.question(),
        evalCase.category(),
        evalCase.subtype(),
        expected(evalCase),
        passed,
        null,
        answer,
        outcomes);
  }

  private static void print(SuiteReport report, PrintStream out) {
    out.println("Endpoint: " + report.endpoint() + "  Run: " + report.runId());
    for (CaseResult caseResult : report.cases()) {
      out.printf(
          "%-4s %-22s %-14s%n",
          caseResult.passed() ? "PASS" : "FAIL", caseResult.id(), caseResult.category());
      if (caseResult.error() != null) {
        out.println("       assistant error: " + caseResult.error());
      }
      for (CheckOutcome outcome : caseResult.checks()) {
        if (!outcome.passed()) {
          out.println(
              "       "
                  + outcome.check()
                  + (report.isGating(outcome.check()) ? "" : " (advisory)")
                  + ": "
                  + outcome.reason());
        }
      }
    }
    out.printf(
        "%nPass rate: %d/%d (%.1f%%), floor %.1f%%%n",
        report.passed(), report.cases().size(), report.passRate() * 100, report.passFloor() * 100);
    if (report.exitCode() == 0) {
      out.println("RESULT: OK");
    } else {
      report.exitReasons().forEach(reason -> out.println("RESULT: FAIL - " + reason));
    }
  }
}

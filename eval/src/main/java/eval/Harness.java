package eval;

import com.fasterxml.jackson.databind.*;
import eval.checks.*;
import eval.knowledge.KnowledgeSources;
import org.yaml.snakeyaml.Yaml;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class Harness {

    public static void main(String[] args) throws Exception {
        System.exit(run(args, Path.of("."), System.out));
    }

    static int run(String[] args, Path root, PrintStream out) throws Exception {
        try {
            return runInner(args, root, out);
        } catch (IOException | RuntimeException e) {
            out.println("ERROR: " + (e.getMessage() != null ? e.getMessage() : e));
            return 2;
        }
    }

    private static int runInner(String[] args, Path root, PrintStream out) throws Exception {
        String endpointArg = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--endpoint") && i + 1 < args.length && !args[i + 1].startsWith("--")) endpointArg = args[++i];
            else {
                out.println("ERROR: " + (args[i].equals("--endpoint") ? "--endpoint needs a value" : "unknown argument '" + args[i] + "'"));
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

        List<Registered> checks = Checks.registered(kb);
        AssistantClient client = new AssistantClient(endpoint);
        if (!client.reachable()) {
            out.println("ERROR: cannot reach the assistant at " + endpoint);
            out.println("Start the stub in another terminal first:");
            out.println("  export OPENROUTER_API_KEY=...");
            out.println("  mvn -q -DskipTests package");
            out.println("  java -jar assistant/target/assistant.jar");
            return 2;
        }

        String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        List<CaseResult> caseResults = new ArrayList<>();
        for (EvalCase c : cases) {
            caseResults.add(runCase(c, client, runId, checks));
        }
        SuiteReport report = new SuiteReport(runId, endpoint, floor, checkInfos(checks), caseResults);

        print(report, out);
        Files.createDirectories(root.resolve("caseResults"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(root.resolve("caseResults/" + runId + ".json").toFile(), report);
        out.println("Report: caseResults/" + runId + ".json");
        return report.exitCode();
    }

    /** The allowed case categories from config; 'out-of-scope' must stay because the exit rule depends on it. */
    private static List<String> categoriesFrom(Map<String, Object> cfg) {
        if (!(cfg.get("categories") instanceof List<?> raw) || raw.isEmpty())
            throw new IllegalArgumentException("eval/config.yaml: 'categories' must be a non-empty list");
        List<String> categories = raw.stream().map(String::valueOf).toList();
        if (!categories.contains(SuiteReport.OUT_OF_SCOPE))
            throw new IllegalArgumentException("eval/config.yaml: 'categories' must include '" + SuiteReport.OUT_OF_SCOPE + "' (the out-of-scope exit rule depends on it)");
        return categories;
    }

    private static List<CheckInfo> checkInfos(List<Registered> checks) {
        return checks.stream().map(r -> new CheckInfo(r.check().name(), r.gating())).toList();
    }

    /** The case's expectation in the assistant's response shape, for the report. */
    private static Expected expected(EvalCase c) {
        return new Expected(c.expectedBehavior().equals("refuse"),
            c.facts().stream().map(f -> new Expected.ExpectedClaim(f.fact(), f.chunks(), f.keywords())).toList());
    }

    private static CaseResult runCase(EvalCase c, AssistantClient client, String runId, List<Registered> checks) {
        Answer answer;
        try {
            answer = client.ask(c.question(), runId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CaseResult(c.id(), c.question(), c.category(), c.subtype(), expected(c), false, "interrupted", null, List.of());
        } catch (Exception e) {
            return new CaseResult(c.id(), c.question(), c.category(), c.subtype(), expected(c), false, e.getMessage(), null, List.of());
        }
        List<CheckOutcome> outcomes = new ArrayList<>();
        boolean passed = true;
        // Checks.registered() (eval/checks/Checks.java) is the single list of every check the harness runs, in run order.
        // A check is a small class implementing Check: it takes the eval case and the assistant's answer and returns
        // pass/fail plus a reason (today only RefusalCheck: hallucination, over-refusal, contract violation).
        // Each entry is wrapped in Registered with a gating flag: a failing gating check fails the case, an advisory
        // one is only reported. Nothing here names a specific check, so adding one is a new class plus one line in Checks.
        CaseState state = new CaseState();
        for (Registered r : checks) {
            CheckResult res = r.check().run(c, answer, state);
            outcomes.add(new CheckOutcome(r.check().name(), res.passed(), res.reason()));
            if (r.gating() && !res.passed()) passed = false;
        }
        return new CaseResult(c.id(), c.question(), c.category(), c.subtype(), expected(c), passed, null, answer, outcomes);
    }

    private static void print(SuiteReport r, PrintStream out) {
        out.println("Endpoint: " + r.endpoint() + "  Run: " + r.runId());
        for (CaseResult c : r.cases()) {
            out.printf("%-4s %-22s %-14s%n", c.passed() ? "PASS" : "FAIL", c.id(), c.category());
            if (c.error() != null) out.println("       assistant error: " + c.error());
            for (CheckOutcome o : c.checks())
                if (!o.passed()) out.println("       " + o.check() + (r.isGating(o.check()) ? "" : " (advisory)") + ": " + o.reason());
        }
        out.printf("%nPass rate: %d/%d (%.1f%%), floor %.1f%%%n", r.passed(), r.cases().size(), r.passRate() * 100, r.passFloor() * 100);
        if (r.exitCode() == 0) out.println("RESULT: OK");
        else r.exitReasons().forEach(x -> out.println("RESULT: FAIL - " + x));
    }
}

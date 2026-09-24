package eval;

import com.fasterxml.jackson.databind.*;
import eval.checks.*;
import org.yaml.snakeyaml.Yaml;
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
        Map<String, Object> cfg = new Yaml().load(Files.readString(root.resolve("eval/config.yaml")));
        String endpoint = (String) cfg.get("endpoint");
        double floor = ((Number) cfg.get("passFloor")).doubleValue();
        for (int i = 0; i + 1 < args.length; i++) if (args[i].equals("--endpoint")) endpoint = args[i + 1];

        // Validate cases against the docs BEFORE contacting the assistant.
        List<EvalCase> cases;
        try {
            cases = EvalCaseLoader.load(root.resolve("eval/cases"), new KnowledgeBase(root.resolve("docs")));
        } catch (IllegalArgumentException e) {
            out.println("ERROR: " + e.getMessage());
            return 2;
        }

        AssistantClient client = new AssistantClient(endpoint);
        if (!client.reachable()) {
            out.println("ERROR: cannot reach the assistant at " + endpoint);
            out.println("Start the stub in another terminal first:");
            out.println("  export ANTHROPIC_API_KEY=...");
            out.println("  mvn -q compile exec:java -Dexec.mainClass=assistant.StubServer");
            return 2;
        }

        String runId = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now());
        List<CaseResult> results = new ArrayList<>();
        for (EvalCase c : cases) results.add(runCase(c, client, runId));
        SuiteReport report = new SuiteReport(runId, endpoint, floor, results);

        print(report, out);
        Files.createDirectories(root.resolve("results"));
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(root.resolve("results/" + runId + ".json").toFile(), report);
        out.println("Report: results/" + runId + ".json");
        return report.exitCode();
    }

    private static CaseResult runCase(EvalCase c, AssistantClient client, String runId) {
        Answer answer;
        try {
            answer = client.ask(c.question(), runId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CaseResult(c.id(), c.category(), c.subtype(), c.expectedBehavior(), false, "interrupted", null, List.of());
        } catch (Exception e) {
            return new CaseResult(c.id(), c.category(), c.subtype(), c.expectedBehavior(), false, e.getMessage(), null, List.of());
        }
        List<CheckOutcome> outcomes = new ArrayList<>();
        boolean passed = true;
        for (Registered r : Checks.registered()) {
            CheckResult res = r.check().run(c, answer);
            outcomes.add(new CheckOutcome(r.check().name(), r.gating(), res.passed(), res.reason()));
            if (r.gating() && !res.passed()) passed = false;
        }
        return new CaseResult(c.id(), c.category(), c.subtype(), c.expectedBehavior(), passed, null, answer, outcomes);
    }

    private static void print(SuiteReport r, PrintStream out) {
        for (CaseResult c : r.cases()) {
            out.printf("%-4s %-22s %-14s%n", c.passed() ? "PASS" : "FAIL", c.id(), c.category());
            if (c.error() != null) out.println("       assistant error: " + c.error());
            for (CheckOutcome o : c.checks())
                if (!o.passed()) out.println("       " + o.check() + (o.gating() ? "" : " (advisory)") + ": " + o.reason());
        }
        out.printf("%nPass rate: %d/%d (%.1f%%), floor %.1f%%%n", r.passed(), r.cases().size(), r.passRate() * 100, r.passFloor() * 100);
        if (r.exitCode() == 0) out.println("RESULT: OK");
        else r.exitReasons().forEach(x -> out.println("RESULT: FAIL - " + x));
    }
}

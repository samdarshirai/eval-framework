package eval;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class HarnessTest {
    @TempDir Path root;
    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile String replyFor = "{\"refused\":true,\"claims\":[]}";
    private volatile int status = 200;

    @BeforeEach void setUp() throws Exception {
        Files.createDirectories(root.resolve("docs"));
        Files.createDirectories(root.resolve("eval/cases"));
        Files.writeString(root.resolve("docs/d.md"), "## A\nbody\n");
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/answer", ex -> {
            requests.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            byte[] b = replyFor.getBytes();
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        config("endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source, out-of-scope]\n");
    }
    @AfterEach void tearDown() { server.stop(0); }
    private String url() { return "http://localhost:" + server.getAddress().getPort() + "/answer"; }

    /** Writes eval/config.yaml with the given body plus a judgeModel, so tests that vary other keys stay valid. */
    private void config(String body) throws IOException {
        Files.writeString(root.resolve("eval/config.yaml"), body + "judgeModel: test/judge\n");
    }

    private void cases(String yaml) throws Exception { Files.writeString(root.resolve("eval/cases/c.yaml"), yaml); }
    private static final String OOS = "- id: oos-1\n  question: How much?\n  category: out-of-scope\n  subtype: unrelated\n  expected_behavior: refuse\n  source: authored\n  owner: p\n  added: \"2026-09-24\"\n";

    private String[] out(int[] code, String... args) throws Exception {
        var buf = new ByteArrayOutputStream();
        code[0] = Harness.run(args, root, new PrintStream(buf), model -> (s, u) -> "YES");
        return new String[]{buf.toString()};
    }

    @Test void refusedOutOfScopePassesAndWritesTimestampedReport() throws Exception {
        cases(OOS);
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(0, code[0], o);
        assertTrue(o.contains("PASS") && o.contains("oos-1"));
        try (var s = Files.list(root.resolve("caseResults"))) { assertEquals(1, s.filter(p -> p.toString().endsWith(".json")).count()); }
    }

    @Test void reportHasQuestionAndExpectedNextToActualAndListsChecksOnce() throws Exception {
        cases("- id: c1\n  question: What is A?\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
        replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(0, code[0], o);
        var m = java.util.regex.Pattern.compile("Report: (\\S+)").matcher(o);
        assertTrue(m.find(), o);
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(root.resolve(m.group(1)).toFile());
        var c = json.get("cases").get(0);
        assertEquals("What is A?", c.get("question").asText());
        assertFalse(c.get("expected").get("refused").asBoolean());
        assertEquals("A is body", c.get("expected").get("claims").get(0).get("claim").asText());
        assertEquals("d#a", c.get("expected").get("claims").get(0).get("citations").get(0).asText());
        assertEquals("body", c.get("expected").get("claims").get(0).get("keywords").get(0).asText());
        assertEquals("d#a", c.get("actual").get("claims").get(0).get("citations").get(0).asText());
        assertFalse(c.has("answer") || c.has("expectedBehavior") || c.has("expectedFacts"));
        assertEquals("Refusal", json.get("checks").get(0).get("name").asText());
        assertTrue(json.get("checks").get(0).get("gating").asBoolean());
        assertFalse(c.get("checks").get(0).has("gating"), "gating is listed once at the top, not per case");
    }

    @Test void confidentAnswerToOutOfScopeFailsRunAndSaysHallucination() throws Exception {
        cases(OOS);
        replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"It costs 5 EUR\",\"citations\":[\"d#a\"]}]}";
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(1, code[0]);
        assertTrue(o.contains("answered where it should have refused"), o);
        assertTrue(o.contains("out-of-scope case failed: oos-1"), o);
    }

    @Test void assistantHttpErrorFailsThatCaseButRunCompletesWithReport() throws Exception {
        cases(OOS);
        status = 500; replyFor = "{\"error\":\"boom\"}";
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(1, code[0]);
        assertTrue(o.contains("HTTP 500"), o);
        assertTrue(Files.exists(root.resolve("caseResults")));
    }

    @Test void missingGoldChunkStopsBeforeAnyAssistantCall() throws Exception {
        cases("- id: c1\n  question: Q?\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: F, chunks: [d#nope]}\n  source: a\n  owner: p\n  added: \"2026-09-24\"\n");
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0]);
        assertTrue(o.contains("c1") && o.contains("d#nope"), o);
        assertEquals(0, requests.get(), "no request may reach the assistant");
    }

    @Test void unreachableEndpointExplainsHowToStartTheStub() throws Exception {
        cases(OOS);
        server.stop(0);
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0]);
        assertTrue(o.contains("assistant/target/assistant.jar"), o);
        assertFalse(o.contains("ConnectException"), o);
    }

    @Test void endpointFlagOverridesConfig() throws Exception {
        cases(OOS);
        config("endpoint: http://localhost:1/answer\npassFloor: 0.90\ncategories: [single-source, out-of-scope]\n");
        int[] code = new int[1];
        out(code, "--endpoint", url());
        assertEquals(0, code[0]);
    }

    @Test void missingConfigExitsTwoWithErrorNotAStackTrace() throws Exception {
        Files.delete(root.resolve("eval/config.yaml"));
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("ERROR"), o);
        assertFalse(o.contains("\tat "), o);
    }

    @Test void missingCasesDirExitsTwoWithError() throws Exception {
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("ERROR"), o);
    }

    @Test void configWithoutPassFloorExitsTwoWithError() throws Exception {
        cases(OOS);
        config("endpoint: " + url() + "\ncategories: [out-of-scope]\n");
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("ERROR"), o);
    }

    @Test void configWithoutOutOfScopeCategoryExitsTwoBecauseTheExitRuleNeedsIt() throws Exception {
        cases(OOS);
        config("endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source]\n");
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("out-of-scope"), o);
        assertEquals(0, requests.get());
    }

    @Test void configWithoutCategoriesExitsTwo() throws Exception {
        cases(OOS);
        config("endpoint: " + url() + "\npassFloor: 0.90\n");
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("categories"), o);
    }

    @Test void unimplementedKnowledgeSourceExitsTwoBeforeAnyAssistantCall() throws Exception {
        cases(OOS);
        config("endpoint: " + url() + "\npassFloor: 0.90\ncategories: [out-of-scope]\n"
            + "knowledgeBase:\n  type: http\n  url: http://localhost:1/chunks\n");
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("not implemented yet"), o);
        assertEquals(0, requests.get());
    }

    @Test void unknownArgExitsTwoWithUsageBeforeTouchingNetwork() throws Exception {
        cases(OOS);
        int[] code = new int[1];
        String o = out(code, "--endpiont", url())[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("ERROR") && o.contains("Usage"), o);
        assertEquals(0, requests.get());
    }

    @Test void endpointWithoutValueExitsTwo() throws Exception {
        cases(OOS);
        int[] code = new int[1];
        String o = out(code, "--endpoint")[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("ERROR") && o.contains("Usage"), o);
        o = out(code, "--endpoint", "--other")[0];
        assertEquals(2, code[0], o);
    }

    @Test void endpointEqualsFormExitsTwo() throws Exception {
        cases(OOS);
        int[] code = new int[1];
        String o = out(code, "--endpoint=http://x")[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("ERROR") && o.contains("Usage"), o);
    }

    @Test void outputShowsWhichEndpointAndRunRan() throws Exception {
        cases(OOS);
        int[] code = new int[1];
        String o = out(code)[0];
        assertTrue(o.contains("Endpoint: " + url() + "  Run: "), o);
    }

    @Test void fabricatedCitationFailsTheCaseEndToEndAndTheReasonIsPrinted() throws Exception {
        cases("- id: c1\n  question: q\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
        replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#nope\"]}]}";
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(1, code[0], o);
        assertTrue(o.contains("Citation integrity: fabricated citation: d#nope"), o);
    }

    @Test void missingJudgeModelExitsTwoBeforeCallingTheAssistant() throws Exception {
        Files.writeString(root.resolve("eval/config.yaml"), "endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source, out-of-scope]\n");
        cases(OOS);
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0]);
        assertTrue(o.contains("judgeModel"), o);
        assertEquals(0, requests.get());
    }

    @Test void missingApiKeyExitsTwoWithTheExportHintBeforeCallingTheAssistant() throws Exception {
        cases(OOS);
        var buf = new ByteArrayOutputStream();
        int code = Harness.run(new String[0], root, new PrintStream(buf), model -> { throw new IllegalStateException("OPENROUTER_API_KEY is not set. Run: export OPENROUTER_API_KEY=..."); });
        assertEquals(2, code, buf.toString());
        assertTrue(buf.toString().contains("OPENROUTER_API_KEY"), buf.toString());
        assertFalse(buf.toString().contains("\tat "), buf.toString());
        assertEquals(0, requests.get());
    }

    @Test void judgeFailureFailsTheCaseWithCheckErrorAndTheRunStillWritesAReport() throws Exception {
        cases("- id: c1\n  question: q\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
        replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
        var buf = new ByteArrayOutputStream();
        int code = Harness.run(new String[0], root, new PrintStream(buf), model -> (s, u) -> { throw new IllegalStateException("boom"); });
        assertEquals(1, code, buf.toString());
        assertTrue(buf.toString().contains("check error: boom"), buf.toString());
        try (var s = Files.list(root.resolve("caseResults"))) { assertEquals(1, s.filter(p -> p.toString().endsWith(".json")).count()); }
    }

    @Test void checkErrorWithNullMessageFallsBackToExceptionString() throws Exception {
        cases("- id: c1\n  question: q\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
        replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
        var buf = new ByteArrayOutputStream();
        Harness.run(new String[0], root, new PrintStream(buf), model -> (s, u) -> { throw new IllegalStateException(); });
        assertTrue(buf.toString().contains("check error: java.lang.IllegalStateException"), buf.toString());
    }

    @Test void negatedClaimWithAllKeywordsFailsCoverageEndToEnd() throws Exception {
        cases("- id: c1\n  question: q\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
        replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"Everything in A except the body\",\"citations\":[\"d#a\"]}]}";
        var judgeCalls = new int[1];
        var buf = new ByteArrayOutputStream();
        int code = Harness.run(new String[0], root, new PrintStream(buf), model -> (s, u) -> { judgeCalls[0]++; return "NO"; });
        assertEquals(1, code, buf.toString());
        assertTrue(buf.toString().contains("Coverage: not covered"), buf.toString());
        assertEquals(1, judgeCalls[0]);
    }
}

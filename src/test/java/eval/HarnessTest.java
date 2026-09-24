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
        Files.writeString(root.resolve("eval/config.yaml"), "endpoint: " + url() + "\npassFloor: 0.90\n");
    }
    @AfterEach void tearDown() { server.stop(0); }
    private String url() { return "http://localhost:" + server.getAddress().getPort() + "/answer"; }

    private void cases(String yaml) throws Exception { Files.writeString(root.resolve("eval/cases/c.yaml"), yaml); }
    private static final String OOS = "- id: oos-1\n  question: How much?\n  category: out-of-scope\n  subtype: unrelated\n  expected_behavior: refuse\n  source: authored\n  owner: p\n  added: \"2026-09-24\"\n";

    private String[] out(int[] code, String... args) throws Exception {
        var buf = new ByteArrayOutputStream();
        code[0] = Harness.run(args, root, new PrintStream(buf));
        return new String[]{buf.toString()};
    }

    @Test void refusedOutOfScopePassesAndWritesTimestampedReport() throws Exception {
        cases(OOS);
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(0, code[0], o);
        assertTrue(o.contains("PASS") && o.contains("oos-1"));
        try (var s = Files.list(root.resolve("results"))) { assertEquals(1, s.filter(p -> p.toString().endsWith(".json")).count()); }
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
        assertTrue(Files.exists(root.resolve("results")));
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
        assertTrue(o.contains("assistant.StubServer"), o);
        assertFalse(o.contains("ConnectException"), o);
    }

    @Test void endpointFlagOverridesConfig() throws Exception {
        cases(OOS);
        Files.writeString(root.resolve("eval/config.yaml"), "endpoint: http://localhost:1/answer\npassFloor: 0.90\n");
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
        Files.writeString(root.resolve("eval/config.yaml"), "endpoint: " + url() + "\n");
        int[] code = new int[1];
        String o = out(code)[0];
        assertEquals(2, code[0], o);
        assertTrue(o.contains("ERROR"), o);
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
}

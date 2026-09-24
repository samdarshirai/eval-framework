package eval;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** --config: a team's own config, docs, cases, output dir and trap pairs, away from the root. */
class HarnessConfigFileTest {
  @TempDir Path root; // the working directory: deliberately empty
  @TempDir Path team; // the team's directory holding everything
  private HttpServer server;

  private static final String OOS =
      "- id: oos-1\n"
          + "  question: How much?\n"
          + "  category: out-of-scope\n"
          + "  subtype: unrelated\n"
          + "  expected_behavior: refuse\n"
          + "  source: authored\n"
          + "  owner: p\n"
          + "  added: \"2026-09-24\"\n";

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/answer",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          byte[] reply = "{\"refused\":true,\"claims\":[]}".getBytes();
          exchange.sendResponseHeaders(200, reply.length);
          exchange.getResponseBody().write(reply);
          exchange.close();
        });
    server.start();
    Files.createDirectories(team.resolve("kb"));
    Files.writeString(team.resolve("kb/d.md"), "## A\nbody\n");
    Files.createDirectories(team.resolve("mycases"));
    Files.writeString(team.resolve("mycases/c.yaml"), OOS);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private String url() {
    return "http://localhost:" + server.getAddress().getPort() + "/answer";
  }

  private Path config(String extra) throws IOException {
    return Files.writeString(
        team.resolve("team.yaml"),
        "endpoint: http://localhost:1/answer\npassFloor: 0.9\ncategories: [out-of-scope]\n"
            + "judgeModel: test/judge\nknowledgeBase:\n  path: kb\n"
            + extra);
  }

  private String run(int[] code, String... args) throws Exception {
    var buf = new ByteArrayOutputStream();
    code[0] = Harness.run(args, root, new PrintStream(buf), model -> (system, user) -> "YES");
    return buf.toString();
  }

  @Test
  void everythingResolvesAgainstTheConfigDirNotTheWorkingRoot() throws Exception {
    Files.writeString(
        team.resolve("traps.yaml"), "- {name: same, fact: F, claim: F, agree: true}\n");
    Files.writeString(team.resolve("empty-sample.yaml"), "[]\n");
    Path configFile =
        config(
            "cases: mycases\noutputDir: out\ncalibration:\n  trapPairs: traps.yaml\n  labeledSample: empty-sample.yaml\n");
    int[] code = new int[1];
    String output = run(code, "--config", configFile.toString(), "--endpoint", url());
    assertEquals(0, code[0], output);
    assertTrue(output.contains("same") || output.contains("calibration"), output);
    try (var files = Files.list(team.resolve("out"))) {
      assertEquals(1, files.count());
    }
    assertTrue(output.contains("Report: " + team.resolve("out")), output);
    try (var files = Files.list(root)) {
      assertEquals(0, files.count(), "nothing may be written to the working root");
    }
  }

  @Test
  void casesAndOutputDirDefaultToCasesAndCaseResultsNextToTheConfig() throws Exception {
    Files.move(team.resolve("mycases"), team.resolve("cases"));
    int[] code = new int[1];
    String output =
        run(code, "--endpoint", url(), "--skip-calibration", "--config", config("").toString());
    assertEquals(0, code[0], output);
    assertTrue(Files.isDirectory(team.resolve("caseResults")), output);
  }

  @Test
  void bundledTrapPairsRunWhenNoOverrideKeyIsSet() throws Exception {
    Files.move(team.resolve("mycases"), team.resolve("cases"));
    int[] code = new int[1];
    String output = run(code, "--config", config("").toString(), "--endpoint", url());
    // The fake judge says YES to everything, so the 7 bundled pairs produce misses (exit 1).
    assertEquals(1, code[0], output);
    assertTrue(output.contains("judge calibration: 5 trap pair miss(es)"), output);
  }

  @Test
  void relativeConfigArgumentResolvesAgainstTheWorkingRoot() throws Exception {
    Files.move(team.resolve("mycases"), team.resolve("cases"));
    Path inRoot = Files.createDirectories(root.resolve("sub"));
    Files.copy(config(""), inRoot.resolve("t.yaml"));
    Files.move(team.resolve("kb"), inRoot.resolve("kb"));
    Files.move(team.resolve("cases"), inRoot.resolve("cases"));
    int[] code = new int[1];
    String output = run(code, "--config", "sub/t.yaml", "--skip-calibration", "--endpoint", url());
    assertEquals(0, code[0], output);
    assertTrue(output.contains("Report: sub/caseResults/"), output);
  }

  @Test
  void configWithoutValueExitsTwoWithUsage() throws Exception {
    int[] code = new int[1];
    String output = run(code, "--config");
    assertEquals(2, code[0]);
    assertTrue(
        output.contains("ERROR: --config needs a value") && output.contains("Usage"), output);
    output = run(code, "--config", "--skip-calibration");
    assertEquals(2, code[0], output);
  }

  @Test
  void nonexistentConfigFileExitsTwo() throws Exception {
    int[] code = new int[1];
    String output = run(code, "--config", "/nonexistent.yaml");
    assertEquals(2, code[0]);
    assertTrue(output.contains("ERROR: config file not found: /nonexistent.yaml"), output);
  }

  @Test
  void missingConfiguredTrapFileExitsTwoBeforeAnyCase() throws Exception {
    Path configFile = config("cases: mycases\ncalibration:\n  trapPairs: nope.yaml\n");
    int[] code = new int[1];
    String output = run(code, "--config", configFile.toString(), "--endpoint", url());
    assertEquals(2, code[0], output);
    assertTrue(output.contains("nope.yaml") && output.contains("--skip-calibration"), output);
  }
}

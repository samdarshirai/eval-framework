package eval;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class HarnessTest {
  @TempDir Path root;
  private HttpServer server;
  private final AtomicInteger requests = new AtomicInteger();
  private volatile String replyFor = "{\"refused\":true,\"claims\":[]}";
  private volatile int status = 200;

  /**
   * Reply used from the third request on: request 1 is the reachability ping, 2 the first attempt.
   */
  private volatile String replyFromRerun = null;

  @BeforeEach
  void setUp() throws Exception {
    Files.createDirectories(root.resolve("docs"));
    Files.createDirectories(root.resolve("eval/cases"));
    Files.writeString(root.resolve("docs/d.md"), "## A\nbody\n");
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/answer",
        exchange -> {
          requests.incrementAndGet();
          exchange.getRequestBody().readAllBytes();
          String reply = requests.get() >= 3 && replyFromRerun != null ? replyFromRerun : replyFor;
          byte[] replyBytes = reply.getBytes();
          exchange.sendResponseHeaders(status, replyBytes.length);
          exchange.getResponseBody().write(replyBytes);
          exchange.close();
        });
    server.start();
    trapFile("- {name: same, fact: F, claim: F, agree: true}\n");
    labeledSampleFile("[]\n");
    config("endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source, out-of-scope]\n");
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  private String url() {
    return "http://localhost:" + server.getAddress().getPort() + "/answer";
  }

  /**
   * Writes eval/config.yaml with the given body plus a judgeModel, so tests that vary other keys
   * stay valid.
   */
  private void config(String body) throws IOException {
    Files.writeString(
        root.resolve("eval/config.yaml"),
        body
            + "judgeModel: test/judge\n"
            + "calibration:\n"
            + "  trapPairs: calibration/trap-pairs.yaml\n"
            + "  labeledSample: calibration/labeled-sample.yaml\n");
  }

  private void labeledSampleFile(String yaml) throws IOException {
    Files.createDirectories(root.resolve("calibration"));
    Files.writeString(root.resolve("calibration/labeled-sample.yaml"), yaml);
  }

  private void trapFile(String yaml) throws IOException {
    Files.createDirectories(root.resolve("calibration"));
    Files.writeString(root.resolve("calibration/trap-pairs.yaml"), yaml);
  }

  private void cases(String yaml) throws Exception {
    Files.writeString(root.resolve("eval/cases/c.yaml"), yaml);
  }

  private static final String OOS =
      "- id: oos-1\n"
          + "  question: How much?\n"
          + "  category: out-of-scope\n"
          + "  subtype: unrelated\n"
          + "  expected_behavior: refuse\n"
          + "  source: authored\n"
          + "  owner: p\n"
          + "  added: \"2026-09-24\"\n";

  private String[] out(int[] code, String... args) throws Exception {
    var buf = new ByteArrayOutputStream();
    code[0] = Harness.run(args, root, new PrintStream(buf), model -> (system, user) -> "YES");
    return new String[] {buf.toString()};
  }

  @Test
  void refusedOutOfScopePassesAndWritesTimestampedReport() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("PASS") && output.contains("oos-1"));
    try (var files = Files.list(root.resolve("caseResults"))) {
      assertEquals(1, files.filter(path -> path.toString().endsWith(".json")).count());
    }
  }

  @Test
  void debugFlagLogsTheRunAndWithoutItNothingIsLogged() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String quiet = out(code)[0];
    assertFalse(quiet.contains("[debug]"), quiet);

    String output = out(code, "--debug")[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("[debug]") && output.contains("config: endpoint="), output);
    assertTrue(output.contains("case oos-1 [out-of-scope] start"), output);
    assertTrue(output.contains("case oos-1 check Refusal"), output);
    assertTrue(output.contains("assistant POST"), output);
    assertTrue(output.contains("judge call in calibration"), output);
    assertFalse(output.contains("Bearer"), output);
  }

  @Test
  void caseFlagRunsOnlyTheNamedCasesAndSaysSo() throws Exception {
    cases(OOS + OOS.replace("oos-1", "oos-2"));
    int[] code = new int[1];
    String output = out(code, "--case", "oos-2")[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("Partial run: 1 of 2 cases [oos-2]"), output);
    assertFalse(output.contains("oos-1"), output);
    assertTrue(output.contains("oos-2"), output);
  }

  @Test
  void unknownCaseIdExitsTwoNamingTheIdAndTheKnownOnes() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--case", "nope")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("unknown case id(s): nope (known: oos-1)"), output);
    assertEquals(0, requests.get(), "must fail before contacting the assistant");
  }

  @Test
  void aStaleCaseWarnsByNameAndStillRuns() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: What is A?\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n"
            + "  confirmed_hash: \"000000000000\"\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("WARNING: case 'c1':"), output);
    assertTrue(output.contains("PASS") && output.contains("c1"), output);
    assertTrue(requests.get() > 0, "the stale case must still run");
  }

  @Test
  void reportHasQuestionAndExpectedNextToActualAndListsChecksOnce() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: What is A?\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(0, code[0], output);
    var reportMatcher = java.util.regex.Pattern.compile("Report: (\\S+)").matcher(output);
    assertTrue(reportMatcher.find(), output);
    var json =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(root.resolve(reportMatcher.group(1)).toFile());
    var caseJson = json.get("cases").get(0);
    assertEquals("What is A?", caseJson.get("question").asText());
    assertFalse(caseJson.get("expected").get("refused").asBoolean());
    assertEquals("A is body", caseJson.get("expected").get("claims").get(0).get("claim").asText());
    assertEquals(
        "d#a", caseJson.get("expected").get("claims").get(0).get("citations").get(0).asText());
    assertEquals(
        "body", caseJson.get("expected").get("claims").get(0).get("keywords").get(0).asText());
    assertEquals(
        "d#a", caseJson.get("actual").get("claims").get(0).get("citations").get(0).asText());
    assertFalse(
        caseJson.has("answer")
            || caseJson.has("expectedBehavior")
            || caseJson.has("expectedFacts"));
    assertEquals("Refusal", json.get("checks").get(0).get("name").asText());
    assertTrue(json.get("checks").get(0).get("gating").asBoolean());
    assertFalse(
        caseJson.get("checks").get(0).has("gating"),
        "gating is listed once at the top, not per case");
  }

  @Test
  void confidentAnswerToOutOfScopeFailsRunAndSaysHallucination() throws Exception {
    cases(OOS);
    replyFor =
        "{\"refused\":false,\"claims\":[{\"claim\":\"It costs 5 EUR\",\"citations\":[\"d#a\"]}]}";
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(1, code[0]);
    assertTrue(output.contains("answered where it should have refused"), output);
    assertTrue(output.contains("out-of-scope case failed: oos-1"), output);
  }

  @Test
  void assistantHttpErrorFailsThatCaseButRunCompletesWithReport() throws Exception {
    cases(OOS);
    status = 500;
    replyFor = "{\"error\":\"boom\"}";
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(1, code[0]);
    assertTrue(output.contains("HTTP 500"), output);
    assertTrue(Files.exists(root.resolve("caseResults")));
  }

  @Test
  void missingGoldChunkStopsBeforeAnyAssistantCall() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: Q?\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: F, chunks: [d#nope]}\n"
            + "  source: a\n"
            + "  owner: p\n"
            + "  added: \"2026-09-24\"\n");
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0]);
    assertTrue(output.contains("c1") && output.contains("d#nope"), output);
    assertEquals(0, requests.get(), "no request may reach the assistant");
  }

  @Test
  void unreachableEndpointExplainsHowToStartTheStub() throws Exception {
    cases(OOS);
    server.stop(0);
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0]);
    assertTrue(output.contains("assistant/target/assistant.jar"), output);
    assertFalse(output.contains("ConnectException"), output);
  }

  @Test
  void endpointFlagOverridesConfig() throws Exception {
    cases(OOS);
    config(
        "endpoint: http://localhost:1/answer\n"
            + "passFloor: 0.90\n"
            + "categories: [single-source, out-of-scope]\n");
    int[] code = new int[1];
    out(code, "--endpoint", url());
    assertEquals(0, code[0]);
  }

  @Test
  void missingConfigExitsTwoWithErrorNotAStackTrace() throws Exception {
    Files.delete(root.resolve("eval/config.yaml"));
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR"), output);
    assertFalse(output.contains("\tat "), output);
  }

  @Test
  void missingCasesDirExitsTwoWithError() throws Exception {
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR"), output);
  }

  @Test
  void configWithoutPassFloorExitsTwoWithError() throws Exception {
    cases(OOS);
    config("endpoint: " + url() + "\ncategories: [out-of-scope]\n");
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR"), output);
  }

  @Test
  void configWithoutOutOfScopeCategoryExitsTwoBecauseTheExitRuleNeedsIt() throws Exception {
    cases(OOS);
    config("endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source]\n");
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("out-of-scope"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void configWithoutCategoriesExitsTwo() throws Exception {
    cases(OOS);
    config("endpoint: " + url() + "\npassFloor: 0.90\n");
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("categories"), output);
  }

  @Test
  void unimplementedKnowledgeSourceExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    config(
        "endpoint: "
            + url()
            + "\npassFloor: 0.90\ncategories: [out-of-scope]\n"
            + "knowledgeBase:\n  type: http\n  url: http://localhost:1/chunks\n");
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("not implemented yet"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void unknownArgExitsTwoWithUsageBeforeTouchingNetwork() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--endpiont", url())[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR") && output.contains("Usage"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void endpointWithoutValueExitsTwo() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--endpoint")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR") && output.contains("Usage"), output);
    output = out(code, "--endpoint", "--other")[0];
    assertEquals(2, code[0], output);
  }

  @Test
  void endpointEqualsFormExitsTwo() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--endpoint=http://x")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR") && output.contains("Usage"), output);
  }

  @Test
  void outputShowsWhichEndpointAndRunRan() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code)[0];
    assertTrue(output.contains("Endpoint: " + url() + "  Run: "), output);
  }

  @Test
  void fabricatedCitationFailsTheCaseEndToEndAndTheReasonIsPrinted() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: q\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor =
        "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#nope\"]}]}";
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(1, code[0], output);
    assertTrue(output.contains("Citation integrity: fabricated citation: d#nope"), output);
  }

  @Test
  void missingJudgeModelExitsTwoBeforeCallingTheAssistant() throws Exception {
    Files.writeString(
        root.resolve("eval/config.yaml"),
        "endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source, out-of-scope]\n");
    cases(OOS);
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0]);
    assertTrue(output.contains("judgeModel"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void missingApiKeyExitsTwoWithTheExportHintBeforeCallingTheAssistant() throws Exception {
    cases(OOS);
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[0],
            root,
            new PrintStream(buf),
            model -> {
              throw new IllegalStateException(
                  "OPENROUTER_API_KEY is not set. Run: export OPENROUTER_API_KEY=...");
            });
    assertEquals(2, code, buf.toString());
    assertTrue(buf.toString().contains("OPENROUTER_API_KEY"), buf.toString());
    assertFalse(buf.toString().contains("\tat "), buf.toString());
    assertEquals(0, requests.get());
  }

  @Test
  void judgeFailureFailsTheCaseWithCheckErrorAndTheRunStillWritesAReport() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: q\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[0],
            root,
            new PrintStream(buf),
            model ->
                (system, user) -> {
                  throw new IllegalStateException("boom");
                });
    assertEquals(1, code, buf.toString());
    assertTrue(buf.toString().contains("check error: boom"), buf.toString());
    try (var files = Files.list(root.resolve("caseResults"))) {
      assertEquals(1, files.filter(path -> path.toString().endsWith(".json")).count());
    }
  }

  @Test
  void checkErrorWithNullMessageFallsBackToExceptionString() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: q\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
    var buf = new ByteArrayOutputStream();
    Harness.run(
        new String[0],
        root,
        new PrintStream(buf),
        model ->
            (system, user) -> {
              throw new IllegalStateException();
            });
    assertTrue(
        buf.toString().contains("check error: java.lang.IllegalStateException"), buf.toString());
  }

  @Test
  void negatedClaimWithAllKeywordsFailsCoverageEndToEnd() throws Exception {
    trapFile("[]\n"); // no trap pairs, so the judge calls counted below are Coverage's alone
    cases(
        "- id: c1\n"
            + "  question: q\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor =
        "{\"refused\":false,\"claims\":[{\"claim\":\"Everything in A except the"
            + " body\",\"citations\":[\"d#a\"]}]}";
    var judgeCalls = new int[1];
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[0],
            root,
            new PrintStream(buf),
            model ->
                (system, user) -> {
                  judgeCalls[0]++;
                  return "NO";
                });
    assertEquals(1, code, buf.toString());
    assertTrue(buf.toString().contains("Coverage: not covered"), buf.toString());
    assertEquals(
        3,
        judgeCalls[0]); // Coverage's confirm call, Groundedness on the uncovered claim, Relevance
  }

  @Test
  void missedTrapPairIsReportedButAllCasesStillRunAndExitCodeIsOne() throws Exception {
    cases(OOS);
    trapFile("- {name: negated, fact: F, claim: not F, agree: false}\n");
    int[] code = new int[1];
    String output = out(code)[0]; // the fake judge says YES to everything
    assertEquals(1, code[0], output);
    assertTrue(output.contains("MISS negated (expected NO)"), output);
    assertTrue(output.contains("judge calibration: 1 trap pair miss(es)"), output);
    assertTrue(output.contains("PASS") && output.contains("oos-1"), output);
    var reportMatcher = java.util.regex.Pattern.compile("Report: (\\S+)").matcher(output);
    assertTrue(reportMatcher.find(), output);
    var json =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(root.resolve(reportMatcher.group(1)).toFile());
    assertTrue(json.get("calibration").get("ran").asBoolean());
    assertEquals(1, json.get("calibration").get("misses").asInt());
    assertEquals("negated", json.get("calibration").get("pairs").get(0).get("name").asText());
    assertFalse(json.get("calibration").get("pairs").get(0).get("passed").asBoolean());
  }

  @Test
  void skipCalibrationNeedsNoTrapFileAndMakesNoTrapJudgeCalls() throws Exception {
    cases(OOS);
    Files.delete(root.resolve("calibration/trap-pairs.yaml"));
    var judgeCalls = new int[1];
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[] {"--skip-calibration"},
            root,
            new PrintStream(buf),
            model ->
                (system, user) -> {
                  judgeCalls[0]++;
                  return "YES";
                });
    assertEquals(0, code, buf.toString());
    assertTrue(buf.toString().contains("Judge calibration: skipped"), buf.toString());
    assertEquals(0, judgeCalls[0]);
  }

  @Test
  void missingTrapFileWithoutTheFlagExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    Files.delete(root.resolve("calibration/trap-pairs.yaml"));
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR") && output.contains("trap-pairs.yaml"), output);
    assertTrue(output.contains("--skip-calibration"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void anUnsupportedPairJudgedSupportedFailsTheRunAndIsInTheReport() throws Exception {
    cases(OOS);
    labeledSampleFile("- {name: u1, kind: unsupported, supported: false, passage: P, claim: C}\n");
    int[] code = new int[1];
    String output = out(code)[0]; // the fake judge says YES to everything
    assertEquals(1, code[0], output);
    assertTrue(output.contains("MISS u1 (labeled UNSUPPORTED, judge said SUPPORTED)"), output);
    assertTrue(
        output.contains("groundedness calibration: 1 of 1 unsupported pair(s) judged supported"),
        output);
    assertTrue(output.contains("PASS") && output.contains("oos-1"), output);
    var reportMatcher = java.util.regex.Pattern.compile("Report: (\\S+)").matcher(output);
    assertTrue(reportMatcher.find(), output);
    var json =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(root.resolve(reportMatcher.group(1)).toFile());
    assertEquals(1, json.get("calibration").get("groundedness").get("falseSupported").asInt());
    assertTrue(json.get("calibration").get("groundedness").has("agreed"));
    assertEquals(
        "u1", json.get("calibration").get("groundedness").get("pairs").get(0).get("name").asText());
  }

  @Test
  void skipCalibrationMakesNoSampleJudgeCalls() throws Exception {
    cases(OOS);
    labeledSampleFile("- {name: u1, kind: unsupported, supported: false, passage: P, claim: C}\n");
    var judgeCalls = new int[1];
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[] {"--skip-calibration"},
            root,
            new PrintStream(buf),
            model ->
                (system, user) -> {
                  judgeCalls[0]++;
                  return "YES";
                });
    assertEquals(0, code, buf.toString());
    assertEquals(0, judgeCalls[0]);
  }

  @Test
  void missingSampleFileWithoutTheFlagExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    Files.delete(root.resolve("calibration/labeled-sample.yaml"));
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(
        output.contains("labeled-sample.yaml") && output.contains("--skip-calibration"), output);
    assertEquals(0, requests.get());
  }

  private static final String CONFIDENT_ANSWER =
      "{\"refused\":false,\"claims\":[{\"claim\":\"It costs 5 euro\",\"citations\":[\"d#a\"]}]}";

  private static final String ANSWER_CASE =
      "- id: c1\n"
          + "  question: q\n"
          + "  category: single-source\n"
          + "  expected_behavior: answer\n"
          + "  facts:\n"
          + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n";
  private static final String GOOD_ANSWER =
      "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
  private static final String REFUSAL = "{\"refused\":true,\"claims\":[]}";

  private void baseline(String json) throws IOException {
    Files.createDirectories(root.resolve("caseResults"));
    Files.writeString(root.resolve("caseResults/baseline.json"), json);
  }

  private com.fasterxml.jackson.databind.JsonNode reportJson(String output) throws IOException {
    var reportMatcher = java.util.regex.Pattern.compile("Report: (\\S+)").matcher(output);
    assertTrue(reportMatcher.find(), output);
    return new com.fasterxml.jackson.databind.ObjectMapper()
        .readTree(root.resolve(reportMatcher.group(1)).toFile());
  }

  @Test
  void aRegressionIsRerunOnceFailsTheRunAndIsNamedInTheOutputAndTheReport() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":true}]}");
    replyFor = REFUSAL;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(1, code[0], output);
    assertEquals(3, requests.get(), "reachability ping, one attempt, one re-run");
    assertTrue(output.contains("REGRESSION c1"), output);
    assertTrue(
        output.contains("RESULT: FAIL - regression vs baseline (baseline.json): c1"), output);
    var comparison = reportJson(output).get("baseline");
    assertEquals("baseline.json", comparison.get("file").asText());
    assertEquals("c1", comparison.get("regressions").get(0).asText());
    assertFalse(comparison.get("reruns").get(0).get("passedOnRerun").asBoolean());
  }

  @Test
  void aFlakeThatPassesOnTheRerunKeepsTheRunGreenAndIsListed() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":true}]}");
    replyFor = REFUSAL;
    replyFromRerun = GOOD_ANSWER;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(0, code[0], output);
    assertEquals(3, requests.get());
    assertTrue(output.contains("flaky      c1"), output);
    assertTrue(output.contains("PASS") && output.contains("c1"), output);
    assertEquals(0, reportJson(output).get("baseline").get("regressions").size());
  }

  @Test
  void aCaseThatFailedInTheBaselineIsNotRerunAndIsNoRegression() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":false}]}");
    replyFor = REFUSAL;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(1, code[0], output); // below the floor
    assertEquals(2, requests.get()); // ping plus one attempt
    assertFalse(output.contains("regression vs baseline"), output);
    assertTrue(output.contains("no case that passed there failed now"), output);
  }

  @Test
  void withoutTheFlagThereIsNoBaselineSectionAndNoRerun() throws Exception {
    cases(OOS);
    replyFor = CONFIDENT_ANSWER;
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, requests.get()); // ping plus one attempt
    assertFalse(output.contains("Baseline:"), output);
    assertTrue(reportJson(output).get("baseline").isNull());
  }

  @Test
  void baselineWithoutAValueExitsTwoWithUsage() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--baseline")[0];
    assertEquals(2, code[0], output);
    assertTrue(
        output.contains("ERROR: --baseline needs a value") && output.contains("Usage"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void anOutOfScopeFlakeIsNotRerunAndStillFailsTheRun() throws Exception {
    cases(OOS);
    baseline("{\"cases\":[{\"id\":\"oos-1\",\"passed\":true}]}");
    replyFor = CONFIDENT_ANSWER;
    replyFromRerun = REFUSAL;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(1, code[0], output);
    assertEquals(2, requests.get(), "ping plus one attempt, no re-run");
    assertTrue(output.contains("out-of-scope case failed: oos-1"), output);
  }

  @Test
  void aBaselineWithNoneOfThisRunsCasesExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    baseline("{\"cases\":[{\"id\":\"some-other-suite-case\",\"passed\":true}]}");
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("baseline.json") && output.contains("none of"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void aBaselineWithADuplicateIdExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    baseline(
        "{\"cases\":[{\"id\":\"oos-1\",\"passed\":true},{\"id\":\"oos-1\",\"passed\":false}]}");
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR") && output.contains("duplicate"), output);
    assertEquals(0, requests.get());
  }

  private String defaultConfig() {
    return "endpoint: " + url() + "\npassFloor: 0.90\ncategories: [single-source, out-of-scope]\n";
  }

  private int judgeCallsOf(String... args) throws Exception {
    var judgeCalls = new int[1];
    Harness.run(
        args,
        root,
        new PrintStream(new ByteArrayOutputStream()),
        model ->
            (system, user) -> {
              judgeCalls[0]++;
              return "YES";
            });
    return judgeCalls[0];
  }

  @Test
  void skipCalibrationInTheConfigSkipsTheJudgeCallsAndTheFlagCanTurnItBackOn() throws Exception {
    cases(OOS);
    labeledSampleFile("- {name: u1, kind: unsupported, supported: false, passage: P, claim: C}\n");
    config(defaultConfig() + "skipCalibration: true\n");
    assertEquals(0, judgeCallsOf());
    assertTrue(judgeCallsOf("--skipCalibration", "false") > 0);
  }

  @Test
  void skipCalibrationFlagOverridesAConfigThatRunsIt() throws Exception {
    cases(OOS);
    labeledSampleFile("- {name: u1, kind: unsupported, supported: false, passage: P, claim: C}\n");
    config(defaultConfig() + "skipCalibration: false\n");
    assertTrue(judgeCallsOf() > 0);
    assertEquals(0, judgeCallsOf("--skipCalibration", "true"));
    assertEquals(0, judgeCallsOf("--skip-calibration"));
  }

  @Test
  void baselineFromTheConfigIsUsedWithoutTheFlag() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":true}]}");
    config(defaultConfig() + "baseline: caseResults/baseline.json\n");
    replyFor = REFUSAL;
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(1, code[0], output);
    assertTrue(output.contains("REGRESSION c1"), output);
  }

  @Test
  void baselineFlagOverridesTheConfigBaseline() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":false}]}");
    Files.writeString(
        root.resolve("caseResults/from-config.json"),
        "{\"cases\":[{\"id\":\"c1\",\"passed\":true}]}");
    config(defaultConfig() + "baseline: caseResults/from-config.json\n");
    replyFor = REFUSAL;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertTrue(output.contains("Baseline: baseline.json"), output);
    assertTrue(output.contains("no case that passed there failed now"), output);
    assertEquals(2, requests.get(), "ping plus one attempt, no re-run");
  }

  @Test
  void aBrokenBaselineFileExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    baseline("{not json");
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("ERROR") && output.contains("baseline.json"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void aBaselineFileThatDoesNotExistExitsTwoNamingThePathBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/missing.json")[0];
    assertEquals(2, code[0], output);
    assertTrue(
        output.contains("baseline file not found") && output.contains("missing.json"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void aMissingBaselineNamedInTheConfigExitsTwoNamingThePathBeforeAnyAssistantCall()
      throws Exception {
    cases(OOS);
    config(defaultConfig() + "baseline: caseResults/nope.json\n");
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("baseline file not found") && output.contains("nope.json"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void noBaselineSetIsLoggedOnceAndTheRunGoesAheadWithoutIt() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code)[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("No baseline set, running without one"), output);
  }

  @Test
  void anEmptyBaselineValueTurnsTheConfigBaselineOff() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":true}]}");
    config(defaultConfig() + "baseline: caseResults/baseline.json\n");
    replyFor = REFUSAL;
    int[] code = new int[1];
    String output = out(code, "--baseline", "")[0];
    assertFalse(output.contains("Baseline:"), output);
    assertTrue(output.contains("No baseline set"), output);
    assertEquals(2, requests.get());
  }

  @Test
  void whenASettingIsGivenTwiceTheLastValueWins() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":true}]}");
    replyFor = REFUSAL;
    int[] code = new int[1];
    assertTrue(
        out(code, "--baseline", "", "--baseline", "caseResults/baseline.json")[0].contains(
            "Baseline:"));
    assertFalse(
        out(code, "--baseline", "caseResults/baseline.json", "--baseline", "")[0].contains(
            "Baseline:"));
  }

  @Test
  void anySettingCanBeOverriddenIncludingNestedOnes() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--calibration.trapPairs", "calibration/gone.yaml")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("gone.yaml") && output.contains("--skip-calibration"), output);
    assertEquals(0, requests.get());
    out(code, "--passFloor", "1.0");
    assertEquals(0, code[0], "an all-passing run meets a floor of 1.0");
  }

  @Test
  void anUnknownSettingExitsTwoNamingItBeforeTouchingTheNetwork() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--skipCalibraton", "true")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("unknown setting") && output.contains("skipCalibraton"), output);
    assertTrue(output.contains("Usage"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void aCaseThatFailedInTheBaselineAndPassesNowIsReportedAsImprovedAndDoesNotChangeTheExit()
      throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":false}]}");
    replyFor = GOOD_ANSWER;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("improved since baseline: c1"), output);
    assertEquals(2, requests.get(), "ping plus one attempt, no re-run");
    assertEquals("c1", reportJson(output).get("baseline").get("improved").get(0).asText());
  }

  @Test
  void nothingImprovedMeansNoImprovedLine() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":true}]}");
    replyFor = GOOD_ANSWER;
    int[] code = new int[1];
    String output = out(code, "--baseline", "caseResults/baseline.json")[0];
    assertFalse(output.contains("improved since baseline"), output);
  }

  @Test
  void aRelevanceNoIsReportedButTheCaseAndTheRunStillPass() throws Exception {
    cases(
        "- id: c1\n"
            + "  question: q\n"
            + "  category: single-source\n"
            + "  expected_behavior: answer\n"
            + "  facts:\n"
            + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n");
    replyFor = "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[0],
            root,
            new PrintStream(buf),
            model -> (system, user) -> system.contains("pertinent") ? "NO" : "YES");
    String output = buf.toString();
    assertEquals(0, code, output);
    assertTrue(output.contains("PASS") && output.contains("c1"), output);
    assertTrue(output.contains("Relevance (advisory): off-topic claim(s)"), output);
    assertTrue(output.contains("flagged on 1 of 1 case(s)"), output);
    assertTrue(output.contains("RESULT: OK"), output);
  }

  /** A judge that says YES and reports 100 prompt and 5 completion tokens for every call. */
  private static llm.Llm reportingYes() {
    return new llm.Llm() {
      @Override
      public String complete(String system, String user) {
        return "YES";
      }

      @Override
      public llm.Completion completeWithUsage(String system, String user) {
        return new llm.Completion("YES", 100, 5);
      }
    };
  }

  private static final String ONE_ANSWER_CASE =
      "- id: c1\n"
          + "  question: q\n"
          + "  category: single-source\n"
          + "  expected_behavior: answer\n"
          + "  facts:\n"
          + "    - {fact: A is body, chunks: [d#a], keywords: [body]}\n";
  private static final String ONE_CLAIM_REPLY =
      "{\"refused\":false,\"claims\":[{\"claim\":\"A is body\",\"citations\":[\"d#a\"]}]}";

  private String runWithReportingJudge(String... args) throws Exception {
    var buf = new ByteArrayOutputStream();
    Harness.run(args, root, new PrintStream(buf), model -> reportingYes());
    return buf.toString();
  }

  private static java.util.List<String> checkNames(com.fasterxml.jackson.databind.JsonNode usage) {
    var names = new java.util.ArrayList<String>();
    usage.get("byCheck").forEach(entry -> names.add(entry.get("check").asText()));
    return names;
  }

  @Test
  void theReportMeasuresCallsTokensAndCostPerCheck() throws Exception {
    config(defaultConfig() + "judgePricing:\n  inputPerMillion: 5.0\n  outputPerMillion: 25.0\n");
    cases(ONE_ANSWER_CASE);
    replyFor = ONE_CLAIM_REPLY;
    String output = runWithReportingJudge();
    // 1 trap pair (calibration) + Coverage confirm + Groundedness + Relevance
    assertTrue(output.contains("Cost and time"), output);
    assertTrue(
        output.contains("judge: 4 calls, 400 prompt + 20 completion tokens, est. $0.0025"), output);
    var usage = reportJson(output).get("usage");
    assertEquals(4, usage.get("judgeCalls").asInt());
    assertEquals(1, usage.get("assistantCalls").asInt());
    assertEquals(
        (400 * 5.0 + 20 * 25.0) / 1_000_000.0, usage.get("judgeCostUsd").asDouble(), 1e-12);
    assertEquals(java.util.List.of("calibration", "Coverage", "Groundedness", "Relevance"), checkNames(usage));
  }

  @Test
  void withoutJudgePricingTheReportHasTokensAndNoCost() throws Exception {
    config(defaultConfig());
    cases(ONE_ANSWER_CASE);
    replyFor = ONE_CLAIM_REPLY;
    String output = runWithReportingJudge();
    assertTrue(output.contains("cost not estimated"), output);
    assertFalse(output.contains("$0.0000"), output);
    assertTrue(reportJson(output).get("usage").get("judgeCostUsd").isNull());
  }

  @Test
  void skippingCalibrationLeavesNoCalibrationRow() throws Exception {
    cases(ONE_ANSWER_CASE);
    replyFor = ONE_CLAIM_REPLY;
    String output = runWithReportingJudge("--skipCalibration", "true");
    assertEquals(
        java.util.List.of("Coverage", "Groundedness", "Relevance"), checkNames(reportJson(output).get("usage")));
  }

  @Test
  void theReportRecordsProvenance() throws Exception {
    config(defaultConfig() + "assistantVersion: stub-v1\n");
    cases(ONE_ANSWER_CASE);
    replyFor = ONE_CLAIM_REPLY;
    var provenance = reportJson(runWithReportingJudge()).get("provenance");
    assertEquals(Judge.promptHash(), provenance.get("judgePromptHash").asText());
    assertEquals(12, provenance.get("judgePromptHash").asText().length());
    assertEquals("stub-v1", provenance.get("assistantVersion").asText());
    assertFalse(provenance.get("judgeModel").asText().isBlank());
    assertTrue(provenance.get("gitSha").isNull(), "the temp root is not a git checkout");
  }

  @Test
  void aBadJudgePricingExitsTwoBeforeAnyAssistantCall() throws Exception {
    config(defaultConfig() + "judgePricing:\n  inputPerMillion: 5.0\n");
    cases(ONE_ANSWER_CASE);
    var buf = new ByteArrayOutputStream();
    int code = Harness.run(new String[0], root, new PrintStream(buf), model -> reportingYes());
    assertEquals(2, code, buf.toString());
    assertTrue(buf.toString().contains("judgePricing"), buf.toString());
    assertEquals(0, requests.get());
  }

  @Test
  void aBaselineRerunIsCountedAsRealSpend() throws Exception {
    cases(ANSWER_CASE);
    baseline("{\"cases\":[{\"id\":\"c1\",\"passed\":true}]}");
    replyFor = REFUSAL;
    replyFromRerun = GOOD_ANSWER;
    String output = runWithReportingJudge("--baseline", "caseResults/baseline.json");
    assertEquals(2, reportJson(output).get("usage").get("assistantCalls").asInt(), output);
  }

  @Test
  void aSubsetOfChecksRunsOnlyThoseAndSaysWhatIsOff() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--checks", "Refusal,Coverage")[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("Checks off: Citation integrity, Groundedness, Source, Relevance"), output);
    try (var files = Files.list(root.resolve("caseResults"))) {
      String report = Files.readString(files.filter(p -> p.toString().endsWith(".json")).findFirst().get());
      assertTrue(report.contains("\"Coverage\"") && !report.contains("\"Groundedness\""), report);
    }
  }

  @Test
  void aBadChecksListExitsTwoBeforeAnyAssistantCall() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--checks", "Refusal,Nope")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("unknown check(s): Nope"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void aCheckThatNeedsAnotherPullsItInAndSaysSo() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--checks", "Refusal,Source")[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("Checks added because another check needs them: Coverage"), output);
  }

  @Test
  void appTypeWithAddChecksRunsTheFloorPlusTheAddition() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--appType", "uncited", "--addChecks", "Relevance")[0];
    assertEquals(0, code[0], output);
    assertTrue(output.contains("Checks off: Citation integrity, Groundedness, Source"), output);
    assertFalse(output.contains("Relevance,"), output);
  }

  @Test
  void appTypeAndChecksTogetherExitTwo() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--appType", "cited", "--checks", "Refusal")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("not both"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void dropRefusalWithOutOfScopeCasesExitsTwo() throws Exception {
    cases(OOS);
    int[] code = new int[1];
    String output = out(code, "--checks", "Coverage")[0];
    assertEquals(2, code[0], output);
    assertTrue(output.contains("'Refusal' is required"), output);
    assertEquals(0, requests.get());
  }

  @Test
  void withoutGroundednessTheSampleIsNotJudged() throws Exception {
    cases(OOS);
    labeledSampleFile("- {name: u1, kind: unsupported, supported: false, passage: P, claim: C}\n");
    var buf = new ByteArrayOutputStream();
    int code =
        Harness.run(
            new String[] {"--checks", "Refusal,Coverage"},
            root,
            new PrintStream(buf),
            model -> (system, user) -> "YES");
    assertEquals(0, code, buf.toString());
  }
}

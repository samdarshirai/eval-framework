package eval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

public final class AssistantClient implements Assistant {
  private static final ObjectMapper mapper =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final URI endpoint;
  private final DebugLog debug;

  public AssistantClient(String endpoint) {
    this(endpoint, DebugLog.OFF);
  }

  AssistantClient(String endpoint, DebugLog debug) {
    this.endpoint = URI.create(endpoint);
    this.debug = debug;
  }

  /** Throws, with how to start the stub, when nothing answers at the endpoint. */
  public void requireReachable() {
    if (!reachable()) {
      throw new IllegalStateException(
          "cannot reach the assistant at "
              + endpoint
              + "\nStart the stub in another terminal first:"
              + "\n  export LLM_API_KEY=..."
              + "\n  mvn -q -DskipTests package"
              + "\n  java -jar assistant/target/assistant.jar");
    }
  }

  /** Any HTTP response counts as reachable; only a failed connection does not. */
  public boolean reachable() {
    try {
      var res =
          http.send(
              HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).GET().build(),
              HttpResponse.BodyHandlers.discarding());
      debug.log("assistant reachable at " + endpoint + " (GET -> HTTP " + res.statusCode() + ")");
      return true;
    } catch (IOException e) {
      debug.log("assistant not reachable at " + endpoint + ": " + DebugLog.clip(e.toString()));
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override
  public Answer ask(String question, String runId) throws IOException, InterruptedException {
    var req =
        HttpRequest.newBuilder(endpoint)
            .timeout(Duration.ofSeconds(60))
            .header("content-type", "application/json")
            .header("X-Eval-Run", runId)
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    mapper.writeValueAsString(Map.of("question", question))))
            .build();
    long startedNanos = System.nanoTime();
    var res = http.send(req, HttpResponse.BodyHandlers.ofString());
    debug.log(
        "assistant POST "
            + endpoint
            + " question: "
            + DebugLog.clip(question)
            + " -> HTTP "
            + res.statusCode()
            + " in "
            + (System.nanoTime() - startedNanos) / 1_000_000
            + " ms, body: "
            + DebugLog.clip(res.body()));
    if (res.statusCode() != 200)
      throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
    try {
      JsonNode n = mapper.readTree(res.body());
      if (n == null
          || !n.isObject()
          || !n.path("refused").isBoolean()
          || !(n.path("claims").isMissingNode()
              || n.path("claims").isNull()
              || n.path("claims").isArray()))
        throw new IOException("response is not valid claims JSON: " + res.body());
      return mapper.treeToValue(n, Answer.class);
    } catch (JsonProcessingException e) {
      throw new IOException("response is not valid claims JSON: " + res.body(), e);
    }
  }
}

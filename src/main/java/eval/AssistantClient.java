package eval;

import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

public final class AssistantClient {
    private static final ObjectMapper M = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final URI endpoint;

    public AssistantClient(String endpoint) { this.endpoint = URI.create(endpoint); }

    /** Any HTTP response counts as reachable; only a failed connection does not. */
    public boolean reachable() {
        try {
            http.send(HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding());
            return true;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public Answer ask(String question, String runId) throws IOException, InterruptedException {
        var req = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(60))
            .header("content-type", "application/json")
            .header("X-Eval-Run", runId)
            .POST(HttpRequest.BodyPublishers.ofString(M.writeValueAsString(Map.of("question", question)))).build();
        var res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode() + ": " + res.body());
        try {
            return M.readValue(res.body(), Answer.class);
        } catch (IOException e) {
            throw new IOException("response is not valid claims JSON: " + res.body(), e);
        }
    }
}

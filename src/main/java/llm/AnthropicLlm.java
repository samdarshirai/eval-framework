package llm;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class AnthropicLlm implements Llm {
    private static final ObjectMapper M = new ObjectMapper();
    private static final URI URL = URI.create("https://api.anthropic.com/v1/messages");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String model, apiKey;

    public AnthropicLlm(String model, String apiKey) { this.model = model; this.apiKey = apiKey; }

    public static AnthropicLlm fromEnv(String model) {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank())
            throw new IllegalStateException("ANTHROPIC_API_KEY is not set. Run: export ANTHROPIC_API_KEY=...");
        return new AnthropicLlm(model, key);
    }

    @Override public String complete(String system, String user) {
        try {
            var req = HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(model, system, user))).build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200)
                throw new IllegalStateException("Anthropic API " + res.statusCode() + ": " + res.body());
            return parseText(res.body());
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException("Anthropic API call failed: " + e.getMessage(), e);
        }
    }

    static String requestBody(String model, String system, String user) {
        ObjectNode n = M.createObjectNode();
        n.put("model", model);
        n.put("max_tokens", 1024);
        n.put("temperature", 0);
        n.put("system", system);
        n.putArray("messages").addObject().put("role", "user").put("content", user);
        return n.toString();
    }

    static String parseText(String json) {
        try {
            return M.readTree(json).get("content").get(0).get("text").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected Anthropic response: " + json, e);
        }
    }
}

package llm;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class OpenRouterLlm implements Llm {
    private static final ObjectMapper M = new ObjectMapper();
    private static final URI URL = URI.create("https://openrouter.ai/api/v1/chat/completions");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String model, apiKey;

    public OpenRouterLlm(String model, String apiKey) { this.model = model; this.apiKey = apiKey; }

    public static OpenRouterLlm fromEnv(String model) {
        String key = System.getenv("OPENROUTER_API_KEY");
        if (key == null || key.isBlank())
            throw new IllegalStateException("OPENROUTER_API_KEY is not set. Run: export OPENROUTER_API_KEY=...");
        return new OpenRouterLlm(model, key);
    }

    @Override public String complete(String system, String user) {
        try {
            var req = HttpRequest.newBuilder(URL).timeout(Duration.ofSeconds(60))
                .header("content-type", "application/json")
                .header("authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody(model, system, user))).build();
            var res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200)
                throw new IllegalStateException("OpenRouter API " + res.statusCode() + ": " + res.body());
            return parseText(res.body());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("OpenRouter API call failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("OpenRouter API call interrupted", e);
        }
    }

    static String requestBody(String model, String system, String user) {
        ObjectNode n = M.createObjectNode();
        n.put("model", model);
        n.put("max_tokens", 1024);
        n.put("temperature", 0);
        ArrayNode messages = n.putArray("messages");
        messages.addObject().put("role", "system").put("content", system);
        messages.addObject().put("role", "user").put("content", user);
        return n.toString();
    }

    static String parseText(String json) {
        try {
            JsonNode root = M.readTree(json);
            // OpenRouter can answer HTTP 200 with an error object instead of choices.
            if (root.has("error"))
                throw new IllegalStateException("OpenRouter API error: " + root.get("error"));
            return root.get("choices").get(0).get("message").get("content").asText();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Unexpected OpenRouter response: " + json, e);
        }
    }
}

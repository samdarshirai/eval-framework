package llm;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

public final class OpenRouterLlm implements Llm {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String DEFAULT_BASE_URL = "https://openrouter.ai/api/v1";
  private static final int MAX_ATTEMPTS = 4;
  private static final long DEFAULT_RETRY_DELAY_MILLIS = 15000;
  private static final long MIN_RETRY_DELAY_MILLIS = 1000;
  private static final long MAX_RETRY_DELAY_MILLIS = 65000;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final URI url;
  private final String model, apiKey, effort;

  public OpenRouterLlm(String model, String apiKey) {
    this(model, apiKey, null);
  }

  public OpenRouterLlm(String model, String apiKey, String effort) {
    this(model, apiKey, effort, null);
  }

  /** {@code baseUrl} is the OpenAI-compatible API root; null or blank means OpenRouter. */
  public OpenRouterLlm(String model, String apiKey, String effort, String baseUrl) {
    this.model = model;
    this.apiKey = apiKey;
    this.effort = effort;
    this.url = chatCompletionsUrl(baseUrl);
  }

  public static OpenRouterLlm fromEnv(String model) {
    return fromEnv(model, null);
  }

  /**
   * {@code effort} is OpenRouter's reasoning effort ("low", "medium", "high"); null leaves it
   * unset.
   */
  public static OpenRouterLlm fromEnv(String model, String effort) {
    return fromEnv(model, effort, null);
  }

  /** The API key comes from env LLM_API_KEY; {@code baseUrl} from config (null: OpenRouter). */
  public static OpenRouterLlm fromEnv(String model, String effort, String baseUrl) {
    String key = System.getenv("LLM_API_KEY");
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("LLM_API_KEY is not set. Run: export LLM_API_KEY=...");
    }
    return new OpenRouterLlm(model, key, effort, baseUrl);
  }

  /** The chat-completions URL under {@code baseUrl}; OpenRouter when null or blank. */
  static URI chatCompletionsUrl(String baseUrl) {
    String base = baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl.strip();
    return URI.create(base.replaceAll("/+$", "") + "/chat/completions");
  }

  @Override
  public String complete(String system, String user) {
    return completeWithUsage(system, user).text();
  }

  @Override
  public Completion completeWithUsage(String system, String user) {
    try {
      var request =
          HttpRequest.newBuilder(url)
              .timeout(Duration.ofSeconds(60))
              .header("content-type", "application/json")
              .header("authorization", "Bearer " + apiKey)
              .POST(HttpRequest.BodyPublishers.ofString(requestBody(model, system, user, effort)))
              .build();
      var response = http.send(request, HttpResponse.BodyHandlers.ofString());
      // A 429 is the account's rate limit, not a judge answer: wait it out and try again.
      for (int attempt = 1; attempt < MAX_ATTEMPTS && response.statusCode() == 429; attempt++) {
        long delayMillis =
            retryDelayMillis(
                response.headers().firstValue("retry-after").orElse(null),
                response.headers().firstValue("x-ratelimit-reset").orElse(null),
                System.currentTimeMillis());
        Thread.sleep(delayMillis);
        response = http.send(request, HttpResponse.BodyHandlers.ofString());
      }
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "OpenRouter API " + response.statusCode() + ": " + response.body());
      }
      return parseCompletion(response.body());
    } catch (java.io.IOException e) {
      throw new IllegalStateException("OpenRouter API call failed: " + e.getMessage(), e);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("OpenRouter API call interrupted", e);
    }
  }

  /** Seconds from retry-after, else epoch-ms X-RateLimit-Reset minus now, else 15s; 1s to 65s. */
  static long retryDelayMillis(String retryAfterSeconds, String resetEpochMillis, long nowMillis) {
    long delayMillis = DEFAULT_RETRY_DELAY_MILLIS;
    Long seconds = parseLong(retryAfterSeconds);
    Long resetEpoch = parseLong(resetEpochMillis);
    if (seconds != null) {
      delayMillis = seconds * 1000;
    } else if (resetEpoch != null) {
      delayMillis = resetEpoch - nowMillis;
    }
    return Math.max(MIN_RETRY_DELAY_MILLIS, Math.min(MAX_RETRY_DELAY_MILLIS, delayMillis));
  }

  private static Long parseLong(String text) {
    if (text == null) {
      return null;
    }
    try {
      return Long.parseLong(text.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }

  static String requestBody(String model, String system, String user) {
    return requestBody(model, system, user, null);
  }

  static String requestBody(String model, String system, String user, String effort) {
    ObjectNode body = MAPPER.createObjectNode();
    body.put("model", model);
    // Reasoning tokens count toward max_tokens; leave room for the answer after them.
    body.put("max_tokens", effort == null ? 1024 : 4096);
    body.put("temperature", 0);
    // Default routing silently ignores parameters a provider does not support; this makes the call
    // fail
    // instead of quietly running at the provider's default temperature (D14).
    body.putObject("provider").put("require_parameters", true);
    if (effort != null) {
      body.putObject("reasoning").put("effort", effort);
    }
    ArrayNode messages = body.putArray("messages");
    messages.addObject().put("role", "system").put("content", system);
    messages.addObject().put("role", "user").put("content", user);
    return body.toString();
  }

  static String parseText(String json) {
    return parseCompletion(json).text();
  }

  static Completion parseCompletion(String json) {
    try {
      JsonNode root = MAPPER.readTree(json);
      // OpenRouter can answer HTTP 200 with an error object instead of choices.
      if (root.has("error")) {
        throw new IllegalStateException("OpenRouter API error: " + root.get("error"));
      }
      String text = root.get("choices").get(0).get("message").get("content").asText();
      JsonNode usage = root.path("usage");
      return new Completion(
          text, usage.path("prompt_tokens").asLong(0), usage.path("completion_tokens").asLong(0));
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Unexpected OpenRouter response: " + json, e);
    }
  }
}

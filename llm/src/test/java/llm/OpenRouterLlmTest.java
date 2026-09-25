package llm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;

class OpenRouterLlmTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void requestBodyPinsTemperatureZeroAndCarriesModelAndPrompts() throws Exception {
    JsonNode requestJson = MAPPER.readTree(OpenRouterLlm.requestBody("m-1", "sys", "hi"));
    assertEquals("m-1", requestJson.get("model").asText());
    assertEquals(0, requestJson.get("temperature").asInt());
    assertTrue(
        requestJson.get("provider").get("require_parameters").asBoolean(),
        "an unsupported temperature must fail, not be ignored");
    assertEquals("system", requestJson.get("messages").get(0).get("role").asText());
    assertEquals("sys", requestJson.get("messages").get(0).get("content").asText());
    assertEquals("user", requestJson.get("messages").get(1).get("role").asText());
    assertEquals("hi", requestJson.get("messages").get(1).get("content").asText());
  }

  @Test
  void parseTextReturnsFirstChoiceContent() {
    assertEquals(
        "hello", OpenRouterLlm.parseText("{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}"));
  }

  @Test
  void parseTextSurfacesErrorBodyOn200() {
    var error =
        assertThrows(
            IllegalStateException.class,
            () -> OpenRouterLlm.parseText("{\"error\":{\"message\":\"boom\"}}"));
    assertTrue(error.getMessage().contains("boom"), error.getMessage());
  }

  @Test
  void fromEnvWithoutKeyExplainsWhatToDo() {
    // only meaningful when the key is unset; skip otherwise
    org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("OPENROUTER_API_KEY") == null);
    var error = assertThrows(IllegalStateException.class, () -> OpenRouterLlm.fromEnv("m"));
    assertTrue(error.getMessage().contains("OPENROUTER_API_KEY"));
  }

  @Test
  void effortIsSentAsReasoningEffortWhenSet() throws Exception {
    var requestJson =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(OpenRouterLlm.requestBody("m", "s", "u", "low"));
    assertEquals("low", requestJson.get("reasoning").get("effort").asText());
    assertEquals(0, requestJson.get("temperature").asInt());
    assertTrue(requestJson.get("provider").get("require_parameters").asBoolean());
  }

  @Test
  void effortRaisesMaxTokensSoReasoningCannotEatTheAnswer() throws Exception {
    var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
    assertEquals(
        1024, mapper.readTree(OpenRouterLlm.requestBody("m", "s", "u")).get("max_tokens").asInt());
    assertEquals(
        4096,
        mapper.readTree(OpenRouterLlm.requestBody("m", "s", "u", "low")).get("max_tokens").asInt());
  }

  @Test
  void retryAfterHeaderWinsOverResetEpoch() {
    assertEquals(3000, OpenRouterLlm.retryDelayMillis("3", "9999999", 1000));
  }

  @Test
  void resetEpochIsUsedWhenNoRetryAfter() {
    assertEquals(20000, OpenRouterLlm.retryDelayMillis(null, "120000", 100000));
  }

  @Test
  void missingBothHeadersWaitsFifteenSeconds() {
    assertEquals(15000, OpenRouterLlm.retryDelayMillis(null, null, 100000));
  }

  @Test
  void delayIsClampedToOneSecondMinimum() {
    assertEquals(1000, OpenRouterLlm.retryDelayMillis("0", null, 100000));
    assertEquals(1000, OpenRouterLlm.retryDelayMillis(null, "50000", 100000));
  }

  @Test
  void delayIsClampedToSixtyFiveSecondMaximum() {
    assertEquals(65000, OpenRouterLlm.retryDelayMillis("9999", null, 100000));
  }

  @Test
  void nonNumericValuesAreTreatedAsAbsent() {
    assertEquals(20000, OpenRouterLlm.retryDelayMillis("soon", "120000", 100000));
    assertEquals(15000, OpenRouterLlm.retryDelayMillis("soon", "later", 100000));
  }

  @Test
  void noReasoningFieldWhenEffortIsNull() throws Exception {
    var requestJson =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(OpenRouterLlm.requestBody("m", "s", "u"));
    assertFalse(requestJson.has("reasoning"));
  }

  @Test
  void parseCompletionReadsTheTokenCounts() {
    Completion completion =
        OpenRouterLlm.parseCompletion(
            "{\"choices\":[{\"message\":{\"content\":\"hi\"}}],"
                + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3}}");
    assertEquals("hi", completion.text());
    assertEquals(12, completion.promptTokens());
    assertEquals(3, completion.completionTokens());
  }

  @Test
  void parseCompletionWithNoUsageBlockCountsZeroTokensAndDoesNotFail() {
    Completion completion =
        OpenRouterLlm.parseCompletion("{\"choices\":[{\"message\":{\"content\":\"hi\"}}]}");
    assertEquals("hi", completion.text());
    assertEquals(0, completion.promptTokens());
    assertEquals(0, completion.completionTokens());
  }

  @Test
  void parseCompletionStillSurfacesAnErrorBodyOn200() {
    assertThrows(
        IllegalStateException.class,
        () -> OpenRouterLlm.parseCompletion("{\"error\":{\"message\":\"boom\"}}"));
  }

  @Test
  void aPlainLlmReportsItsTextWithZeroTokens() {
    Llm llm = (system, user) -> "x";
    assertEquals(new Completion("x", 0, 0), llm.completeWithUsage("s", "u"));
  }
}

package llm;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;

class OpenRouterLlmTest {
  private static final ObjectMapper M = new ObjectMapper();

  @Test
  void requestBodyPinsTemperatureZeroAndCarriesModelAndPrompts() throws Exception {
    JsonNode n = M.readTree(OpenRouterLlm.requestBody("m-1", "sys", "hi"));
    assertEquals("m-1", n.get("model").asText());
    assertEquals(0, n.get("temperature").asInt());
    assertTrue(
        n.get("provider").get("require_parameters").asBoolean(),
        "an unsupported temperature must fail, not be ignored");
    assertEquals("system", n.get("messages").get(0).get("role").asText());
    assertEquals("sys", n.get("messages").get(0).get("content").asText());
    assertEquals("user", n.get("messages").get(1).get("role").asText());
    assertEquals("hi", n.get("messages").get(1).get("content").asText());
  }

  @Test
  void parseTextReturnsFirstChoiceContent() {
    assertEquals(
        "hello", OpenRouterLlm.parseText("{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}"));
  }

  @Test
  void parseTextSurfacesErrorBodyOn200() {
    var e =
        assertThrows(
            IllegalStateException.class,
            () -> OpenRouterLlm.parseText("{\"error\":{\"message\":\"boom\"}}"));
    assertTrue(e.getMessage().contains("boom"), e.getMessage());
  }

  @Test
  void fromEnvWithoutKeyExplainsWhatToDo() {
    // only meaningful when the key is unset; skip otherwise
    org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("OPENROUTER_API_KEY") == null);
    var e = assertThrows(IllegalStateException.class, () -> OpenRouterLlm.fromEnv("m"));
    assertTrue(e.getMessage().contains("OPENROUTER_API_KEY"));
  }

  @Test
  void effortIsSentAsReasoningEffortWhenSet() throws Exception {
    var n =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(OpenRouterLlm.requestBody("m", "s", "u", "low"));
    assertEquals("low", n.get("reasoning").get("effort").asText());
    assertEquals(0, n.get("temperature").asInt());
    assertTrue(n.get("provider").get("require_parameters").asBoolean());
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
  void noReasoningFieldWhenEffortIsNull() throws Exception {
    var n =
        new com.fasterxml.jackson.databind.ObjectMapper()
            .readTree(OpenRouterLlm.requestBody("m", "s", "u"));
    assertFalse(n.has("reasoning"));
  }
}

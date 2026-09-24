package llm;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnthropicLlmTest {
    private static final ObjectMapper M = new ObjectMapper();

    @Test void requestBodyPinsTemperatureZeroAndCarriesModelAndPrompts() throws Exception {
        JsonNode n = M.readTree(AnthropicLlm.requestBody("m-1", "sys", "hi"));
        assertEquals("m-1", n.get("model").asText());
        assertEquals(0, n.get("temperature").asInt());
        assertEquals("sys", n.get("system").asText());
        assertEquals("hi", n.get("messages").get(0).get("content").asText());
    }

    @Test void parseTextReturnsFirstTextBlock() {
        assertEquals("hello", AnthropicLlm.parseText("{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}"));
    }

    @Test void fromEnvWithoutKeyExplainsWhatToDo() {
        // only meaningful when the key is unset; skip otherwise
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("ANTHROPIC_API_KEY") == null);
        var e = assertThrows(IllegalStateException.class, () -> AnthropicLlm.fromEnv("m"));
        assertTrue(e.getMessage().contains("ANTHROPIC_API_KEY"));
    }
}

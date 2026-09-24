package assistant;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AssistantTest {
    private final List<Chunk> chunks = List.of(
        new Chunk("bs#browser-support", "bs", "Browser Support Safari 14 bundle.js"),
        new Chunk("cm#modes", "cm", "Consent Mode default consent states"));

    private Assistant with(String modelOutput, List<String> seenUserPrompts) {
        return new Assistant(new BM25Index(chunks), (sys, user) -> { seenUserPrompts.add(user); return modelOutput; }, 2);
    }

    @Test void answersWithCitedClaims() {
        var prompts = new ArrayList<String>();
        var r = with("{\"refused\":false,\"claims\":[{\"claim\":\"bundle.js supports Safari 14\",\"citations\":[\"bs#browser-support\"]}]}", prompts)
            .answer("Which browsers does bundle.js support?");
        assertFalse(r.refused());
        assertEquals(List.of("bs#browser-support"), r.claims().get(0).citations());
        assertTrue(prompts.get(0).contains("[bs#browser-support]"), "prompt must expose chunk ids to the model");
    }

    @Test void refusalHasNoClaims() {
        var r = with("{\"refused\":true,\"claims\":[]}", new ArrayList<>()).answer("How much does it cost?");
        assertTrue(r.refused());
        assertTrue(r.claims().isEmpty());
    }

    @Test void refusedWithClaimsIsNormalisedToNoClaims() {
        var r = Assistant.parse("{\"refused\":true,\"claims\":[{\"claim\":\"x\",\"citations\":[]}]}");
        assertTrue(r.claims().isEmpty());
    }

    @Test void markdownFencedJsonIsAccepted() {
        var r = Assistant.parse("```json\n{\"refused\":true,\"claims\":[]}\n```");
        assertTrue(r.refused());
    }

    @Test void proseInsteadOfJsonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Assistant.parse("Sorry, I cannot help with that."));
    }

    @Test void extraKeysFromModelAreIgnored() {
        var r = Assistant.parse("{\"refused\":true,\"claims\":[],\"answer\":\"hi\"}");
        assertTrue(r.refused());
    }

    @Test void emptyObjectIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Assistant.parse("{}"));
    }

    @Test void objectWithoutRefusedIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Assistant.parse("{\"answer\":\"hi\"}"));
    }

    @Test void nonRefusalWithoutClaimsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> Assistant.parse("{\"refused\":false}"));
    }

    @Test void refusalWithoutClaimsKeyIsAccepted() {
        var r = Assistant.parse("{\"refused\":true}");
        assertTrue(r.refused());
        assertTrue(r.claims().isEmpty());
    }
}

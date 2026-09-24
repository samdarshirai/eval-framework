package assistant;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BM25IndexTest {
    private final List<Chunk> chunks = List.of(
        new Chunk("d#safari", "d", "Safari 14 browsers supported by bundle.js"),
        new Chunk("d#consent", "d", "Google Consent Mode default consent states"),
        new Chunk("d#geo", "d", "Geolocation rulesets regional settings"));

    @Test void rankedByTermMatch() {
        var top = new BM25Index(chunks).search("which browsers does bundle.js support", 2);
        assertEquals("d#safari", top.get(0).id());
    }

    @Test void alwaysReturnsKChunksEvenWithNoTermOverlap() {
        var top = new BM25Index(chunks).search("pricing subscription", 3);
        assertEquals(3, top.size());
    }

    @Test void kLargerThanCorpusReturnsAll() {
        assertEquals(3, new BM25Index(chunks).search("consent", 10).size());
    }
}

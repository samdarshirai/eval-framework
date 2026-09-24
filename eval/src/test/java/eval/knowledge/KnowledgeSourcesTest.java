package eval.knowledge;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeSourcesTest {
    private static final Path ROOT = Path.of(".");

    @Test void absentBlockDefaultsToDocsDir() {
        assertInstanceOf(DocsDirSource.class, KnowledgeSources.from(Map.of(), ROOT));
    }

    @Test void docsTypeReadsRealChunks() throws Exception {
        var chunks = KnowledgeSources.from(Map.of("knowledgeBase", Map.of("type", "docs", "path", "docs")), ROOT).chunks();
        assertTrue(chunks.stream().anyMatch(c -> c.id().equals("tcf2#non-iab-vendors-1")));
    }

    @Test void httpAndManifestArePlaceholdersThatSayNotImplemented() {
        var http = KnowledgeSources.from(Map.of("knowledgeBase", Map.of("type", "http", "url", "http://x/chunks")), ROOT);
        var manifest = KnowledgeSources.from(Map.of("knowledgeBase", Map.of("type", "manifest", "path", "m.json")), ROOT);
        assertTrue(assertThrows(UnsupportedOperationException.class, http::chunks).getMessage().contains("not implemented yet"));
        assertTrue(assertThrows(UnsupportedOperationException.class, manifest::chunks).getMessage().contains("not implemented yet"));
    }

    @Test void unknownTypeListsTheKnownOnes() {
        var e = assertThrows(IllegalArgumentException.class,
            () -> KnowledgeSources.from(Map.of("knowledgeBase", Map.of("type", "s3")), ROOT));
        assertTrue(e.getMessage().contains("'s3'") && e.getMessage().contains("docs, http, manifest"), e.getMessage());
    }

    @Test void httpWithoutUrlAndNonMapBlockAreErrors() {
        assertTrue(assertThrows(IllegalArgumentException.class,
            () -> KnowledgeSources.from(Map.of("knowledgeBase", Map.of("type", "http")), ROOT)).getMessage().contains("'url'"));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeSources.from(Map.of("knowledgeBase", "docs"), ROOT));
    }
}

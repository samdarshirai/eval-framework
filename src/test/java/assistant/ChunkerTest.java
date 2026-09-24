package assistant;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ChunkerTest {
    private static List<String> ids(String md) {
        return Chunker.chunk("d", md).stream().map(Chunk::id).toList();
    }

    @Test void idsAreDocPlusHeadingSlug() {
        assertEquals(List.of("d#t", "d#default-consent-states", "d#sub-one"),
            ids("# T\nintro\n## Default Consent States\nx\n### Sub One\ny"));
    }

    @Test void duplicateHeadingsGetSuffixFromOneAndSingletonsGetNone() {
        assertEquals(List.of("d#overview-1", "d#other", "d#overview-2"),
            ids("## Overview\na\n## Other\nb\n## Overview\nc"));
    }

    @Test void emptyBodyHeadingSkippedButStillCountsForSuffix() {
        assertEquals(List.of("d#overview-2"), ids("## Overview\n## Overview\nc"));
    }

    @Test void reorderingOrAddingSectionsLeavesOtherIdsUnchanged() {
        Set<String> base = new HashSet<>(ids("## A\na\n## B\nb\n## C\nc"));
        assertEquals(base, new HashSet<>(ids("## C\nc\n## A\na\n## B\nb")));
        Set<String> plusD = new HashSet<>(ids("## A\na\n## D\nd\n## B\nb\n## C\nc"));
        assertTrue(plusD.containsAll(base));
    }

    @Test void hashInsideCodeFenceIsNotAHeading() {
        assertEquals(List.of("d#a"), ids("## A\n```\n# not a heading\n```\ntext"));
    }

    @Test void deeperHeadingsFoldIntoParent() {
        var chunks = Chunker.chunk("d", "## A\n#### Deep\nz");
        assertEquals(1, chunks.size());
        assertTrue(chunks.get(0).text().contains("Deep"));
    }

    @Test void frontmatterIsStripped() {
        var chunks = Chunker.chunk("d", "---\nsource: http://x\n---\n## A\nbody");
        assertEquals(List.of("d#a"), chunks.stream().map(Chunk::id).toList());
        assertFalse(chunks.get(0).text().contains("source:"));
    }

    @Test void realDocsHaveUniqueIdsAndTheExpectedAnchors() throws Exception {
        List<String> all = Chunker.chunkDir(Path.of("docs")).stream().map(Chunk::id).toList();
        assertEquals(all.size(), new HashSet<>(all).size(), "chunk IDs must be unique");
        assertTrue(all.contains("browser-support#browser-support"));
        assertTrue(all.contains("browser-support#libraries-support"));
        assertTrue(all.contains("tcf2#non-iab-vendors-1"));
        assertTrue(all.contains("tcf2#non-iab-vendors-2"));
        for (String doc : List.of("browser-support", "ab-test", "geolocation-rules", "consent-mode", "tcf2"))
            assertTrue(all.stream().anyMatch(i -> i.startsWith(doc + "#")), "no chunks for " + doc);
    }
}

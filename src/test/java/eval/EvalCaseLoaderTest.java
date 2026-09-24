package eval;

import assistant.Chunk;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EvalCaseLoaderTest {
    @TempDir Path dir;
    private final KnowledgeBase kb = new KnowledgeBase(List.of(new Chunk("d#a", "d", "x"), new Chunk("d#b", "d", "y")));

    private void write(String name, String yaml) throws Exception { Files.writeString(dir.resolve(name), yaml); }
    private String err(Runnable r) { return assertThrows(IllegalArgumentException.class, r::run).getMessage(); }
    private static final String META = "  source: authored\n  owner: platform\n  added: \"2026-09-24\"\n";

    @Test void loadsAnswerCaseWithGoldChunksAndOptionalKeywords() throws Exception {
        write("a.yaml", """
            - id: c1
              question: Q?
              category: single-source
              expected_behavior: answer
              facts:
                - fact: F
                  chunks: [d#a, d#b]
                  keywords: [Safari, "14"]
            """ + META);
        var c = EvalCaseLoader.load(dir, kb).get(0);
        assertEquals(List.of("d#a", "d#b"), c.facts().get(0).chunks());
        assertEquals(List.of("Safari", "14"), c.facts().get(0).keywords());
    }

    @Test void refuseCaseNeedsNoFacts() throws Exception {
        write("a.yaml", "- id: c1\n  question: Q?\n  category: out-of-scope\n  subtype: unrelated\n  expected_behavior: refuse\n" + META);
        var c = EvalCaseLoader.load(dir, kb).get(0);
        assertTrue(c.facts().isEmpty());
        assertEquals("unrelated", c.subtype());
    }

    @Test void missingGoldChunkIsHardErrorNamingCaseAndChunk() throws Exception {
        write("a.yaml", "- id: c1\n  question: Q?\n  category: single-source\n  expected_behavior: answer\n  facts:\n    - {fact: F, chunks: [d#nope]}\n" + META);
        String m = err(() -> { try { EvalCaseLoader.load(dir, kb); } catch (java.io.IOException e) { throw new RuntimeException(e); } });
        assertTrue(m.contains("c1") && m.contains("d#nope"), m);
    }

    @Test void unquotedYamlDateIsAccepted() throws Exception {
        write("a.yaml", "- id: c1\n  question: Q?\n  category: out-of-scope\n  expected_behavior: refuse\n  source: authored\n  owner: platform\n  added: 2026-09-24\n");
        assertEquals("2026-09-24", EvalCaseLoader.load(dir, kb).get(0).added());
    }

    @Test void badInputsGiveOneClearError() throws Exception {
        String base = "- id: c1\n  question: Q?\n  category: x\n";
        write("a.yaml", base + "  expected_behavior: maybe\n" + META);
        assertTrue(err(() -> load()).contains("expected_behavior"));
        write("a.yaml", base + "  expected_behavior: answer\n" + META);          // answer with no facts
        assertTrue(err(() -> load()).contains("c1"));
        write("a.yaml", "- id: c1\n  question: Q?\n  category: x\n  expected_behavior: refuse\n" + META
            + "- id: c1\n  question: Q2?\n  category: x\n  expected_behavior: refuse\n" + META);
        assertTrue(err(() -> load()).contains("duplicate"));
        write("a.yaml", "- id: [unclosed\n");
        assertTrue(err(() -> load()).contains("a.yaml"));
        Files.delete(dir.resolve("a.yaml"));
        assertTrue(err(() -> load()).contains("no cases"));
    }

    @Test void wrongTypedFieldsGiveClearErrorsNotCrashesOrSilentDrops() throws Exception {
        String head = "- id: c1\n  question: Q?\n  category: x\n  expected_behavior: answer\n";
        write("a.yaml", head + "  facts:\n    - some fact text\n" + META);
        String m = err(() -> load());
        assertTrue(m.contains("c1") && m.contains("fact"), m);
        write("a.yaml", head + "  facts:\n    -\n" + META);
        m = err(() -> load());
        assertTrue(m.contains("c1") && m.contains("fact"), m);
        write("a.yaml", head + "  facts:\n    - {fact: F, chunks: [d#a], keywords: Safari}\n" + META);
        m = err(() -> load());
        assertTrue(m.contains("c1") && m.contains("keywords") && m.contains("list"), m);
        write("a.yaml", head + "  facts:\n    - {fact: F, chunks: d#a}\n" + META);
        m = err(() -> load());
        assertTrue(m.contains("c1") && m.contains("chunks") && m.contains("list"), m);
        write("a.yaml", "- id: c1\n  question: Q?\n  category: x\n  expected_behavior: refuse\n  facts: {fact: F}\n" + META);
        m = err(() -> load());
        assertTrue(m.contains("c1") && m.contains("facts") && m.contains("list"), m);
    }

    private void load() { try { EvalCaseLoader.load(dir, kb); } catch (java.io.IOException e) { throw new RuntimeException(e); } }
}

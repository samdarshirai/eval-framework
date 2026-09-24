package eval.knowledge;

import kb.Chunk;
import java.nio.file.Path;
import java.util.List;

/**
 * PLACEHOLDER, not implemented yet. Intended: read a JSON file holding an array of {@code {id, text}} that the app's
 * own build exports from the same pipeline it indexes. The source doc for a chunk is the part of the id before '#'.
 */
public final class ManifestSource implements KnowledgeSource {
    private final Path manifest;

    public ManifestSource(Path manifest) { this.manifest = manifest; }

    @Override public List<Chunk> chunks() {
        throw new UnsupportedOperationException("knowledgeBase type 'manifest' is not implemented yet (would read "
            + manifest + " as [{id, text}]). Use type 'docs' for now.");
    }
}

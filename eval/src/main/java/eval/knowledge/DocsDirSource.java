package eval.knowledge;

import kb.Chunk;
import kb.Chunker;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Default: chunk a docs directory with the reference Chunker, the same rules the stub assistant uses. */
public final class DocsDirSource implements KnowledgeSource {
    private final Path docsDir;

    public DocsDirSource(Path docsDir) { this.docsDir = docsDir; }

    @Override public List<Chunk> chunks() throws IOException { return Chunker.chunkDir(docsDir); }
}

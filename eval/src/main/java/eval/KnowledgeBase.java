package eval;

import kb.Chunk;
import eval.knowledge.KnowledgeSource;
import eval.knowledge.KnowledgeSources;
import java.io.IOException;
import java.util.*;

public final class KnowledgeBase {
    private final Map<String, Chunk> byId = new LinkedHashMap<>();

    public KnowledgeBase(KnowledgeSource source) throws IOException { this(source.chunks()); }
    public KnowledgeBase(List<Chunk> chunks) { chunks.forEach(c -> byId.put(c.id(), c)); }

    /** The chunks the config's {@code knowledgeBase} setting names (docs directory by default). */
    static KnowledgeBase from(EvalConfig config) throws IOException {
        return new KnowledgeBase(
                KnowledgeSources.from(config.raw(), config.baseDir(), config.fileName()));
    }

    public boolean has(String id) { return byId.containsKey(id); }
    public Chunk get(String id) { return byId.get(id); }
    public Set<String> ids() { return byId.keySet(); }
}

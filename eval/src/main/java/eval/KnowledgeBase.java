package eval;

import kb.Chunk;
import eval.knowledge.KnowledgeSource;
import java.io.IOException;
import java.util.*;

public final class KnowledgeBase {
    private final Map<String, Chunk> byId = new LinkedHashMap<>();

    public KnowledgeBase(KnowledgeSource source) throws IOException { this(source.chunks()); }
    public KnowledgeBase(List<Chunk> chunks) { chunks.forEach(c -> byId.put(c.id(), c)); }

    public boolean has(String id) { return byId.containsKey(id); }
    public Chunk get(String id) { return byId.get(id); }
    public Set<String> ids() { return byId.keySet(); }
}

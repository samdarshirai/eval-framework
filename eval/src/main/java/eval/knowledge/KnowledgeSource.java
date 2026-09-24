package eval.knowledge;

import kb.Chunk;
import java.io.IOException;
import java.util.List;

/**
 * Where the harness gets the chunks the assistant was built on. All the harness needs is the same set of chunk IDs
 * and text the assistant has, so the source is pluggable and chosen by {@code knowledgeBase.type} in eval/config.yaml.
 * To add a source: implement this interface and add one case to {@link KnowledgeSources}.
 */
public interface KnowledgeSource {
    List<Chunk> chunks() throws IOException;
}

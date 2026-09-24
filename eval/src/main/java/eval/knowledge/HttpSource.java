package eval.knowledge;

import kb.Chunk;
import java.util.List;

/**
 * PLACEHOLDER, not implemented yet. Intended: GET {@code url} and read a JSON array of {@code {id, text}}, so the
 * harness sees exactly the chunks the app indexed, with no chunking rules reimplemented on the harness side.
 * Note for when it is built: this needs the app running before cases are validated, which changes the D8 ordering
 * (validate cases before contacting the assistant); amend D8 then.
 */
public final class HttpSource implements KnowledgeSource {
    private final String url;

    public HttpSource(String url) { this.url = url; }

    @Override public List<Chunk> chunks() {
        throw new UnsupportedOperationException("knowledgeBase type 'http' is not implemented yet (would GET " + url
            + " and read [{id, text}]). Use type 'docs' for now.");
    }
}

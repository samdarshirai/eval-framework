package assistant;

import kb.Chunk;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import llm.Llm;
import java.util.List;

public final class Assistant {
    private static final ObjectMapper M = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    static final String SYSTEM = """
        You answer questions about Usercentrics Web CMP v2 using ONLY the documentation chunks provided.
        Reply with a single JSON object and nothing else:
        {"refused": boolean, "claims": [{"claim": string, "citations": [chunkId, ...]}]}
        Rules:
        - Each claim is one atomic factual statement. Cite the id(s) of the chunk(s) that support it.
        - If the chunks do not contain the answer, reply {"refused": true, "claims": []}. Never answer from outside knowledge.
        - Output no other text.
        """;

    private final BM25Index index;
    private final Llm llm;
    private final int topK;

    public Assistant(BM25Index index, Llm llm, int topK) { this.index = index; this.llm = llm; this.topK = topK; }

    public AssistantResponse answer(String question) {
        StringBuilder ctx = new StringBuilder();
        for (Chunk c : index.search(question, topK))
            ctx.append('[').append(c.id()).append("]\n").append(c.text()).append("\n\n");
        return parse(llm.complete(SYSTEM, ctx + "Question: " + question));
    }

    static AssistantResponse parse(String raw) {
        String s = raw.strip().replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
        try {
            JsonNode n = M.readTree(s);
            if (!n.isObject() || !n.path("refused").isBoolean()
                || !(n.path("refused").asBoolean() || n.path("claims").isArray()))
                throw new IllegalArgumentException("missing refused/claims");
            AssistantResponse r = M.treeToValue(n, AssistantResponse.class);
            List<Claim> claims = r.refused() || r.claims() == null ? List.of() : r.claims();
            return new AssistantResponse(r.refused(), claims);
        } catch (Exception e) {
            throw new IllegalArgumentException("Model output is not the expected JSON: " + raw, e);
        }
    }
}

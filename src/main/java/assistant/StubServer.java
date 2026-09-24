package assistant;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import llm.AnthropicLlm;
import org.yaml.snakeyaml.Yaml;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

public final class StubServer {
    private static final ObjectMapper M = new ObjectMapper();

    public static HttpServer create(int port, Assistant assistant) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        server.createContext("/answer", ex -> {
            int status; byte[] body;
            try {
                if (!ex.getRequestMethod().equals("POST")) { status = 405; body = msg("POST only"); }
                else {
                    String q;
                    try { q = M.readTree(ex.getRequestBody()).path("question").asText(""); }
                    catch (com.fasterxml.jackson.core.JsonProcessingException e) { q = ""; }
                    if (q.isBlank()) { status = 400; body = msg("question is required"); }
                    else { status = 200; body = M.writeValueAsBytes(assistant.answer(q)); }
                }
            } catch (Exception e) { status = 500; body = msg(e.getMessage()); }
            ex.getResponseHeaders().set("content-type", "application/json");
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        return server;
    }

    private static byte[] msg(String m) { return ("{\"error\":" + M.valueToTree(String.valueOf(m)) + "}").getBytes(StandardCharsets.UTF_8); }

    public static void main(String[] args) throws Exception {
        Map<String, Object> cfg = new Yaml().load(Files.readString(Path.of("config/assistant.yaml")));
        var chunks = Chunker.chunkDir(Path.of("docs"));
        var assistant = new Assistant(new BM25Index(chunks), AnthropicLlm.fromEnv((String) cfg.get("model")), ((Number) cfg.get("topK")).intValue());
        int port = ((Number) cfg.get("port")).intValue();
        create(port, assistant).start();
        System.out.println("Stub assistant listening on http://localhost:" + port + "/answer (" + chunks.size() + " chunks)");
    }
}

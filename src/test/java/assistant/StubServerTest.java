package assistant;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StubServerTest {
    private HttpServer server;
    private String base;

    @BeforeEach void start() throws Exception {
        var idx = new BM25Index(List.of(new Chunk("bs#a", "bs", "Safari 14")));
        var a = new Assistant(idx, (s, u) -> "{\"refused\":false,\"claims\":[{\"claim\":\"c\",\"citations\":[\"bs#a\"]}]}", 1);
        server = StubServer.create(0, a);
        server.start();
        base = "http://localhost:" + server.getAddress().getPort() + "/answer";
    }
    @AfterEach void stop() { server.stop(0); }

    private HttpResponse<String> send(String method, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base)).header("content-type", "application/json");
        b = method.equals("POST") ? b.POST(HttpRequest.BodyPublishers.ofString(body)) : b.GET();
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void postReturnsExactlyRefusedAndClaims() throws Exception {
        var res = send("POST", "{\"question\":\"Which Safari?\"}");
        assertEquals(200, res.statusCode());
        JsonNode n = new ObjectMapper().readTree(res.body());
        assertEquals(java.util.Set.of("refused", "claims"), new java.util.HashSet<>(java.util.stream.StreamSupport
            .stream(java.util.Spliterators.spliteratorUnknownSize(n.fieldNames(), 0), false).toList()));
        assertEquals("bs#a", n.get("claims").get(0).get("citations").get(0).asText());
    }

    @Test void listensOnLoopbackOnly() { assertTrue(server.getAddress().getAddress().isLoopbackAddress(), server.getAddress().toString()); }

    @Test void blankQuestionIs400() throws Exception { assertEquals(400, send("POST", "{\"question\":\" \"}").statusCode()); }
    @Test void nonJsonRequestBodyIs400() throws Exception { assertEquals(400, send("POST", "not json").statusCode()); }
    @Test void getIs405() throws Exception { assertEquals(405, send("GET", null).statusCode()); }

    @Test void unparseableModelOutputIs500NotAHang() throws Exception {
        server.stop(0);
        var a = new Assistant(new BM25Index(List.of(new Chunk("bs#a", "bs", "x"))), (s, u) -> "not json", 1);
        server = StubServer.create(0, a); server.start();
        base = "http://localhost:" + server.getAddress().getPort() + "/answer";
        assertEquals(500, send("POST", "{\"question\":\"q\"}").statusCode());
    }
}

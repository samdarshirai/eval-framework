package eval;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class AssistantClientTest {
    private HttpServer server;
    private final AtomicReference<String> header = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{\"refused\":false,\"claims\":[{\"claim\":\"c\",\"citations\":[\"d#a\"]}],\"extra\":1}";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/answer", ex -> {
            header.set(ex.getRequestHeaders().getFirst("X-Eval-Run"));
            body.set(new String(ex.getRequestBody().readAllBytes()));
            byte[] b = reply.getBytes();
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }
    private String url() { return "http://localhost:" + server.getAddress().getPort() + "/answer"; }

    @Test void postsQuestionWithEvalRunHeaderAndParsesClaims() throws Exception {
        Answer a = new AssistantClient(url()).ask("Which Safari?", "run-1");
        assertEquals("run-1", header.get());
        assertTrue(body.get().contains("Which Safari?"));
        assertFalse(a.refused());
        assertEquals("d#a", a.claims().get(0).citations().get(0));
    }

    @Test void non200BecomesIOExceptionWithStatus() {
        status = 500; reply = "{\"error\":\"boom\"}";
        var e = assertThrows(IOException.class, () -> new AssistantClient(url()).ask("q", "r"));
        assertTrue(e.getMessage().contains("500"));
    }

    @Test void garbageBodyBecomesIOException() {
        reply = "not json";
        assertThrows(IOException.class, () -> new AssistantClient(url()).ask("q", "r"));
    }

    @Test void reachableTrueWhenUpFalseWhenDown() {
        assertTrue(new AssistantClient(url()).reachable());
        server.stop(0);
        assertFalse(new AssistantClient(url()).reachable());
    }
}

package assistant;

import com.fasterxml.jackson.databind.*;
import llm.Llm;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StubServerTest {
    @LocalServerPort int port;
    @Autowired Environment env;
    @MockBean Llm llm;

    @BeforeEach void modelAnswers() {
        when(llm.complete(any(), any())).thenReturn("{\"refused\":false,\"claims\":[{\"claim\":\"c\",\"citations\":[\"bs#a\"]}]}");
    }

    private HttpResponse<String> send(String method, String body) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/answer")).header("content-type", "application/json");
        b = method.equals("POST") ? b.POST(HttpRequest.BodyPublishers.ofString(body)) : b.GET();
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test void postReturnsExactlyRefusedAndClaims() throws Exception {
        var res = send("POST", "{\"question\":\"Which Safari?\"}");
        assertEquals(200, res.statusCode());
        JsonNode n = new ObjectMapper().readTree(res.body());
        var fields = new HashSet<String>();
        n.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("refused", "claims"), fields);
        assertEquals("bs#a", n.get("claims").get(0).get("citations").get(0).asText());
    }

    @Test void listensOnLoopbackOnly() { assertEquals("127.0.0.1", env.getProperty("server.address")); }

    @Test void blankQuestionIs400() throws Exception { assertEquals(400, send("POST", "{\"question\":\" \"}").statusCode()); }
    @Test void nonJsonRequestBodyIs400() throws Exception { assertEquals(400, send("POST", "not json").statusCode()); }
    @Test void getIs405() throws Exception { assertEquals(405, send("GET", null).statusCode()); }

    @Test void unparseableModelOutputIs500NotAHang() throws Exception {
        when(llm.complete(any(), any())).thenReturn("not json");
        assertEquals(500, send("POST", "{\"question\":\"q\"}").statusCode());
    }

    @Test void failedLlmCallIs500() throws Exception {
        when(llm.complete(any(), any())).thenThrow(new IllegalStateException("OpenRouter API 529"));
        var res = send("POST", "{\"question\":\"q\"}");
        assertEquals(500, res.statusCode());
        assertTrue(res.body().contains("OpenRouter API 529"), res.body());
    }
}

package assistant;

import kb.Chunker;
import llm.AnthropicLlm;
import llm.Llm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import java.io.IOException;
import java.nio.file.Path;

@SpringBootApplication
public class StubServer {
    public static void main(String[] args) {
        SpringApplication.run(StubServer.class, args);
    }

    @Bean BM25Index index(@Value("${assistant.docs}") String docs) throws IOException {
        return new BM25Index(Chunker.chunkDir(Path.of(docs)));
    }

    @Bean Llm llm(@Value("${assistant.model}") String model) { return AnthropicLlm.fromEnv(model); }

    @Bean Assistant assistant(BM25Index index, Llm llm, @Value("${assistant.topK}") int topK) {
        return new Assistant(index, llm, topK);
    }
}

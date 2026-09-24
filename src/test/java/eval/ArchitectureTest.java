package eval;

import org.junit.jupiter.api.Test;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureTest {
    @Test void evalOnlyImportsChunkAndChunkerFromAssistant() throws Exception {
        Set<String> allowed = Set.of("import assistant.Chunk;", "import assistant.Chunker;");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/eval"))) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList())
                for (String line : Files.readAllLines(f))
                    if (line.startsWith("import assistant.") && !allowed.contains(line.strip()))
                        fail(f + " breaks the HTTP-only boundary: " + line);
        }
    }
}

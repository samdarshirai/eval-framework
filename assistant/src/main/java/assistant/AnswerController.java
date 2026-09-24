package assistant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@RestController
class AnswerController {
    record Question(String question) {}

    private final Assistant assistant;

    AnswerController(Assistant assistant) { this.assistant = assistant; }

    @PostMapping("/answer")
    AssistantResponse answer(@RequestBody Question q) {
        if (q.question() == null || q.question().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "question is required");
        return assistant.answer(q.question());
    }

    /** Unparseable model output or a failed LLM call: a clear 500, never a fake answer. */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> failed(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", String.valueOf(e.getMessage())));
    }
}

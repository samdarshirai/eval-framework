package eval;

import java.util.List;

public record ExpectedFact(String fact, List<String> chunks, List<String> keywords) {}

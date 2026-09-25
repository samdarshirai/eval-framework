package llm;

/** A reply and the token counts the provider reported for it (0 when it reported none). */
public record Completion(String text, long promptTokens, long completionTokens) {}

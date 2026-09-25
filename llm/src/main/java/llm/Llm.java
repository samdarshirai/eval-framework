package llm;

public interface Llm {
    String complete(String system, String user);

    /** The same call, plus the token counts the provider reported; 0 for an Llm that reports none. */
    default Completion completeWithUsage(String system, String user) {
        return new Completion(complete(system, user), 0, 0);
    }
}

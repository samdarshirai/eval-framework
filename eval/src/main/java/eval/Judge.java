package eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import llm.Llm;
import java.util.*;
import java.util.regex.*;

/** Two narrow judge questions used by Coverage (D24). The prompts are the only place judge wording lives. */
public final class Judge {
    private static final ObjectMapper M = new ObjectMapper();
    // Leading markdown or quotes are ignored ("**YES**"); the first word must still be yes or no.
    private static final Pattern FIRST_WORD = Pattern.compile("^[\\s*`\"']*(yes|no)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ARRAY = Pattern.compile("\\[[\\d,\\s]*\\]");

    private static final String AGREE_SYSTEM = """
        You compare a documented fact with a claim made by an assistant.
        Answer YES if the claim states the fact. Extra correct detail is fine.
        Answer NO if the claim contradicts the fact, negates it, limits it with an exception, or gives a different value or version.
        Reply with exactly one word: YES or NO.""";

    private static final String COVERING_SYSTEM = """
        You are given a documented fact and a numbered list of claims made by an assistant.
        Return the numbers of the claims that each state the fact. Extra correct detail is fine.
        A claim that contradicts the fact, negates it, limits it with an exception, or gives a different value or version does not state it.
        Reply with a JSON array of numbers only, for example [1, 3]. Reply [] if no claim states the fact.""";

    private final Llm llm;
    public Judge(Llm llm) { this.llm = llm; }

    /** Do the fact and the claim agree? */
    public boolean agree(String fact, String claim) {
        String reply = llm.complete(AGREE_SYSTEM, "Fact: " + fact + "\nClaim: " + claim);
        Matcher m = FIRST_WORD.matcher(reply == null ? "" : reply);
        if (!m.find()) throw new IllegalStateException("judge returned neither YES nor NO: \"" + reply + "\"");
        return m.group(1).equalsIgnoreCase("yes");
    }

    /** Zero-based indices of the claims that state the fact; empty means none does. */
    public List<Integer> covering(String fact, List<Claim> claims) {
        StringBuilder numbered = new StringBuilder();
        for (int i = 0; i < claims.size(); i++) numbered.append(i + 1).append(". ").append(claims.get(i).claim()).append('\n');
        String reply = llm.complete(COVERING_SYSTEM, "Fact: " + fact + "\nClaims:\n" + numbered);
        Matcher m = ARRAY.matcher(reply == null ? "" : reply);
        if (!m.find()) throw new IllegalStateException("judge returned no JSON array: \"" + reply + "\"");
        try {
            int[] numbers = M.readValue(m.group(), int[].class);
            List<Integer> indices = new ArrayList<>();
            for (int n : numbers) {
                if (n < 1 || n > claims.size()) throw new IllegalStateException("judge returned claim number " + n + " but there are " + claims.size() + " claims");
                if (!indices.contains(n - 1)) indices.add(n - 1);
            }
            return indices;
        } catch (java.io.IOException e) {
            throw new IllegalStateException("judge returned an unreadable array: \"" + reply + "\"", e);
        }
    }
}

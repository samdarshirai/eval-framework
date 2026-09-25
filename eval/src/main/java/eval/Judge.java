package eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.regex.*;
import llm.Llm;

/**
 * Four narrow judge questions used by Coverage, Groundedness and Relevance (D24, D10, D12). The
 * prompts are the only place judge wording lives.
 */
public final class Judge {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  // Leading markdown or quotes are ignored ("**YES**"); the first word must still be yes or no.
  private static final Pattern FIRST_WORD =
      Pattern.compile("^[\\s*`\"']*(yes|no)\\b", Pattern.CASE_INSENSITIVE);
  private static final Pattern ARRAY = Pattern.compile("\\[[\\d,\\s]*\\]");

  private static final String AGREE_SYSTEM_PROMPT =
      """
You compare a documented fact with a claim made by an assistant.
Answer YES if the claim states the fact. Extra correct detail is fine.
Answer NO if the claim contradicts the fact, negates it, limits it with an exception, or gives a different value or version.
Reply with exactly one word: YES or NO.\
""";

  private static final String COVERING_SYSTEM_PROMPT =
      """
You are given a documented fact and a numbered list of claims made by an assistant.
Return the numbers of the claims that each state the fact. Extra correct detail is fine.
A claim that contradicts the fact, negates it, limits it with an exception, or gives a different value or version does not state it.
Reply with a JSON array of numbers only, for example [1, 3]. Reply [] if no claim states the fact.\
""";

  private static final String SUPPORTS_SYSTEM_PROMPT =
      """
You check whether a passage from documentation supports a claim made by an assistant.
Answer YES only if everything the claim states is stated in the passage or follows directly from it, including every number, version, condition and qualifier such as "always", "only" or "all".
Answer NO if the claim adds a detail the passage does not state, changes a number or version, overstates or drops a qualifier, contradicts the passage, or is not about what the passage says.
The claim may be worded differently from the passage and may combine sentences of the passage.
Reply with exactly one word: YES or NO.\
""";

  private static final String RELEVANT_SYSTEM_PROMPT =
      """
You check whether a claim made by an assistant is pertinent to the user's question.
Answer YES if the claim helps answer the question, including background the answer needs to make sense.
Answer NO if the claim is off-topic padding: it may be true, but it does not help answer this question.
Reply with exactly one word: YES or NO.\
""";

  private final Llm llm;

  public Judge(Llm llm) {
    this.llm = llm;
  }

  /** Do the fact and the claim agree? */
  public boolean agree(String fact, String claim) {
    return yesOrNo(llm.complete(AGREE_SYSTEM_PROMPT, "Fact: " + fact + "\nClaim: " + claim));
  }

  /** Does the passage support the claim? */
  public boolean supports(String claim, String passage) {
    return yesOrNo(
        llm.complete(SUPPORTS_SYSTEM_PROMPT, "Passage:\n" + passage + "\n\nClaim: " + claim));
  }

  /** Is the claim pertinent to the question, and not true-but-off-topic padding? */
  public boolean relevant(String question, String claim) {
    return yesOrNo(
        llm.complete(RELEVANT_SYSTEM_PROMPT, "Question: " + question + "\nClaim: " + claim));
  }

  private static boolean yesOrNo(String reply) {
    Matcher matcher = FIRST_WORD.matcher(reply == null ? "" : reply);
    if (!matcher.find()) {
      throw new IllegalStateException("judge returned neither YES nor NO: \"" + reply + "\"");
    }
    return matcher.group(1).equalsIgnoreCase("yes");
  }

  /** Zero-based indices of the claims that state the fact; empty means none does. */
  public List<Integer> covering(String fact, List<Claim> claims) {
    StringBuilder numbered = new StringBuilder();
    for (int i = 0; i < claims.size(); i++) {
      numbered.append(i + 1).append(". ").append(claims.get(i).claim()).append('\n');
    }
    String reply = llm.complete(COVERING_SYSTEM_PROMPT, "Fact: " + fact + "\nClaims:\n" + numbered);
    Matcher matcher = ARRAY.matcher(reply == null ? "" : reply);
    if (!matcher.find()) {
      throw new IllegalStateException("judge returned no JSON array: \"" + reply + "\"");
    }
    String array = matcher.group();
    if (matcher.find()) {
      throw new IllegalStateException("judge returned more than one array: \"" + reply + "\"");
    }
    try {
      int[] numbers = MAPPER.readValue(array, int[].class);
      List<Integer> indices = new ArrayList<>();
      for (int claimNumber : numbers) {
        if (claimNumber < 1 || claimNumber > claims.size()) {
          throw new IllegalStateException(
              "judge returned claim number "
                  + claimNumber
                  + " but there are "
                  + claims.size()
                  + " claims");
        }
        if (!indices.contains(claimNumber - 1)) {
          indices.add(claimNumber - 1);
        }
      }
      return indices;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("judge returned an unreadable array: \"" + reply + "\"", e);
    }
  }
}

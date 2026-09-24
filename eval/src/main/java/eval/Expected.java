package eval;

import java.util.List;

/** What a case asserts, in the assistant's response shape. Citations are gold chunks: any one of them is correct. */
public record Expected(boolean refused, List<ExpectedClaim> claims) {
    public record ExpectedClaim(String claim, List<String> citations, List<String> keywords) {}
}

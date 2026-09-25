package eval.checks;

import eval.*;
import java.util.*;
import kb.Chunk;

/**
 * Does each claim's cited chunk support it? (D53) Every claim goes to the judge, one claim and one
 * cited chunk per call, and passes when any one cited chunk supports it. There is no shortcut for
 * claims that cite a gold chunk: Coverage tolerates extra detail, so only this check can catch an
 * invented addition to a correct fact. A claim with no existing cited chunk is skipped: Citation
 * integrity reports that.
 */
public final class GroundednessCheck implements Check {
  /** Max characters of a chunk's text shown in a failure message. */
  private static final int CHUNK_PREVIEW_LENGTH = 120;

  private final KnowledgeBase kb;
  private final Judge judge;

  public GroundednessCheck(KnowledgeBase kb, Judge judge) {
    this.kb = kb;
    this.judge = judge;
  }

  @Override
  public String name() {
    return "Groundedness";
  }

  @Override
  public CheckResult run(EvalCase evalCase, Answer answer, CaseState state) {
    List<Claim> claims = answer.claims() == null ? List.of() : answer.claims();
    List<String> notes = new ArrayList<>();
    List<String> problems = new ArrayList<>();
    for (Claim claim : claims) {
      List<Chunk> citedChunks = chunksCitedBy(claim);
      if (citedChunks.isEmpty()) {
        notes.add(
            "skipped, no existing citation (see Citation integrity): \"" + claim.claim() + "\"");
      } else if (anyChunkSupports(claim, citedChunks)) {
        notes.add("grounded (judge): \"" + claim.claim() + "\"");
      } else {
        problems.add(
            "unsupported: \""
                + claim.claim()
                + "\" (cited "
                + formatCitedChunks(citedChunks)
                + ")");
      }
    }
    if (!problems.isEmpty()) {
      return CheckResult.fail(String.join("; ", problems));
    }
    return notes.isEmpty() ? CheckResult.ok() : CheckResult.ok(String.join("; ", notes));
  }

  private List<Chunk> chunksCitedBy(Claim claim) {
    List<Chunk> chunks = new ArrayList<>();
    if (claim.citations() == null) {
      return chunks;
    }
    for (String id : claim.citations()) {
      if (kb.has(id)) {
        chunks.add(kb.get(id));
      }
    }
    return chunks;
  }

  private boolean anyChunkSupports(Claim claim, List<Chunk> citedChunks) {
    for (Chunk chunk : citedChunks) {
      if (judge.supports(claim.claim(), chunk.text())) {
        return true;
      }
    }
    return false;
  }

  private static String formatCitedChunks(List<Chunk> citedChunks) {
    List<String> parts = new ArrayList<>();
    for (Chunk chunk : citedChunks) {
      String text = chunk.text().replaceAll("\\s+", " ");
      String preview =
          text.length() <= CHUNK_PREVIEW_LENGTH
              ? text
              : text.substring(0, CHUNK_PREVIEW_LENGTH) + "...";
      parts.add(chunk.id() + ": \"" + preview + "\"");
    }
    return String.join(", ", parts);
  }
}

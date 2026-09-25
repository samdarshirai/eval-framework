package eval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;

/**
 * A short fingerprint of the gold chunks a case was confirmed against (D21). It covers
 * only the case's own gold chunks, so an edit elsewhere in a document does not make every case that
 * cites the document look stale.
 */
final class CaseHash {
  private CaseHash() {}

  /**
   * 12 hex characters of SHA-256 over the id and text of every gold chunk of the case, sorted by id
   * and without repeats, or null when the case has no gold chunks (a refuse case).
   */
  static String of(EvalCase evalCase, KnowledgeBase knowledgeBase) {
    TreeSet<String> goldChunkIds = new TreeSet<>();
    for (ExpectedFact fact : evalCase.facts()) {
      goldChunkIds.addAll(fact.chunks());
    }
    if (goldChunkIds.isEmpty()) {
      return null;
    }
    StringBuilder material = new StringBuilder();
    for (String goldChunkId : goldChunkIds) {
      material
          .append(goldChunkId)
          .append('\n')
          .append(knowledgeBase.get(goldChunkId).text())
          .append('\n');
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(material.toString().getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest).substring(0, 12);
    } catch (NoSuchAlgorithmException missingAlgorithm) {
      throw new IllegalStateException(missingAlgorithm);
    }
  }
}

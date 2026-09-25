package eval;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * What a report was measured with, so a later regression can be tied to a change. {@code
 * judgePromptHash} fingerprints the judge wording; {@code gitSha} is the harness commit, suffixed
 * {@code -dirty} when the working tree has uncommitted changes, null outside a git checkout; {@code
 * assistantVersion} is whatever the team sets as {@code assistantVersion} in the config, because
 * the assistant is a black box over HTTP (D28).
 */
public record Provenance(
    String judgeModel, String judgePromptHash, String gitSha, String assistantVersion) {

  static Provenance capture(EvalConfig config, Path root) {
    Object assistantVersion = config.raw().get("assistantVersion");
    return new Provenance(
        config.requireJudgeModel(),
        Judge.promptHash(),
        gitSha(root),
        assistantVersion == null ? null : assistantVersion.toString());
  }

  private static String gitSha(Path root) {
    String sha = git(root, "rev-parse", "--short", "HEAD");
    if (sha == null) {
      return null;
    }
    String changes = git(root, "status", "--porcelain");
    return changes == null || changes.isEmpty() ? sha : sha + "-dirty";
  }

  /** Trimmed stdout of a git command, or null if git is missing or the command fails. */
  private static String git(Path root, String... args) {
    String[] command = new String[args.length + 1];
    command[0] = "git";
    System.arraycopy(args, 0, command, 1, args.length);
    try {
      Process process =
          new ProcessBuilder(command)
              .directory(root.toFile())
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      String output = new String(process.getInputStream().readAllBytes()).trim();
      return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0 ? output : null;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      return null;
    }
  }
}

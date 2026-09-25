package eval;

import eval.calibration.LabeledSample;
import eval.calibration.TrapPairs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Checks the judge before the real cases run. The judge is the LLM that decides whether an answer
 * is right, so if the judge is wrong, every verdict after it is wrong too. Two quick tests, both
 * with answers we already know:
 *
 * <ul>
 *   <li><b>Trap pairs</b>: a fact next to a claim that looks like it agrees but does not. For
 *       example the fact "Safari 14 or later is supported" against the claim "All Safari versions
 *       except 14 are supported": a keyword match would accept it, and the judge must say it does
 *       not agree. A few correct rewordings are mixed in, so a judge that always says no fails too.
 *   <li><b>Groundedness sample</b>: 20 claims, each with the doc text it cites and a hand-written
 *       label saying whether that text really supports the claim. The judge must agree with the
 *       labels.
 * </ul>
 *
 * Both sets ship inside the jar; the config can point to your own files. If the judge fails a test
 * the run exits 1. The whole step is skipped when the {@code skipCalibration} setting is true.
 */
final class CalibrationRun {
  private CalibrationRun() {}

  /** Throws, before any assistant call, when a configured calibration file cannot be read. */
  static void preflight(EvalConfig config) {
    if (config.skipCalibration()) {
      return;
    }
    requireReadable(config.trapPairsFile(), "the judge trap pairs", "them");
    requireReadable(config.labeledSampleFile(), "the labeled Groundedness sample", "it");
  }

  static SuiteReport.Calibration run(EvalConfig config, Judge judge) throws IOException {
    if (config.skipCalibration()) {
      return SuiteReport.Calibration.SKIPPED;
    }
    Path trapFile = config.trapPairsFile();
    List<TrapPairs.TrapResult> trapResults =
        trapFile == null ? TrapPairs.runBundled(judge) : TrapPairs.run(trapFile, judge);
    Path sampleFile = config.labeledSampleFile();
    LabeledSample.Result groundedness =
        sampleFile == null ? LabeledSample.runBundled(judge) : LabeledSample.run(sampleFile, judge);
    return new SuiteReport.Calibration(true, trapResults, groundedness);
  }

  /** A null file means the bundled copy is used, so there is nothing to read. */
  private static void requireReadable(Path file, String what, String pronoun) {
    if (file != null && !Files.isReadable(file)) {
      throw new IllegalArgumentException(
          "cannot read "
              + what
              + " at "
              + file
              + "\nRestore the file, or pass --skip-calibration to run without "
              + pronoun
              + ".");
    }
  }
}

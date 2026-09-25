package eval;

import eval.calibration.LabeledSample;
import eval.calibration.TrapPairs;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Judge calibration at the start of a run (D41): the trap pairs and the Groundedness sample, each
 * bundled in the jar unless the config names a file. Skipped when the {@code skipCalibration}
 * setting is true.
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

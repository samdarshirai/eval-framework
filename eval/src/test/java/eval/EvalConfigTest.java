package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalConfigTest {
  @TempDir Path dir;

  private static final String BASE =
      "endpoint: http://x/answer\npassFloor: 0.9\ncategories: [out-of-scope]\n";

  private Path write(String extra) throws Exception {
    return Files.writeString(dir.resolve("team.yaml"), BASE + extra);
  }

  @Test
  void defaultLayoutUsesEvalCasesAndCaseResultsUnderTheRoot() throws Exception {
    Files.createDirectories(dir.resolve("eval"));
    Files.writeString(dir.resolve("eval/config.yaml"), BASE);
    EvalConfig config = EvalConfig.load(dir, null);
    assertEquals(dir.resolve("eval/cases"), config.casesDir());
    assertEquals(dir.resolve("caseResults"), config.outputDir());
    assertNull(config.trapPairsFile());
    assertEquals("eval/config.yaml", config.fileName());
  }

  @Test
  void configFileDefaultsAreCasesAndCaseResultsNextToTheFile() throws Exception {
    EvalConfig config = EvalConfig.loadFile(write(""), null);
    assertEquals(dir.resolve("cases"), config.casesDir());
    assertEquals(dir.resolve("caseResults"), config.outputDir());
    assertEquals(dir, config.baseDir());
  }

  @Test
  void relativeKeysResolveAgainstTheConfigDirAndAbsoluteOnesAreKept() throws Exception {
    Path elsewhere = dir.resolve("abs").toAbsolutePath();
    EvalConfig config =
        EvalConfig.loadFile(
            write(
                "cases: my/cases\noutputDir: "
                    + elsewhere
                    + "\ncalibration:\n  trapPairs: traps.yaml\n"),
            null);
    assertEquals(dir.resolve("my/cases"), config.casesDir());
    assertEquals(elsewhere, config.outputDir());
    assertEquals(dir.resolve("traps.yaml"), config.trapPairsFile());
  }

  @Test
  void errorMessagesNameTheConfigFileActuallyUsed() throws Exception {
    Path file = dir.resolve("team.yaml");
    Files.writeString(file, BASE);
    IllegalArgumentException error =
        assertThrows(
            IllegalArgumentException.class,
            () -> EvalConfig.loadFile(file, null).requireJudgeModel());
    assertTrue(
        error.getMessage().startsWith(file + ": 'judgeModel' is required"), error.getMessage());
  }

  @Test
  void labeledSampleFileIsNullByDefaultAndResolvedAgainstTheConfigFolderWhenSet() throws Exception {
    String base = "endpoint: http://x\npassFloor: 0.9\ncategories: [out-of-scope]\njudgeModel: m\n";
    Path plain = Files.writeString(dir.resolve("plain.yaml"), base);
    assertNull(EvalConfig.loadFile(plain, null).labeledSampleFile());
    Path custom =
        Files.writeString(
            dir.resolve("custom.yaml"), base + "calibration:\n  labeledSample: pairs.yaml\n");
    assertEquals(dir.resolve("pairs.yaml"), EvalConfig.loadFile(custom, null).labeledSampleFile());
  }

  @Test
  void skipCalibrationIsOffByDefaultAndReadsTheConfigKey() throws Exception {
    assertFalse(EvalConfig.loadFile(write(""), null).skipCalibration());
    assertTrue(EvalConfig.loadFile(write("skipCalibration: true\n"), null).skipCalibration());
    assertFalse(EvalConfig.loadFile(write("skipCalibration: false\n"), null).skipCalibration());
  }

  @Test
  void skipCalibrationMustBeABooleanAndTheErrorNamesTheFile() throws Exception {
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                EvalConfig.loadFile(write("skipCalibration: \"yes please\"\n"), null)
                    .skipCalibration());
    assertTrue(
        failure.getMessage().contains("team.yaml")
            && failure.getMessage().contains("skipCalibration"),
        failure.getMessage());
  }

  @Test
  void baselineFileIsNullByDefaultAndResolvedAgainstTheConfigDirWhenSet() throws Exception {
    assertNull(EvalConfig.loadFile(write(""), null).baselineFile());
    assertEquals(
        dir.resolve("results/baseline.json"),
        EvalConfig.loadFile(write("baseline: results/baseline.json\n"), null).baselineFile());
    assertNull(EvalConfig.loadFile(write("baseline: \"\"\n"), null).baselineFile());
  }
}

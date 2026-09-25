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
  void debugIsOffByDefaultAndMustBeABoolean() throws Exception {
    assertFalse(EvalConfig.loadFile(write(""), null).debug());
    assertTrue(EvalConfig.loadFile(write("debug: true\n"), null).debug());
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> EvalConfig.loadFile(write("debug: \"yes\"\n"), null).debug());
    assertTrue(failure.getMessage().contains("'debug' must be true or false"));
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

  @Test
  void anOverrideReplacesTheConfigValueAndIsTypedLikeYaml() throws Exception {
    EvalConfig config =
        EvalConfig.loadFile(
            write("skipCalibration: false\n"),
            java.util.Map.of(
                "skipCalibration", "true", "passFloor", "0.8", "endpoint", "http://y/a"));
    assertTrue(config.skipCalibration());
    assertEquals(0.8, config.passFloor());
    assertEquals("http://y/a", config.endpoint());
  }

  @Test
  void aDottedOverrideSetsANestedKeyAndKeepsItsSiblings() throws Exception {
    EvalConfig config =
        EvalConfig.loadFile(
            write("calibration:\n  labeledSample: pairs.yaml\n"),
            java.util.Map.of("calibration.trapPairs", "traps.yaml"));
    assertEquals(dir.resolve("traps.yaml"), config.trapPairsFile());
    assertEquals(dir.resolve("pairs.yaml"), config.labeledSampleFile());
  }

  @Test
  void aDottedOverrideThroughAValueThatIsNotAMapIsAnError() throws Exception {
    var failure =
        assertThrows(
            IllegalArgumentException.class,
            () -> EvalConfig.loadFile(write(""), java.util.Map.of("endpoint.host", "x")));
    assertTrue(failure.getMessage().contains("endpoint.host"), failure.getMessage());
  }

  @Test
  void anEmptyBaselineOverrideMeansNoBaseline() throws Exception {
    EvalConfig config =
        EvalConfig.loadFile(write("baseline: old.json\n"), java.util.Map.of("baseline", ""));
    assertNull(config.baselineFile());
  }

  @Test
  void onlyKnownTopLevelKeysAreSettings() {
    assertTrue(EvalConfig.isSetting("baseline"));
    assertTrue(EvalConfig.isSetting("calibration.trapPairs"));
    assertTrue(EvalConfig.isSetting("skipCalibration"));
    assertFalse(EvalConfig.isSetting("skip-calibration"));
    assertFalse(EvalConfig.isSetting("endpiont"));
    assertFalse(EvalConfig.isSetting("endpoint=http://x"));
  }

  @Test
  void judgePricingIsNullWhenNotSet() throws Exception {
    assertNull(EvalConfig.loadFile(write(""), null).judgePricing());
  }

  @Test
  void judgePricingReadsBothPrices() throws Exception {
    EvalConfig config =
        EvalConfig.loadFile(
            write("judgePricing:\n  inputPerMillion: 5\n  outputPerMillion: 25.5\n"), null);
    assertEquals(new UsageMeter.Pricing(5.0, 25.5), config.judgePricing());
  }

  @Test
  void judgePricingCanBeOverriddenOnTheCommandLineWithDots() throws Exception {
    EvalConfig config =
        EvalConfig.loadFile(
            write("judgePricing:\n  inputPerMillion: 5\n  outputPerMillion: 25\n"),
            java.util.Map.of("judgePricing.outputPerMillion", "10"));
    assertEquals(new UsageMeter.Pricing(5.0, 10.0), config.judgePricing());
    assertTrue(EvalConfig.isSetting("judgePricing.inputPerMillion"));
  }

  @Test
  void aMalformedJudgePricingIsAConfigErrorNamingTheFile() throws Exception {
    for (String bad :
        new String[] {
          "judgePricing: 5\n",
          "judgePricing:\n  inputPerMillion: 5\n",
          "judgePricing:\n  inputPerMillion: -1\n  outputPerMillion: 25\n",
          "judgePricing:\n  inputPerMillion: cheap\n  outputPerMillion: 25\n",
          "judgePricing:\n  inputPerMillion: .inf\n  outputPerMillion: 25\n",
          "judgePricing:\n  inputPerMillion: 5\n  outputPerMillion: .nan\n"
        }) {
      EvalConfig config = EvalConfig.loadFile(write(bad), null);
      IllegalArgumentException error =
          assertThrows(IllegalArgumentException.class, config::judgePricing, bad);
      assertTrue(error.getMessage().contains("team.yaml"), error.getMessage());
      assertTrue(error.getMessage().contains("judgePricing"), error.getMessage());
    }
  }
}

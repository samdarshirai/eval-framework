package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CliArgsTest {
  @Test
  void noArgumentsMeansNoConfigFileAndNoOverrides() {
    CliArgs cli = CliArgs.parse(new String[] {});
    assertNull(cli.configFile());
    assertEquals(Map.of(), cli.overrides());
  }

  @Test
  void aSettingAndItsValueBecomeAnOverrideAndConfigPicksTheFile() {
    CliArgs cli =
        CliArgs.parse(
            new String[] {"--config", "team.yaml", "--baseline", "b.json", "--passFloor", "0.8"});
    assertEquals("team.yaml", cli.configFile());
    assertEquals(Map.of("baseline", "b.json", "passFloor", "0.8"), cli.overrides());
  }

  @Test
  void aNestedSettingIsWrittenWithDotsAndAnEmptyValueIsAllowed() {
    CliArgs cli =
        CliArgs.parse(new String[] {"--calibration.trapPairs", "t.yaml", "--baseline", ""});
    assertEquals(Map.of("calibration.trapPairs", "t.yaml", "baseline", ""), cli.overrides());
  }

  @Test
  void skipCalibrationIsAShorthandForTheSettingSetToTrue() {
    assertEquals(
        Map.of("skipCalibration", "true"),
        CliArgs.parse(new String[] {"--skip-calibration"}).overrides());
  }

  @Test
  void caseIdsBecomeAnOverride() {
    assertEquals(Map.of("case", "a,b"), CliArgs.parse(new String[] {"--case", "a,b"}).overrides());
  }

  @Test
  void debugIsABareFlagOrTakesAnExplicitValue() {
    assertEquals(Map.of("debug", "true"), CliArgs.parse(new String[] {"--debug"}).overrides());
    assertEquals(
        Map.of("debug", "true", "baseline", "b.json"),
        CliArgs.parse(new String[] {"--debug", "--baseline", "b.json"}).overrides());
    assertEquals(
        Map.of("debug", "false"), CliArgs.parse(new String[] {"--debug", "false"}).overrides());
  }

  @Test
  void theLastValueOfARepeatedSettingWins() {
    assertEquals(
        Map.of("baseline", "second.json"),
        CliArgs.parse(new String[] {"--baseline", "first.json", "--baseline", "second.json"})
            .overrides());
  }

  @Test
  void everyErrorNamesTheProblemAndCarriesTheUsage() {
    for (String[] bad :
        new String[][] {
          {"--endpiont", "x"}, // unknown setting
          {"stray"}, // not a --setting
          {"--endpoint=http://x"}, // = form
          {"--endpoint"}, // no value
          {"--endpoint", "--baseline", "b"}, // value looks like a setting
          {"--config"}
        }) {
      var failure = assertThrows(IllegalArgumentException.class, () -> CliArgs.parse(bad));
      assertTrue(failure.getMessage().contains("Usage"), String.join(" ", bad));
    }
    assertTrue(
        assertThrows(
                IllegalArgumentException.class,
                () -> CliArgs.parse(new String[] {"--endpiont", "x"}))
            .getMessage()
            .contains("unknown setting '--endpiont'"));
    assertTrue(
        assertThrows(
                IllegalArgumentException.class, () -> CliArgs.parse(new String[] {"--endpoint"}))
            .getMessage()
            .contains("--endpoint needs a value"));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> CliArgs.parse(new String[] {"stray"}))
            .getMessage()
            .contains("unknown argument 'stray'"));
  }

  @Test
  void checksBecomesAnOverride() {
    assertEquals(
        Map.of("checks", "Refusal,Coverage"),
        CliArgs.parse(new String[] {"--checks", "Refusal,Coverage"}).overrides());
  }
}

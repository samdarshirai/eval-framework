package eval;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BaselineTest {
  @TempDir Path dir;

  private Path write(String json) throws IOException {
    Path file = dir.resolve("baseline.json");
    Files.writeString(file, json);
    return file;
  }

  @Test
  void aCaseThatPassedInTheBaselineIsReportedAsPassed() throws Exception {
    Baseline baseline =
        Baseline.load(
            write("{\"cases\":[{\"id\":\"a\",\"passed\":true},{\"id\":\"b\",\"passed\":false}]}"));
    assertTrue(baseline.passed("a"));
    assertFalse(baseline.passed("b"));
  }

  @Test
  void aCaseThatIsNotInTheBaselineIsNotPassed() throws Exception {
    Baseline baseline = Baseline.load(write("{\"cases\":[{\"id\":\"a\",\"passed\":true}]}"));
    assertFalse(baseline.passed("new-case"));
  }

  @Test
  void otherReportFieldsAreIgnoredSoAnyPastReportLoads() throws Exception {
    Baseline baseline =
        Baseline.load(
            write(
                "{\"runId\":\"r\",\"passRate\":1.0,\"calibration\":{\"ran\":false},"
                    + "\"cases\":[{\"id\":\"a\",\"passed\":true,\"checks\":[],\"actual\":null}]}"));
    assertTrue(baseline.passed("a"));
    assertEquals("baseline.json", baseline.name());
  }

  @Test
  void aMissingFileNamesTheFile() {
    var failure =
        assertThrows(IllegalArgumentException.class, () -> Baseline.load(dir.resolve("nope.json")));
    assertTrue(failure.getMessage().contains("nope.json"), failure.getMessage());
  }

  @Test
  void invalidJsonAnEmptyFileAnEmptyCasesListAndAMissingCasesListAreErrorsNotAnEmptyBaseline()
      throws Exception {
    for (String body :
        new String[] {"{not json", "", "{}", "{\"cases\":\"x\"}", "[]", "{\"cases\":[]}"}) {
      var failure = assertThrows(IllegalArgumentException.class, () -> Baseline.load(write(body)));
      assertTrue(
          failure.getMessage().contains("baseline.json"), body + " -> " + failure.getMessage());
    }
  }

  @Test
  void aCaseWithoutATextIdOrABooleanPassedIsAnError() throws Exception {
    for (String body :
        new String[] {
          "{\"cases\":[{\"passed\":true}]}",
          "{\"cases\":[{\"id\":\"a\"}]}",
          "{\"cases\":[{\"id\":\"a\",\"passed\":\"yes\"}]}"
        }) {
      var failure = assertThrows(IllegalArgumentException.class, () -> Baseline.load(write(body)));
      assertTrue(
          failure.getMessage().contains("baseline.json"), body + " -> " + failure.getMessage());
    }
  }

  @Test
  void knowsSaysWhetherTheCaseIsInTheBaselineWhetherOrNotItPassed() throws Exception {
    Baseline baseline =
        Baseline.load(
            write("{\"cases\":[{\"id\":\"a\",\"passed\":true},{\"id\":\"b\",\"passed\":false}]}"));
    assertTrue(baseline.knows("a"));
    assertTrue(baseline.knows("b"));
    assertFalse(baseline.knows("c"));
  }

  @Test
  void aDuplicateIdIsAnErrorNamingTheIdEvenWhenBothEntriesAgree() throws Exception {
    for (String body :
        new String[] {
          "{\"cases\":[{\"id\":\"a\",\"passed\":true},{\"id\":\"a\",\"passed\":false}]}",
          "{\"cases\":[{\"id\":\"a\",\"passed\":true},{\"id\":\"a\",\"passed\":true}]}"
        }) {
      var failure = assertThrows(IllegalArgumentException.class, () -> Baseline.load(write(body)));
      assertTrue(
          failure.getMessage().contains("baseline.json") && failure.getMessage().contains("'a'"),
          body + " -> " + failure.getMessage());
    }
  }
}

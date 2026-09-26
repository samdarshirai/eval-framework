package eval;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The command line: {@code --config <file>} picks the config file, and every other {@code
 * --<setting> <value>} overrides that config key for this one run (D47). Parsing only; the values
 * are applied by {@link EvalConfig}.
 */
record CliArgs(String configFile, Map<String, String> overrides) {
  private static final String USAGE =
      """
      Usage: Harness [--config <file>] [--<setting> <value>]... [--skip-calibration] [--debug]
      Settings (same names as in the config file; a value here beats the file):
        endpoint passFloor judgeModel categories cases case appType addChecks outputDir skipCalibration baseline debug
        calibration.trapPairs calibration.labeledSample knowledgeBase.type ...\
      """;

  /** Throws IllegalArgumentException, message ending with the usage, for anything malformed. */
  static CliArgs parse(String[] args) {
    String configFile = null;
    Map<String, String> overrides = new LinkedHashMap<>();
    for (int index = 0; index < args.length; index++) {
      String arg = args[index];
      if (arg.equals("--skip-calibration")) { // shorthand for --skipCalibration true
        overrides.put("skipCalibration", "true");
        continue;
      }
      boolean bare = index + 1 >= args.length || args[index + 1].startsWith("--");
      if (arg.equals("--debug") && bare) { // shorthand for --debug true
        overrides.put("debug", "true");
        continue;
      }
      String key = arg.startsWith("--") ? arg.substring(2) : "";
      if (!key.equals("config") && !EvalConfig.isSetting(key)) {
        throw usageError(
            arg.startsWith("--")
                ? "unknown setting '" + arg + "'"
                : "unknown argument '" + arg + "'");
      }
      if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
        throw usageError(arg + " needs a value");
      }
      String value = args[++index];
      if (key.equals("config")) {
        configFile = value;
      } else {
        overrides.put(key, value);
      }
    }
    return new CliArgs(configFile, overrides);
  }

  private static IllegalArgumentException usageError(String problem) {
    return new IllegalArgumentException(problem + "\n" + USAGE);
  }
}

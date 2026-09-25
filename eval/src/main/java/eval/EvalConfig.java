package eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * The settings from the config file ({@code eval/config.yaml} by default). Loading reads endpoint,
 * passFloor and categories (in that order) and validates categories; the judge model is checked
 * only when {@link #requireJudgeModel()} is called, so callers control when that failure surfaces.
 */
final class EvalConfig {
  private final Map<String, Object> raw;
  private final Path baseDir;
  private final boolean defaultLayout;
  private final String fileName;
  private final String endpoint;
  private final double passFloor;
  private final List<String> categories;

  private EvalConfig(
      Map<String, Object> raw,
      Path baseDir,
      boolean defaultLayout,
      String fileName,
      String endpoint,
      double passFloor,
      List<String> categories) {
    this.raw = raw;
    this.baseDir = baseDir;
    this.defaultLayout = defaultLayout;
    this.fileName = fileName;
    this.endpoint = endpoint;
    this.passFloor = passFloor;
    this.categories = categories;
  }

  /** Loads {@code <root>/eval/config.yaml}; paths resolve against root. */
  static EvalConfig load(Path root, String endpointOverride) throws IOException {
    return read(root.resolve("eval/config.yaml"), root, true, "eval/config.yaml", endpointOverride);
  }

  /**
   * Loads an explicit config file; every relative path in it resolves against the file's own
   * directory.
   */
  static EvalConfig loadFile(Path configFile, String endpointOverride) throws IOException {
    Path parent = configFile.getParent();
    Path baseDir = parent != null ? parent : Path.of(".");
    return read(configFile, baseDir, false, configFile.toString(), endpointOverride);
  }

  private static EvalConfig read(
      Path file, Path baseDir, boolean defaultLayout, String fileName, String endpointOverride)
      throws IOException {
    Map<String, Object> raw = new Yaml().load(Files.readString(file));
    String endpoint = endpointOverride != null ? endpointOverride : (String) raw.get("endpoint");
    double passFloor = ((Number) raw.get("passFloor")).doubleValue();
    return new EvalConfig(
        raw, baseDir, defaultLayout, fileName, endpoint, passFloor, categoriesFrom(raw, fileName));
  }

  /** The directory relative paths resolve against. */
  Path baseDir() {
    return baseDir;
  }

  /** The file name used in error messages. */
  String fileName() {
    return fileName;
  }

  /** The {@code cases:} directory; default {@code eval/cases} (default layout) or {@code cases}. */
  Path casesDir() {
    return baseDir.resolve(text("cases", defaultLayout ? "eval/cases" : "cases"));
  }

  /** The {@code outputDir:} directory; default {@code caseResults}. */
  Path outputDir() {
    return baseDir.resolve(text("outputDir", "caseResults"));
  }

  /** The {@code calibration.trapPairs} file, or null when the bundled trap pairs should be used. */
  Path trapPairsFile() {
    return calibrationFile("trapPairs");
  }

  /** The {@code calibration.labeledSample} file, or null when the bundled sample should be used. */
  Path labeledSampleFile() {
    return calibrationFile("labeledSample");
  }

  /** The {@code skipCalibration:} flag; default false. {@code --skip-calibration} overrides it. */
  boolean skipCalibration() {
    Object value = raw.get("skipCalibration");
    if (value == null) {
      return false;
    }
    if (!(value instanceof Boolean skip)) {
      throw new IllegalArgumentException(fileName + ": 'skipCalibration' must be true or false");
    }
    return skip;
  }

  /**
   * The {@code baseline:} report file, or null when none is set. {@code --baseline} overrides it.
   */
  Path baselineFile() {
    String value = text("baseline", null);
    return value == null ? null : baseDir.resolve(value);
  }

  private Path calibrationFile(String key) {
    Object block = raw.get("calibration");
    if (block == null) {
      return null;
    }
    if (!(block instanceof Map<?, ?> calibration)) {
      throw new IllegalArgumentException(fileName + ": 'calibration' must be a map");
    }
    Object value = calibration.get(key);
    return value == null || value.toString().isBlank() ? null : baseDir.resolve(value.toString());
  }

  private String text(String key, String defaultValue) {
    Object value = raw.get(key);
    return value == null || value.toString().isBlank() ? defaultValue : value.toString();
  }

  String endpoint() {
    return endpoint;
  }

  double passFloor() {
    return passFloor;
  }

  List<String> categories() {
    return categories;
  }

  /** The whole config map, for KnowledgeSources.from(cfg, root). */
  Map<String, Object> raw() {
    return raw;
  }

  String requireJudgeModel() {
    if (!(raw.get("judgeModel") instanceof String judgeModel) || judgeModel.isBlank()) {
      throw new IllegalArgumentException(
          fileName + ": 'judgeModel' is required (an OpenRouter model slug)");
    }
    return judgeModel;
  }

  /**
   * The allowed case categories from config; 'out-of-scope' must stay because the exit rule depends
   * on it.
   */
  private static List<String> categoriesFrom(Map<String, Object> cfg, String fileName) {
    if (!(cfg.get("categories") instanceof List<?> rawCategories) || rawCategories.isEmpty()) {
      throw new IllegalArgumentException(fileName + ": 'categories' must be a non-empty list");
    }
    List<String> categories = rawCategories.stream().map(String::valueOf).toList();
    if (!categories.contains(SuiteReport.OUT_OF_SCOPE)) {
      throw new IllegalArgumentException(
          fileName
              + ": 'categories' must include '"
              + SuiteReport.OUT_OF_SCOPE
              + "' (the out-of-scope exit rule depends on it)");
    }
    return categories;
  }
}

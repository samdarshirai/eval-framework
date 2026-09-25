package eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /**
   * The top-level keys of the config file. Each can be overridden for one run with {@code --<key>
   * <value>}; a nested key is written with dots ({@code --calibration.trapPairs file}).
   */
  private static final Set<String> SETTINGS =
      Set.of(
          "endpoint",
          "passFloor",
          "judgeModel",
          "judgePricing",
          "categories",
          "cases",
          "outputDir",
          "skipCalibration",
          "debug",
          "case",
          "baseline",
          "calibration",
          "knowledgeBase");

  /** True when {@code key} (dots allowed) starts with one of the config file's top-level keys. */
  static boolean isSetting(String key) {
    return SETTINGS.contains(key.split("\\.", -1)[0]) && !key.startsWith(".") && !key.endsWith(".");
  }

  /**
   * Loads {@code <root>/eval/config.yaml}; paths resolve against root. Each override replaces the
   * config file's value for that key, typed as YAML would type it ({@code true}, {@code 0.8}, a
   * path, a {@code [list]}).
   */
  static EvalConfig load(Path root, Map<String, String> overrides) throws IOException {
    return read(root.resolve("eval/config.yaml"), root, true, "eval/config.yaml", overrides);
  }

  /** The config file the command line names, or the default one; then the overrides on top. */
  static EvalConfig from(Path root, CliArgs cli) throws IOException {
    if (cli.configFile() == null) {
      return load(root, cli.overrides());
    }
    Path configFile = root.resolve(cli.configFile());
    if (!Files.isRegularFile(configFile)) {
      throw new IllegalArgumentException("config file not found: " + cli.configFile());
    }
    return loadFile(configFile, cli.overrides());
  }

  /**
   * Loads an explicit config file; every relative path in it resolves against the file's own
   * directory.
   */
  static EvalConfig loadFile(Path configFile, Map<String, String> overrides) throws IOException {
    Path parent = configFile.getParent();
    Path baseDir = parent != null ? parent : Path.of(".");
    return read(configFile, baseDir, false, configFile.toString(), overrides);
  }

  private static EvalConfig read(
      Path file,
      Path baseDir,
      boolean defaultLayout,
      String fileName,
      Map<String, String> overrides)
      throws IOException {
    Map<String, Object> raw = new Yaml().load(Files.readString(file));
    if (overrides != null) {
      overrides.forEach((key, value) -> override(raw, key, value));
    }
    Object endpointValue = raw.get("endpoint");
    String endpoint = endpointValue == null ? null : endpointValue.toString();
    double passFloor = ((Number) raw.get("passFloor")).doubleValue();
    return new EvalConfig(
        raw, baseDir, defaultLayout, fileName, endpoint, passFloor, categoriesFrom(raw, fileName));
  }

  /** Sets {@code key} (dots nest) to {@code value}, parsed as YAML; creates missing maps. */
  @SuppressWarnings("unchecked")
  private static void override(Map<String, Object> raw, String key, String value) {
    String[] path = key.split("\\.");
    Map<String, Object> target = raw;
    for (int index = 0; index < path.length - 1; index++) {
      Object child = target.get(path[index]);
      if (child == null) {
        child = new LinkedHashMap<String, Object>();
        target.put(path[index], child);
      }
      if (!(child instanceof Map<?, ?>)) {
        throw new IllegalArgumentException(
            "cannot set '" + key + "': '" + path[index] + "' is not a map in the config");
      }
      target = (Map<String, Object>) child;
    }
    target.put(path[path.length - 1], new Yaml().load(value));
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

  /** The {@code debug:} flag; default false. {@code --debug} turns it on for one run. */
  boolean debug() {
    Object value = raw.get("debug");
    if (value == null) {
      return false;
    }
    if (!(value instanceof Boolean debug)) {
      throw new IllegalArgumentException(fileName + ": 'debug' must be true or false");
    }
    return debug;
  }

  /**
   * The {@code case:} ids to run, comma-separated ({@code --case a,b}); empty means every case.
   */
  List<String> caseIds() {
    Object value = raw.get("case");
    if (value == null) {
      return List.of();
    }
    List<?> parts = value instanceof List<?> list ? list : List.of(value.toString().split(","));
    return parts.stream().map(part -> part.toString().trim()).filter(id -> !id.isEmpty()).toList();
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
   * The {@code judgePricing:} block (USD per million tokens), or null when it is not set. A block
   * that is set must be complete, so a typo cannot silently turn the estimate into zero.
   */
  UsageMeter.Pricing judgePricing() {
    Object block = raw.get("judgePricing");
    if (block == null) {
      return null;
    }
    if (block instanceof Map<?, ?> pricing
        && pricing.get("inputPerMillion") instanceof Number input
        && pricing.get("outputPerMillion") instanceof Number output
        && Double.isFinite(input.doubleValue())
        && Double.isFinite(output.doubleValue())
        && input.doubleValue() >= 0
        && output.doubleValue() >= 0) {
      return new UsageMeter.Pricing(input.doubleValue(), output.doubleValue());
    }
    throw new IllegalArgumentException(
        fileName
            + ": 'judgePricing' needs inputPerMillion and outputPerMillion, each a number of at"
            + " least 0 (USD per million tokens)");
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

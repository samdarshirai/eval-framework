package eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * The settings from {@code eval/config.yaml}. Loading reads endpoint, passFloor and categories (in
 * that order) and validates categories; the judge model is checked only when {@link
 * #requireJudgeModel()} is called, so callers control when that failure surfaces.
 */
final class EvalConfig {
  private final Map<String, Object> raw;
  private final String endpoint;
  private final double passFloor;
  private final List<String> categories;

  private EvalConfig(
      Map<String, Object> raw, String endpoint, double passFloor, List<String> categories) {
    this.raw = raw;
    this.endpoint = endpoint;
    this.passFloor = passFloor;
    this.categories = categories;
  }

  /**
   * Loads eval/config.yaml under root; a non-null endpointOverride wins over the file's endpoint.
   */
  static EvalConfig load(Path root, String endpointOverride) throws IOException {
    Map<String, Object> raw = new Yaml().load(Files.readString(root.resolve("eval/config.yaml")));
    String endpoint = endpointOverride != null ? endpointOverride : (String) raw.get("endpoint");
    double passFloor = ((Number) raw.get("passFloor")).doubleValue();
    return new EvalConfig(raw, endpoint, passFloor, categoriesFrom(raw));
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
          "eval/config.yaml: 'judgeModel' is required (an OpenRouter model slug)");
    }
    return judgeModel;
  }

  /**
   * The allowed case categories from config; 'out-of-scope' must stay because the exit rule depends
   * on it.
   */
  private static List<String> categoriesFrom(Map<String, Object> cfg) {
    if (!(cfg.get("categories") instanceof List<?> rawCategories) || rawCategories.isEmpty()) {
      throw new IllegalArgumentException("eval/config.yaml: 'categories' must be a non-empty list");
    }
    List<String> categories = rawCategories.stream().map(String::valueOf).toList();
    if (!categories.contains(SuiteReport.OUT_OF_SCOPE)) {
      throw new IllegalArgumentException(
          "eval/config.yaml: 'categories' must include '"
              + SuiteReport.OUT_OF_SCOPE
              + "' (the out-of-scope exit rule depends on it)");
    }
    return categories;
  }
}

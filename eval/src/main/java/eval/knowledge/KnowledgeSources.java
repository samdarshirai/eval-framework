package eval.knowledge;

import java.nio.file.Path;
import java.util.Map;

/** Builds the configured KnowledgeSource. Adding a source is one class plus one case in the switch below. */
public final class KnowledgeSources {
    /**
     * @param cfg  the parsed eval/config.yaml; its optional {@code knowledgeBase} block is {@code {type, path|url}}.
     *             Absent means the default: {@code docs} chunked from {@code <root>/docs}.
     * @param root the repo root that relative paths resolve against
     */
    public static KnowledgeSource from(Map<String, Object> cfg, Path root) {
        return from(cfg, root, "eval/config.yaml");
    }

    /** As above; {@code configName} prefixes error messages. */
    public static KnowledgeSource from(Map<String, Object> cfg, Path root, String configName) {
        Object block = cfg.get("knowledgeBase");
        if (block != null && !(block instanceof Map<?, ?>))
            throw new IllegalArgumentException(configName + ": 'knowledgeBase' must be a map with a 'type'");
        Map<?, ?> kbConfig = block == null ? Map.of() : (Map<?, ?>) block;
        String type = kbConfig.get("type") == null ? "docs" : kbConfig.get("type").toString();
        return switch (type) {
            case "docs" -> new DocsDirSource(root.resolve(text(kbConfig, "path", "docs", configName)));
            case "http" -> new HttpSource(text(kbConfig, "url", null, configName));
            case "manifest" -> new ManifestSource(root.resolve(text(kbConfig, "path", null, configName)));
            default -> throw new IllegalArgumentException(
                configName + ": unknown knowledgeBase type '" + type + "', expected one of: docs, http, manifest");
        };
    }

    private static String text(Map<?, ?> kbConfig, String key, String defaultValue, String configName) {
        Object value = kbConfig.get(key);
        if (value != null && !value.toString().isBlank()) return value.toString();
        if (defaultValue != null) return defaultValue;
        throw new IllegalArgumentException(configName + ": knowledgeBase needs '" + key + "' for type '" + kbConfig.get("type") + "'");
    }
}

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
        Object block = cfg.get("knowledgeBase");
        if (block != null && !(block instanceof Map<?, ?>))
            throw new IllegalArgumentException("eval/config.yaml: 'knowledgeBase' must be a map with a 'type'");
        Map<?, ?> kbConfig = block == null ? Map.of() : (Map<?, ?>) block;
        String type = kbConfig.get("type") == null ? "docs" : kbConfig.get("type").toString();
        return switch (type) {
            case "docs" -> new DocsDirSource(root.resolve(text(kbConfig, "path", "docs")));
            case "http" -> new HttpSource(text(kbConfig, "url", null));
            case "manifest" -> new ManifestSource(root.resolve(text(kbConfig, "path", null)));
            default -> throw new IllegalArgumentException(
                "eval/config.yaml: unknown knowledgeBase type '" + type + "', expected one of: docs, http, manifest");
        };
    }

    private static String text(Map<?, ?> kbConfig, String key, String defaultValue) {
        Object value = kbConfig.get(key);
        if (value != null && !value.toString().isBlank()) return value.toString();
        if (defaultValue != null) return defaultValue;
        throw new IllegalArgumentException("eval/config.yaml: knowledgeBase needs '" + key + "' for type '" + kbConfig.get("type") + "'");
    }
}

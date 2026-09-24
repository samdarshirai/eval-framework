package eval;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import java.io.IOException;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.util.*;

public final class EvalCaseLoader {
    // Single source of truth; SuiteReport's never-cut rule matches "out-of-scope" exactly.
    private static final List<String> CATEGORIES = List.of("single-source", "multi-source", "false-premise", "out-of-scope", "edge-case");

    public static List<EvalCase> load(Path dir, KnowledgeBase kb) throws IOException {
        List<Path> files;
        try (var s = Files.list(dir)) { files = s.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml")).sorted().toList(); }
        List<EvalCase> cases = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (Path f : files) {
            Object root;
            try { root = new Yaml().load(Files.readString(f)); }
            catch (YAMLException e) { throw new IllegalArgumentException(f.getFileName() + ": invalid YAML: " + e.getMessage().lines().findFirst().orElse("")); }
            if (!(root instanceof List<?> list)) throw new IllegalArgumentException(f.getFileName() + ": expected a list of cases");
            for (Object o : list) {
                EvalCase c = parse(f.getFileName().toString(), o, kb);
                if (!ids.add(c.id())) throw new IllegalArgumentException(f.getFileName() + ": duplicate case id '" + c.id() + "'");
                cases.add(c);
            }
        }
        if (cases.isEmpty()) throw new IllegalArgumentException("no cases found in " + dir);
        return cases;
    }

    private static EvalCase parse(String file, Object o, KnowledgeBase kb) {
        if (!(o instanceof Map<?, ?> m)) throw new IllegalArgumentException(file + ": each case must be a map");
        String id = str(m, "id", file, "?", true);
        String where = file + " case '" + id + "'";
        String behavior = str(m, "expected_behavior", file, id, true);
        if (!behavior.equals("answer") && !behavior.equals("refuse"))
            throw new IllegalArgumentException(where + ": expected_behavior must be 'answer' or 'refuse', got '" + behavior + "'");
        List<ExpectedFact> facts = new ArrayList<>();
        Object factsRaw = m.get("facts");
        if (factsRaw != null && !(factsRaw instanceof List<?>))
            throw new IllegalArgumentException(where + ": 'facts' must be a list");
        if (factsRaw instanceof List<?> fl) {
            for (Object fo : fl) {
                if (!(fo instanceof Map<?, ?> fm))
                    throw new IllegalArgumentException(where + ": each fact must be a map with 'fact' and 'chunks'");
                List<String> chunks = strList(fm.get("chunks"), "chunks", where);
                if (chunks.isEmpty()) throw new IllegalArgumentException(where + ": fact needs at least one gold chunk");
                for (String ch : chunks)
                    if (!kb.has(ch)) throw new IllegalArgumentException(where + ": gold chunk '" + ch + "' does not exist in the knowledge base");
                facts.add(new ExpectedFact(str(fm, "fact", file, id, true), chunks, strList(fm.get("keywords"), "keywords", where)));
            }
        }
        if (behavior.equals("answer") && facts.isEmpty())
            throw new IllegalArgumentException(where + ": an 'answer' case needs at least one expected fact");
        String category = str(m, "category", file, id, true);
        if (!CATEGORIES.contains(category))
            throw new IllegalArgumentException(where + ": category '" + category + "' is not one of: " + String.join(", ", CATEGORIES));
        Object added = m.get("added");
        String addedStr = added instanceof Date d ? d.toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString() : str(m, "added", file, id, true);
        return new EvalCase(id, str(m, "question", file, id, true), category,
            str(m, "subtype", file, id, false), behavior, facts,
            str(m, "source", file, id, true), str(m, "owner", file, id, true), addedStr);
    }

    private static String str(Map<?, ?> m, String key, String file, String id, boolean required) {
        Object v = m.get(key);
        if (v == null || v.toString().isBlank()) {
            if (required) throw new IllegalArgumentException(file + " case '" + id + "': missing '" + key + "'");
            return null;
        }
        return v.toString();
    }

    private static List<String> strList(Object o, String field, String where) {
        if (o == null) return List.of();
        if (!(o instanceof List<?> l)) throw new IllegalArgumentException(where + ": '" + field + "' must be a list");
        return l.stream().map(String::valueOf).toList();
    }
}

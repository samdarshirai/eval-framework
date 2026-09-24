package eval;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;
import java.io.IOException;
import java.nio.file.*;
import java.time.ZoneOffset;
import java.util.*;

public final class EvalCaseLoader {
    /**
     * Loads every case file in {@code casesDir} and validates all of it before the harness contacts the assistant.
     * Any problem throws an IllegalArgumentException naming the file, case and field.
     *
     * @param allowedCategories the categories this app accepts (eval/config.yaml), so a service can add or remove its own
     */
    public static List<EvalCase> load(Path casesDir, KnowledgeBase kb, List<String> allowedCategories) throws IOException {
        List<Path> caseFiles;
        try (var fileStream = Files.list(casesDir)) {
            caseFiles = fileStream.filter(p -> p.toString().endsWith(".yaml") || p.toString().endsWith(".yml")).sorted().toList();
        }
        List<EvalCase> cases = new ArrayList<>();
        Set<String> seenCaseIds = new HashSet<>();
        for (Path caseFile : caseFiles) {
            String fileName = caseFile.getFileName().toString();
            for (Object entry : readCaseEntries(caseFile)) {
                EvalCase evalCase = parseCase(fileName, entry, kb, allowedCategories);
                if (!seenCaseIds.add(evalCase.id())) throw new IllegalArgumentException(fileName + ": duplicate case id '" + evalCase.id() + "'");
                cases.add(evalCase);
            }
        }
        if (cases.isEmpty()) throw new IllegalArgumentException("no cases found in " + casesDir);
        return cases;
    }

    /** Reads one case file and returns its top-level list; the file must be valid YAML holding a list of cases. */
    private static List<?> readCaseEntries(Path caseFile) throws IOException {
        String fileName = caseFile.getFileName().toString();
        Object parsedYaml;
        try {
            parsedYaml = new Yaml().load(Files.readString(caseFile));
        } catch (YAMLException e) {
            throw new IllegalArgumentException(fileName + ": invalid YAML: " + e.getMessage().lines().findFirst().orElse(""));
        }
        if (!(parsedYaml instanceof List<?> entries)) throw new IllegalArgumentException(fileName + ": expected a list of cases");
        return entries;
    }

    /**
     * Turns one YAML entry into an EvalCase. SnakeYAML gives untyped maps and lists, so every field is
     * type-checked here by hand. Order of checks:
     * id, expected_behavior, facts (with gold-chunk lookup), the answer-needs-facts rule, category, then metadata.
     */
    private static EvalCase parseCase(String file, Object entry, KnowledgeBase kb, List<String> allowedCategories) {
        // A case must be a YAML map; a bare string or list in the file is a mistake.
        if (!(entry instanceof Map<?, ?> caseMap)) throw new IllegalArgumentException(file + ": each case must be a map");
        // The id comes first so every later error can name the case ("file case 'id'").
        String id = str(caseMap, "id", file, "?", true);
        String where = file + " case '" + id + "'";

        // expected_behavior is set per case (D2) and only has two legal values.
        String expectedBehavior = str(caseMap, "expected_behavior", file, id, true);
        if (!expectedBehavior.equals("answer") && !expectedBehavior.equals("refuse"))
            throw new IllegalArgumentException(where + ": expected_behavior must be 'answer' or 'refuse', got '" + expectedBehavior + "'");

        // Facts are optional in the YAML (a 'refuse' case has none), but when present must be a list of maps.
        List<ExpectedFact> facts = new ArrayList<>();
        Object factsRaw = caseMap.get("facts");
        if (factsRaw != null && !(factsRaw instanceof List<?>))
            throw new IllegalArgumentException(where + ": 'facts' must be a list");
        if (factsRaw instanceof List<?> factEntries) {
            for (Object factEntry : factEntries) {
                if (!(factEntry instanceof Map<?, ?> factMap))
                    throw new IllegalArgumentException(where + ": each fact must be a map with 'fact' and 'chunks'");
                // Gold chunks are any-of alternatives (D5): at least one, and each must exist in the docs (D6).
                // Checking here, before any assistant call, is what makes a stale chunk ID fail loudly and early.
                List<String> goldChunkIds = strList(factMap.get("chunks"), "chunks", where);
                if (goldChunkIds.isEmpty()) throw new IllegalArgumentException(where + ": fact needs at least one gold chunk");
                for (String goldChunkId : goldChunkIds)
                    if (!kb.has(goldChunkId)) throw new IllegalArgumentException(where + ": gold chunk '" + goldChunkId + "' does not exist in the knowledge base");
                facts.add(new ExpectedFact(str(factMap, "fact", file, id, true), goldChunkIds, strList(factMap.get("keywords"), "keywords", where)));
            }
        }
        // An 'answer' case with no expected facts could never fail Coverage, so it is rejected.
        if (expectedBehavior.equals("answer") && facts.isEmpty())
            throw new IllegalArgumentException(where + ": an 'answer' case needs at least one expected fact");

        // A typo'd category would silently drop the case out of its rules (e.g. the out-of-scope exit rule), so reject it.
        String category = str(caseMap, "category", file, id, true);
        if (!allowedCategories.contains(category))
            throw new IllegalArgumentException(where + ": category '" + category + "' is not one of: " + String.join(", ", allowedCategories));

        // Unquoted YAML dates (added: 2026-09-24) arrive as java.util.Date; normalise them to ISO text.
        Object addedRaw = caseMap.get("added");
        String added = addedRaw instanceof Date addedDate
            ? addedDate.toInstant().atZone(ZoneOffset.UTC).toLocalDate().toString()
            : str(caseMap, "added", file, id, true);
        return new EvalCase(id, str(caseMap, "question", file, id, true), category,
            str(caseMap, "subtype", file, id, false), expectedBehavior, facts,
            str(caseMap, "source", file, id, true), str(caseMap, "owner", file, id, true), added);
    }

    /** Reads a scalar field as text; a missing or blank required field is an error, an optional one is null. */
    private static String str(Map<?, ?> map, String key, String file, String id, boolean required) {
        Object value = map.get(key);
        if (value == null || value.toString().isBlank()) {
            if (required) throw new IllegalArgumentException(file + " case '" + id + "': missing '" + key + "'");
            return null;
        }
        return value.toString();
    }

    /** Reads an optional list field as a list of text; absent means empty, a non-list is an error. */
    private static List<String> strList(Object value, String field, String where) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> items)) throw new IllegalArgumentException(where + ": '" + field + "' must be a list");
        return items.stream().map(String::valueOf).toList();
    }
}

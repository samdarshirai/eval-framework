package kb;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class Chunker {
    private static final Pattern HEADING = Pattern.compile("^(#{1,3})\\s+(.+?)\\s*$");

    private record Section(String heading, StringBuilder body) {}

    public static List<Chunk> chunkDir(Path dir) throws IOException {
        List<Chunk> out = new ArrayList<>();
        List<Path> files;
        try (var s = Files.list(dir)) {
            files = s.filter(p -> p.toString().endsWith(".md")).sorted().toList();
        }
        for (Path p : files) {
            String doc = p.getFileName().toString().replaceFirst("\\.md$", "");
            out.addAll(chunk(doc, Files.readString(p)));
        }
        return out;
    }

    public static List<Chunk> chunk(String doc, String markdown) {
        List<Section> sections = new ArrayList<>();
        boolean inFence = false;
        for (String line : stripFrontmatter(markdown).split("\n", -1)) {
            if (line.stripLeading().startsWith("```")) inFence = !inFence;
            Matcher m = inFence ? null : HEADING.matcher(line);
            if (m != null && m.matches()) {
                sections.add(new Section(m.group(2), new StringBuilder()));
            } else if (!sections.isEmpty()) {
                sections.get(sections.size() - 1).body().append(line).append('\n');
            }
        }
        Map<String, Integer> total = new HashMap<>();
        for (Section s : sections) total.merge(slug(s.heading()), 1, Integer::sum);
        Map<String, Integer> seen = new HashMap<>();
        List<Chunk> out = new ArrayList<>();
        for (Section s : sections) {
            String slug = slug(s.heading());
            int n = seen.merge(slug, 1, Integer::sum);
            if (s.body().toString().isBlank()) continue;
            String id = doc + "#" + slug + (total.get(slug) > 1 ? "-" + n : "");
            out.add(new Chunk(id, doc, s.heading() + "\n" + s.body().toString().strip()));
        }
        return out;
    }

    static String slug(String heading) {
        String s = heading.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isEmpty() ? "section" : s;
    }

    private static String stripFrontmatter(String md) {
        if (!md.startsWith("---\n")) return md;
        int end = md.indexOf("\n---\n", 3);
        return end < 0 ? md : md.substring(end + 5);
    }

    public static void main(String[] args) throws IOException {
        chunkDir(Path.of("docs")).forEach(c -> System.out.println(c.id()));
    }
}

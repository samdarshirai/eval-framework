package assistant;

import java.util.*;

public final class BM25Index {
    private static final double K1 = 1.5, B = 0.75;
    private final List<Chunk> chunks;
    private final List<Map<String, Integer>> tf = new ArrayList<>();
    private final Map<String, Integer> df = new HashMap<>();
    private final int[] len;
    private final double avgLen;

    public BM25Index(List<Chunk> chunks) {
        this.chunks = chunks;
        this.len = new int[chunks.size()];
        int total = 0;
        for (int i = 0; i < chunks.size(); i++) {
            List<String> toks = tokenize(chunks.get(i).text());
            len[i] = toks.size();
            total += toks.size();
            Map<String, Integer> counts = new HashMap<>();
            toks.forEach(t -> counts.merge(t, 1, Integer::sum));
            counts.keySet().forEach(t -> df.merge(t, 1, Integer::sum));
            tf.add(counts);
        }
        this.avgLen = chunks.isEmpty() ? 0 : (double) total / chunks.size();
    }

    public List<Chunk> search(String query, int k) {
        List<String> q = tokenize(query);
        double[] score = new double[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) {
            for (String t : q) {
                int f = tf.get(i).getOrDefault(t, 0);
                if (f == 0) continue;
                double idf = Math.log(1 + (chunks.size() - df.get(t) + 0.5) / (df.get(t) + 0.5));
                score[i] += idf * f * (K1 + 1) / (f + K1 * (1 - B + B * len[i] / avgLen));
            }
        }
        return java.util.stream.IntStream.range(0, chunks.size()).boxed()
            .sorted(Comparator.<Integer>comparingDouble(i -> -score[i]).thenComparingInt(i -> i))
            .limit(k).map(chunks::get).toList();
    }

    static List<String> tokenize(String s) {
        return Arrays.stream(s.toLowerCase().split("[^a-z0-9]+")).filter(t -> !t.isEmpty()).toList();
    }
}

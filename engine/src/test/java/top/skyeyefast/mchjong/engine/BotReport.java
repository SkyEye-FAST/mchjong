package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Paired experiment statistics: a seed, including all seat rotations, is one sampling unit. */
final class BotReport {
    private BotReport() {}
    record Match(long seed, int rotation, String challenger, String field, String rules,
                 List<Integer> points, List<Integer> ranks, int hands) {}
    record Interval(double before, double after, double delta, double low, double high) {}
    record Comparison(int seeds, int matches, Interval points, Interval rank) {}

    static void print(String before, String after) {
        try {
            var result = compare(read(before), read(after));
            System.out.printf("paired seeds=%d matches_per_version=%d bootstrap=10000 unit=seed%n", result.seeds, result.matches);
            print("field_points", result.points);
            print("field_rank", result.rank);
        } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
    }

    private static void print(String label, Interval value) {
        System.out.printf(Locale.ROOT, "%s before=%.3f after=%.3f delta=%.3f paired_95%%_CI=[%.3f,%.3f]%n",
            label, value.before, value.after, value.delta, value.low, value.high);
    }

    private static List<Match> read(String prefix) throws IOException {
        var path = Path.of(prefix).toAbsolutePath();
        var result = new ArrayList<Match>();
        try (var paths = Files.list(path.getParent())) {
            for (var file : paths.filter(p -> p.getFileName().toString().startsWith(path.getFileName().toString())
                && p.toString().endsWith(".jsonl")).sorted().toList()) {
                try (var lines = Files.lines(file)) {
                    lines.filter(line -> !line.isBlank()).map(line -> new Gson().fromJson(line, Match.class)).forEach(result::add);
                }
            }
        }
        return result;
    }

    static Comparison compare(List<Match> before, List<Match> after) {
        var a = group(before);
        var b = group(after);
        if (!a.keySet().equals(b.keySet())) throw new IllegalArgumentException("Paired runs must contain identical seeds");
        double[][] points = new double[2][a.size()], ranks = new double[2][a.size()];
        int index = 0;
        for (long seed : a.keySet()) {
            for (int rotation : a.get(seed).keySet()) {
                var first = a.get(seed).get(rotation);
                var second = b.get(seed).get(rotation);
                if (second == null || !first.challenger.equals(second.challenger) || !first.field.equals(second.field)
                    || !first.rules.equals(second.rules) || first.points.size() != second.points.size())
                    throw new IllegalArgumentException("Mismatched opponent, rules or rotations");
                int players = first.points.size();
                for (int seat = 0; seat < players; seat++) if (seat != rotation) {
                    points[0][index] += first.points.get(seat) / (double) (players * (players - 1));
                    points[1][index] += second.points.get(seat) / (double) (players * (players - 1));
                    ranks[0][index] += first.ranks.get(seat) / (double) (players * (players - 1));
                    ranks[1][index] += second.ranks.get(seat) / (double) (players * (players - 1));
                }
            }
            index++;
        }
        return new Comparison(a.size(), before.size(), interval(points), interval(ranks));
    }

    private static Map<Long, Map<Integer, Match>> group(List<Match> matches) {
        var result = new TreeMap<Long, Map<Integer, Match>>();
        for (var match : matches) {
            int players = match.points.size();
            if (players < 3 || players > 4 || match.ranks.size() != players || match.rotation < 0 || match.rotation >= players)
                throw new IllegalArgumentException("Invalid match dimensions");
            var rotations = result.computeIfAbsent(match.seed, ignored -> new HashMap<>());
            if (rotations.put(match.rotation, match) != null) throw new IllegalArgumentException("Duplicate seed and rotation");
        }
        if (result.size() < 2) throw new IllegalArgumentException("Need at least two complete seeds");
        for (var rotations : result.values()) {
            int players = rotations.values().iterator().next().points.size();
            if (rotations.size() != players || rotations.values().stream().anyMatch(match -> match.points.size() != players))
                throw new IllegalArgumentException("Each seed requires every seat rotation");
        }
        return result;
    }

    private static Interval interval(double[][] samples) {
        int count = samples[0].length;
        double before = Arrays.stream(samples[0]).average().orElseThrow();
        double after = Arrays.stream(samples[1]).average().orElseThrow();
        var random = new Random(74291);
        double[] bootstrap = new double[10000];
        for (int i = 0; i < bootstrap.length; i++) for (int j = 0; j < count; j++) {
            int selected = random.nextInt(count);
            bootstrap[i] += (samples[1][selected] - samples[0][selected]) / count;
        }
        Arrays.sort(bootstrap);
        return new Interval(before, after, after - before, bootstrap[249], bootstrap[9749]);
    }
}

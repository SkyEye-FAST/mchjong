package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;

/** Opt-in review of completed built-in play. The teacher never selects an executed action. */
final class BotTeacherReview {
    private static final Gson JSON = new Gson();

    private BotTeacherReview() {}

    record QComparison(String stage, Double teacher, Double builtIn) {
        Double gap() { return teacher == null || builtIn == null ? null : teacher - builtIn; }
    }

    private record Sample(long seed, int step, MjaiProtocol.Position position, int actual, double priority) {}

    static void run(String[] args) {
        if (args.length != 7) throw new IllegalArgumentException(
            "teacher <preset.json> <difficulty> <seeds> <first-seed> <samples> <report.jsonl>");
        try {
            var preset = JSON.fromJson(Files.readString(Path.of(args[1])), BotPreset.class);
            var difficulty = BotDifficulty.valueOf(args[2]);
            int seeds = Integer.parseInt(args[3]);
            long firstSeed = Long.parseLong(args[4]);
            int limit = Integer.parseInt(args[5]);
            if (seeds < 1 || limit < 1) throw new IllegalArgumentException("Need positive seeds and sample count");
            var report = Path.of(args[6]).toAbsolutePath();
            Files.createDirectories(report.getParent());
            Files.writeString(report, "");
            var random = new Random(0);
            var samples = new ArrayList<Sample>();
            int eligible = 0;
            for (int seed = 0; seed < seeds; seed++) {
                var game = GameLifecycleTest.started(preset.rules(), firstSeed + seed);
                int steps = 0;
                while (game.phase != Game.Phase.MATCH_END && steps++ < 20000) {
                    boolean acted = false;
                    for (int seat = 0; seat < preset.rules().players(); seat++) {
                        var view = game.view(game.players[seat].id);
                        if (view.actions().isEmpty()) continue;
                        int actual = TrainingBot.choose(view, difficulty);
                        if (view.actions().size() > 1 &&
                            (game.phase == Game.Phase.TURN || game.phase == Game.Phase.REACTION)) {
                            eligible++;
                            boolean critical = view.actions().stream().anyMatch(action -> switch (action.type()) {
                                case RIICHI, CHI, PON, OPEN_KAN, CLOSED_KAN, ADDED_KAN, NUKI -> true;
                                default -> false;
                            });
                            double priority = random.nextDouble() * (critical ? 2.0 : 1.0);
                            var lowest = samples.stream().min(Comparator.comparingDouble(Sample::priority)).orElse(null);
                            if (samples.size() < limit || priority > lowest.priority()) {
                                var sample = new Sample(firstSeed + seed, steps, game.mjaiPosition(seat), actual, priority);
                                if (samples.size() == limit) samples.remove(lowest);
                                samples.add(sample);
                            }
                        }
                        if (!game.act(game.players[seat].id, view.decision(), actual))
                            throw new AssertionError("Rejected built-in action");
                        acted = true;
                        break;
                    }
                    if (!acted) throw new AssertionError("Deadlocked built-in match");
                }
                if (game.phase != Game.Phase.MATCH_END) throw new AssertionError("Match budget exceeded");
            }
            samples.sort(Comparator.comparingLong(Sample::seed).thenComparingInt(Sample::step));
            int disagreements = 0;
            int qAvailable = 0;
            double largestGap = Double.NEGATIVE_INFINITY;
            String largestCase = "";
            for (var sample : samples) {
                var position = sample.position();
                var view = position.view();
                int teacher;
                JsonObject response;
                JsonObject reachDiscard;
                try (var session = new MjaiSession(preset)) {
                    while ((teacher = session.poll(position)) < 0) Thread.sleep(1);
                    response = session.response();
                    reachDiscard = session.reachDiscardResponse();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Teacher review interrupted", error);
                }
                var actualAction = view.actions().get(sample.actual());
                var teacherAction = view.actions().get(teacher);
                boolean different = !equivalent(actualAction, teacherAction);
                if (different) disagreements++;
                var q = compareQ(view, actualAction, teacherAction, response, reachDiscard);
                if (q.gap() != null) {
                    qAvailable++;
                    if (different && q.gap() > largestGap) {
                        largestGap = q.gap();
                        largestCase = "seed=" + sample.seed() + " seat=" + position.seat() + " step=" + sample.step();
                    }
                }
                var row = new LinkedHashMap<String, Object>();
                row.put("seed", sample.seed()); row.put("step", sample.step()); row.put("seat", position.seat());
                row.put("rules", preset.rules().name()); row.put("difficulty", difficulty.name());
                row.put("builtIn", actualAction); row.put("teacher", teacherAction); row.put("different", different);
                row.put("qStage", q.stage());
                if (q.gap() != null) {
                    row.put("teacherQ", q.teacher()); row.put("builtInQ", q.builtIn());
                    row.put("teacherQMinusBuiltInQ", q.gap());
                }
                row.put("response", response);
                if (reachDiscard != null) row.put("reachDiscardResponse", reachDiscard);
                if (different) {
                    var candidates = TrainingBot.inspect(view, difficulty);
                    int teacherIndex = teacher;
                    var teacherCandidates = candidates.stream().filter(candidate -> candidate.index() == teacherIndex).toList();
                    row.put("teacherCandidates", teacherCandidates);
                    row.put("teacherRejection", teacherCandidates.isEmpty() ? "outside-inspection" :
                        teacherCandidates.stream().allMatch(candidate -> !candidate.exclusion().isEmpty()) ?
                            teacherCandidates.stream().map(TrainingBot.Diagnostic::exclusion).distinct().toList() :
                            "lower-utility-after-evaluation");
                    row.put("candidates", candidates);
                }
                row.put("view", view); row.put("mjaiHistory", position.events());
                Files.writeString(report, JSON.toJson(row) + "\n", StandardOpenOption.APPEND);
            }
            System.out.printf("teacher trajectory eligible=%d sampled=%d disagreements=%d q_available=%d report=%s%n",
                eligible, samples.size(), disagreements, qAvailable, report);
            if (!largestCase.isEmpty())
                System.out.printf("largest sampled same-state Q difference=%.3f %s%n", largestGap, largestCase);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    static QComparison compareQ(TableView view, Action builtIn, Action teacher,
                                JsonObject response, JsonObject reachDiscard) {
        if (view.rules().sanma()) return new QComparison("three-player-q-unmapped", null, null);
        if (builtIn.type() == Action.Type.RIICHI && teacher.type() == Action.Type.RIICHI) {
            return new QComparison("reach-discard", q(reachDiscard, discardIndex(teacher.tiles().getFirst())),
                q(reachDiscard, discardIndex(builtIn.tiles().getFirst())));
        }
        int teacherIndex = actionIndex(view, teacher);
        int builtInIndex = actionIndex(view, builtIn);
        if (teacherIndex == 42 && builtInIndex == 42 && !equivalent(builtIn, teacher) &&
            teacher.type() != Action.Type.OPEN_KAN && builtIn.type() != Action.Type.OPEN_KAN) {
            var meta = response == null || !response.has("meta") ? null : response.getAsJsonObject("meta");
            var select = meta != null && meta.has("kan_select") && meta.get("kan_select").isJsonObject() ?
                meta.getAsJsonObject("kan_select") : null;
            int teacherTile = teacher.type() == Action.Type.CLOSED_KAN ? Tile.kind(teacher.tiles().getFirst()) :
                discardIndex(teacher.tiles().getFirst());
            int builtInTile = builtIn.type() == Action.Type.CLOSED_KAN ? Tile.kind(builtIn.tiles().getFirst()) :
                discardIndex(builtIn.tiles().getFirst());
            return new QComparison("kan-select", qMeta(select, teacherTile), qMeta(select, builtInTile));
        }
        if (teacherIndex < 0 || builtInIndex < 0 || teacherIndex == builtInIndex && !equivalent(builtIn, teacher))
            return new QComparison("action-detail-unavailable", null, null);
        return new QComparison("action", q(response, teacherIndex), q(response, builtInIndex));
    }

    private static Double q(JsonObject response, int index) {
        return response == null || !response.has("meta") ? null : qMeta(response.getAsJsonObject("meta"), index);
    }

    private static Double qMeta(JsonObject meta, int index) {
        if (meta == null || index < 0) return null;
        if (!meta.has("mask_bits") || !meta.has("q_values")) return null;
        long mask = meta.get("mask_bits").getAsLong();
        if ((mask & (1L << index)) == 0) return null;
        int offset = Long.bitCount(mask & ((1L << index) - 1));
        var values = meta.getAsJsonArray("q_values");
        return offset < values.size() ? values.get(offset).getAsDouble() : null;
    }

    private static int actionIndex(TableView view, Action action) {
        return switch (action.type()) {
            case DISCARD -> discardIndex(action.tiles().getFirst());
            case RIICHI -> 37;
            case CHI -> {
                int called = Tile.kind(view.focus().tile());
                int min = action.tiles().stream().mapToInt(Tile::kind).min().orElseThrow();
                int max = action.tiles().stream().mapToInt(Tile::kind).max().orElseThrow();
                yield called < min ? 38 : called > max ? 40 : 39;
            }
            case PON -> 41;
            case OPEN_KAN, CLOSED_KAN, ADDED_KAN -> 42;
            case RON, TSUMO -> 43;
            case ABORT_NINE -> 44;
            case PASS -> 45;
            default -> -1;
        };
    }

    private static int discardIndex(int tile) {
        int kind = Tile.kind(tile);
        return Tile.red(tile) ? 34 + kind / 9 : kind;
    }

    private static boolean equivalent(Action first, Action second) {
        return first.type() == second.type() && first.tiles().stream().map(BotAnalysis::face).sorted().toList()
            .equals(second.tiles().stream().map(BotAnalysis::face).sorted().toList());
    }
}

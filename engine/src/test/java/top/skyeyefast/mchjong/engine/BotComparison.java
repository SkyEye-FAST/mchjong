package top.skyeyefast.mchjong.engine;

import java.util.Arrays;
import java.util.Locale;

/** Opt-in experiment, not a test suite. Seeds and outcomes never reach the bot. */
public final class BotComparison {
    private BotComparison() {}

    public static void main(String[] args) {
        if (args[0].equals("measure")) { measure(); return; }
        if (args[0].equals("suite")) {
            measure();
            for (var rules : new String[]{"TENHOU_4", "MAHJONG_SOUL_3"})
                for (var pair : new String[][]{{"NORMAL", "EASY"}, {"HARD", "NORMAL"}})
                    main(new String[]{args[rules.equals("TENHOU_4") ? 1 : 2], rules, pair[0], pair[1]});
            return;
        }
        int seeds = Integer.parseInt(args[0]);
        var rules = RuleSet.valueOf(args[1]);
        var a = BotDifficulty.valueOf(args[2]);
        var b = BotDifficulty.valueOf(args[3]);
        System.out.printf("comparison rules=%s challenger=%s field=%s seeds=%d%n", rules, a, b, seeds);
        var stats = new Stats[]{new Stats(), new Stats()};
        for (int seed = 0; seed < seeds; seed++) for (int rotate = 0; rotate < rules.players(); rotate++) {
            var game = GameLifecycleTest.started(rules, 74291L + seed);
            int steps = 0;
            while (game.phase != Game.Phase.MATCH_END && steps++ < 20000) {
                boolean acted = false;
                for (int seat = 0; seat < rules.players(); seat++) {
                    var view = game.view(game.players[seat].id);
                    if (view.actions().isEmpty()) continue;
                    int group = seat == rotate ? 0 : 1;
                    long start = System.nanoTime();
                    int action = TrainingBot.choose(view, group == 0 ? a : b);
                    long elapsed = System.nanoTime() - start;
                    stats[group].nanos += elapsed;
                    stats[group].times.add(elapsed);
                    stats[group].decisions++;
                    if (!game.act(game.players[seat].id, view.decision(), action)) throw new AssertionError("Rejected action");
                    acted = true;
                    break;
                }
                if (!acted) throw new AssertionError("Deadlock");
            }
            if (game.phase != Game.Phase.MATCH_END) throw new AssertionError("Match budget exceeded");
            for (int seat = 0; seat < rules.players(); seat++) {
                var s = stats[seat == rotate ? 0 : 1];
                s.matches++;
                s.rank += game.finalRanks.get(seat);
                s.points += game.players[seat].points - rules.config().startingPoints();
                for (var hand : game.replay.hands()) {
                    s.hands++;
                    final int player = seat;
                    if (hand.wins().stream().anyMatch(w -> w.from() == player)) s.deals++;
                    for (var win : hand.wins()) if (win.seat() == seat) {
                        s.wins++;
                        s.value += win.from() >= 0 ? win.score().ron() :
                            win.score().tsumoDealer() * (seat == hand.dealer() ? rules.players() - 1 : 1)
                                + (seat == hand.dealer() ? 0 : win.score().tsumoChild() * (rules.players() - 2));
                    }
                }
            }
            System.out.printf("seed=%d rotation=%d hands=%d%n", 74291 + seed, rotate, game.handNumber);
        }
        stats[0].print(a); stats[1].print(b);
    }

    private static void measure() {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74291);
        var view = game.view(game.players[game.turn].id);
        for (int i = 0; i < 100; i++) HandAnalyzer.discardEfficiency(view.seats().get(view.viewerSeat()).hand(), java.util.List.of());
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) HandAnalyzer.discardEfficiency(view.seats().get(view.viewerSeat()).hand(), java.util.List.of());
        System.out.printf(Locale.ROOT, "discardEfficiency mean_ms=%.3f%n", (System.nanoTime() - start) / 500e6);
        for (var level : BotDifficulty.values()) {
            for (int i = 0; i < 20; i++) TrainingBot.choose(view, level);
            long[] times = new long[100];
            for (int i = 0; i < times.length; i++) {
                start = System.nanoTime(); TrainingBot.choose(view, level); times[i] = System.nanoTime() - start;
            }
            Arrays.sort(times);
            System.out.printf(Locale.ROOT, "%s mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f max_ms=%.3f%n", level,
                Arrays.stream(times).average().orElseThrow() / 1e6, times[50] / 1e6, times[95] / 1e6, times[99] / 1e6);
        }
    }

    private static final class Stats {
        long hands, wins, deals, value, decisions, nanos, matches, rank, points;
        final java.util.ArrayList<Long> times = new java.util.ArrayList<>();
        void print(BotDifficulty level) {
            times.sort(Long::compare);
            System.out.printf(Locale.ROOT,
                "%s seats=%d hands=%d win=%.4f deal=%.4f value=%.1f rank=%.3f points=%.1f decision_ms=%.3f p95_ms=%.3f max_ms=%.3f decisions=%d%n",
                level, matches, hands, wins / (double) hands, deals / (double) hands, value / (double) Math.max(1, wins),
                rank / (double) matches, points / (double) matches, nanos / (double) decisions / 1e6,
                times.get((int) (times.size() * .95)) / 1e6, times.getLast() / 1e6, decisions);
        }
    }
}

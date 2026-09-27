package top.skyeyefast.mchjong.engine;

import java.util.Arrays;
import java.util.Locale;

/** Opt-in experiment, not a test suite. Seeds and outcomes never reach the bot. */
public final class BotComparison {
    private BotComparison() {}

    public static void main(String[] args) {
        if (args[0].equals("paired")) { BotReport.print(args[1], args[2]); return; }
        if (args[0].equals("mjai")) {
            try {
                var preset = new com.google.gson.Gson().fromJson(java.nio.file.Files.readString(java.nio.file.Path.of(args[1])), BotPreset.class);
                var level = BotDifficulty.valueOf(args[2]);
                compare(Integer.parseInt(args[3]), preset.rules(), level, level, Long.parseLong(args[4]), preset);
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            return;
        }
        if (args[0].equals("tables")) {
            tables(Integer.parseInt(args[1]), Integer.parseInt(args[2]), RuleSet.valueOf(args[3]), BotDifficulty.valueOf(args[4]));
            return;
        }
        if (args[0].equals("measure")) { measure(); return; }
        if (args[0].equals("hand")) {
            var game = TrainingBotTest.hand(args[1]);
            if (args.length > 2 && args[2].equals("legal")) {
                game.players[0].firstTurn = false;
                game.options.set(0, LegalActions.onTurn(game, 0));
            }
            inspect(game.view(game.players[0].id));
            return;
        }
        if (args[0].equals("opening")) {
            var game = GameLifecycleTest.started(RuleSet.TENHOU_4, Long.parseLong(args[1]));
            inspect(game.view(game.players[game.turn].id));
            return;
        }
        if (args[0].equals("position") || args[0].equals("inspect")) {
            try {
                var data = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(java.nio.file.Path.of(args[1])));
                if (args.length > 2) data = data.getAsJsonArray().get(Integer.parseInt(args[2])).getAsJsonObject().get("view");
                var view = new com.google.gson.Gson().fromJson(data, TableView.class);
                if (args[0].equals("position")) measure(view); else inspect(view);
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            return;
        }
        if (args[0].equals("suite")) {
            measure();
            for (var rules : new String[]{"TENHOU_4", "MAHJONG_SOUL_3"})
                main(new String[]{args[rules.equals("TENHOU_4") ? 1 : 2], rules, "HARD", "EASY", args.length > 3 ? args[3] : "74291"});
            return;
        }
        int seeds = Integer.parseInt(args[0]);
        var rules = RuleSet.valueOf(args[1]);
        var a = BotDifficulty.valueOf(args[2]);
        var b = BotDifficulty.valueOf(args[3]);
        long firstSeed = args.length > 4 ? Long.parseLong(args[4]) : 74291;
        compare(seeds, rules, a, b, firstSeed, null);
    }

    private static void compare(int seeds, RuleSet rules, BotDifficulty a, BotDifficulty b, long firstSeed, BotPreset external) {
        String challenger = external == null ? a.name() : external.id();
        System.out.printf("comparison rules=%s challenger=%s field=%s seeds=%d first_seed=%d%n", rules, challenger, b, seeds, firstSeed);
        var stats = new Stats[]{new Stats(), new Stats()};
        var disagreements = new java.util.ArrayList<java.util.Map<String, Object>>();
        var sample = new java.util.Random(0);
        int differences = 0;
        int compared = 0;
        String report = System.getProperty("bot.report");
        if (report != null) {
            try {
                var path = java.nio.file.Path.of(report).toAbsolutePath();
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.writeString(path, "");
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }
        for (int seed = 0; seed < seeds; seed++) for (int rotate = 0; rotate < rules.players(); rotate++) {
            var game = GameLifecycleTest.started(rules, firstSeed + seed);
            var mjai = external == null ? null : new MjaiSession(external);
            var riskPath = System.getProperty("bot.riskReport");
            var risk = riskPath == null ? null : new BotRiskReport(riskPath + "-" + (firstSeed + seed) + "-" + rotate + ".jsonl");
            try {
            int steps = 0;
            int notifiedHand = -1;
            while (game.phase != Game.Phase.MATCH_END && steps++ < 20000) {
                boolean acted = false;
                for (int seat = 0; seat < rules.players(); seat++) {
                    var view = game.view(game.players[seat].id);
                    if (view.actions().isEmpty()) continue;
                    int group = seat == rotate ? 0 : 1;
                    long start = System.nanoTime();
                    int action;
                    if (group == 0 && mjai != null && (game.phase == Game.Phase.TURN || game.phase == Game.Phase.REACTION)) {
                        var position = game.mjaiPosition(seat);
                        while ((action = mjai.poll(position)) < 0) {
                            try { Thread.sleep(1); }
                            catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException(error); }
                        }
                    } else action = TrainingBot.choose(view, group == 0 ? a : b);
                    long elapsed = System.nanoTime() - start;
                    if (external != null && group == 0 && view.actions().size() > 1 && Boolean.getBoolean("bot.profile")
                        && (game.phase == Game.Phase.TURN || game.phase == Game.Phase.REACTION)) {
                        int heuristic = TrainingBot.choose(view, b);
                        compared++;
                        if (!equivalent(view.actions().get(action), view.actions().get(heuristic))) {
                            var entry = java.util.Map.<String, Object>of("seed", firstSeed + seed, "rotation", rotate,
                                "model", view.actions().get(action), "heuristic", view.actions().get(heuristic),
                                "response", mjai.response(), "view", view);
                            int selected = sample.nextInt(++differences);
                            if (disagreements.size() < 300) disagreements.add(entry);
                            else if (selected < 300) disagreements.set(selected, entry);
                        }
                    }
                    stats[group].nanos += elapsed;
                    stats[group].times.add(elapsed);
                    stats[group].decisions++;
                    stats[group].actions.merge(view.actions().get(action).type(), 1, Integer::sum);
                    for (var type : new Action.Type[]{Action.Type.RIICHI, Action.Type.PON, Action.Type.NUKI})
                        if (view.actions().stream().anyMatch(o -> o.type() == type)) stats[group].offered.merge(type, 1, Integer::sum);
                    if (elapsed > stats[group].slowest) { stats[group].slowest = elapsed; stats[group].position = view; }
                    if (Boolean.getBoolean("bot.profile")) stats[group].observe(view, action);
                    if (risk != null) risk.observe(firstSeed + seed, rotate, view, view.actions().get(action));
                    if (!game.act(game.players[seat].id, view.decision(), action)) throw new AssertionError("Rejected action");
                    if (risk != null && (game.phase == Game.Phase.HAND_END || game.phase == Game.Phase.MATCH_END))
                        risk.finish(game.replay.hands().getLast());
                    if (mjai != null && game.handNumber != notifiedHand
                        && (game.phase == Game.Phase.HAND_END || game.phase == Game.Phase.MATCH_END)) {
                        var hand = game.replay.hands().getLast();
                        mjai.finish(hand.number(), rotate, MjaiProtocol.hand(rotate, hand.round(), hand.dealer(), hand.honba(), hand.sticks(),
                            hand.initialPoints(), hand.initialHands(), hand.initialDora(), hand.events()),
                            MjaiProtocol.result(hand, game.phase == Game.Phase.MATCH_END));
                        notifiedHand = game.handNumber;
                    }
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
                        for (var yaku : win.score().yaku()) s.yaku.merge(yaku, 1, Integer::sum);
                        s.value += win.from() >= 0 ? win.score().ron() :
                            win.score().tsumoDealer() * (seat == hand.dealer() ? rules.players() - 1 : 1)
                                + (seat == hand.dealer() ? 0 : win.score().tsumoChild() * (rules.players() - 2));
                    }
                }
            }
            var points = java.util.stream.IntStream.range(0, rules.players())
                .map(seat -> game.players[seat].points - rules.config().startingPoints()).boxed().toList();
            System.out.printf("seed=%d rotation=%d hands=%d points=%s ranks=%s%n", firstSeed + seed, rotate, game.handNumber, points, game.finalRanks);
            if (report != null) {
                var result = java.util.Map.of("seed", firstSeed + seed, "rotation", rotate, "challenger", challenger,
                    "field", b.name(), "rules", rules.name(), "points", points, "ranks", game.finalRanks, "hands", game.handNumber);
                try {
                    java.nio.file.Files.writeString(java.nio.file.Path.of(report), new com.google.gson.Gson().toJson(result) + "\n",
                        java.nio.file.StandardOpenOption.APPEND);
                } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
            }
            } finally { if (mjai != null) mjai.close(); }
        }
        stats[0].print(challenger); stats[1].print(b.name());
        if (compared > 0) System.out.printf("teacher compared=%d differences=%d sampled=%d%n", compared, differences, disagreements.size());
        if (external != null && Boolean.getBoolean("bot.profile")) {
            try {
                java.nio.file.Files.writeString(java.nio.file.Path.of("build", "mjai-disagreements-" + external.id() + "-" + b + ".json"),
                    new com.google.gson.Gson().toJson(disagreements));
            } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
        }
        if (Boolean.getBoolean("bot.profile")) for (int i = 0; i < stats.length; i++) {
            try {
                String label = i == 0 ? challenger : b.name();
                java.nio.file.Files.writeString(java.nio.file.Path.of("build", "bot-slow-" + label + ".json"),
                    new com.google.gson.Gson().toJson(stats[i].position));
                if (stats[i].earlyRetreat != null)
                    java.nio.file.Files.writeString(java.nio.file.Path.of("build", "bot-retreat-" + label + ".json"),
                        new com.google.gson.Gson().toJson(stats[i].earlyRetreat));
                System.out.printf("%s retreats=%d unannounced_retreats=%d%n", label, stats[i].retreats, stats[i].unannouncedRetreats);
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
    }

    private static boolean equivalent(Action first, Action second) {
        return first.type() == second.type() && first.tiles().stream().map(BotAnalysis::face).sorted().toList()
            .equals(second.tiles().stream().map(BotAnalysis::face).sorted().toList());
    }

    private static void measure() {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74291);
        var view = game.view(game.players[game.turn].id);
        measure(view);
    }
    private static void tables(int count, int ticks, RuleSet rules, BotDifficulty level) {
        if (count < 1 || ticks < 100) throw new IllegalArgumentException("Need tables and at least 100 ticks");
        var games = new Game[count];
        for (int i = 0; i < count; i++) {
            games[i] = GameLifecycleTest.started(rules, 95311L + i);
            for (int seat = 0; seat < rules.players(); seat++) {
                games[i].players[seat].bot = true;
                games[i].players[seat].botDifficulty = level;
            }
        }
        long[] times = new long[ticks];
        int transitions = 0, sampled = 0;
        for (int tick = 0; tick < ticks; tick++) {
            if (Arrays.stream(games).allMatch(g -> g.phase == Game.Phase.MATCH_END)) break;
            long start = System.nanoTime();
            for (var game : games) {
                if (game.phase == Game.Phase.MATCH_END) continue;
                long before = game.decision;
                game.tick();
                if (before != game.decision) transitions++;
            }
            times[sampled++] = System.nanoTime() - start;
        }
        times = Arrays.copyOf(times, sampled);
        Arrays.sort(times);
        System.out.printf(Locale.ROOT, "tables=%d rules=%s level=%s ticks=%d transitions=%d hands=%d mean_ms=%.3f p95_ms=%.3f max_ms=%.3f over_50_ms=%d%n",
            count, rules, level, sampled, transitions, Arrays.stream(games).mapToInt(g -> g.handNumber).sum(),
            Arrays.stream(times).average().orElseThrow() / 1e6, times[(int) (sampled * .95)] / 1e6, times[sampled - 1] / 1e6,
            Arrays.stream(times).filter(t -> t > 50_000_000).count());
    }
    private static void inspect(TableView view) {
        for (var level : BotDifficulty.values()) {
            System.out.printf("%s choice=%s%n", level, view.actions().get(TrainingBot.choose(view, level)));
            var analysis = new BotAnalysis(view, level);
            System.out.println(analysis.defence.threats);
            for (var candidate : TrainingBot.inspect(view, level)) {
                var evaluation = candidate.evaluation();
                var potential = evaluation.potential();
                System.out.printf(Locale.ROOT, "%s discard=%s shanten=%d live=%d value=%.1f mode=%s search=%s excluded=%s%n",
                    view.actions().get(candidate.index()), candidate.discard() < 0 ? "-" : Tile.notation(Tile.kind(candidate.discard())),
                    evaluation.shanten(), evaluation.live(), evaluation.points(), analysis.defence.mode(evaluation),
                    candidate.search(), candidate.exclusion());
                System.out.printf(Locale.ROOT, "  plan=%s potential_han=%.3f closed_option=%.3f waits=%s%n  terms=%s adjustments=%s forward=%.3f final_utility=%.3f%n",
                    potential.routes().plan(), potential.routes().han(), potential.closedOption(), evaluation.waits(),
                    evaluation.terms(), candidate.adjustments(), candidate.forward(), candidate.utility());
                for (var route : potential.routes().routes())
                    System.out.printf(Locale.ROOT, "  route=%s family=%s missing=%.2f progress=%.3f han=%.1f%n",
                        route.name(), route.family(), route.missing(), route.progress(), route.han());
            }
        }
    }
    private static void measure(TableView view) {
        var self = view.seats().get(view.viewerSeat());
        var cpu = java.lang.management.ManagementFactory.getThreadMXBean();
        if (!cpu.isCurrentThreadCpuTimeSupported()) throw new IllegalStateException("Decision timing requires JVM thread CPU accounting");
        if (!cpu.isThreadCpuTimeEnabled()) cpu.setThreadCpuTimeEnabled(true);
        for (int i = 0; i < 100; i++) HandAnalyzer.discardEfficiency(self.hand(), self.melds());
        long start = System.nanoTime();
        for (int i = 0; i < 500; i++) HandAnalyzer.discardEfficiency(self.hand(), self.melds());
        System.out.printf(Locale.ROOT, "discardEfficiency mean_ms=%.3f%n", (System.nanoTime() - start) / 500e6);
        for (var level : BotDifficulty.values()) {
            for (int i = 0; i < 20; i++) TrainingBot.choose(view, level);
            long[] times = new long[100];
            long cpuNanos = 0;
            for (int i = 0; i < times.length; i++) {
                long cpuStart = cpu.getCurrentThreadCpuTime();
                start = System.nanoTime(); TrainingBot.choose(view, level); times[i] = System.nanoTime() - start;
                cpuNanos += cpu.getCurrentThreadCpuTime() - cpuStart;
            }
            Arrays.sort(times);
            System.out.printf(Locale.ROOT, "%s mean_ms=%.3f p50_ms=%.3f p95_ms=%.3f max_ms=%.3f cpu_ms=%.3f%n", level,
                Arrays.stream(times).average().orElseThrow() / 1e6, times[50] / 1e6, times[95] / 1e6, times[99] / 1e6,
                cpuNanos / (times.length * 1e6));
        }
    }

    private static final class Stats {
        long hands, wins, deals, value, decisions, nanos, matches, rank, points;
        long slowest;
        TableView position;
        int retreats, unannouncedRetreats;
        TableView earlyRetreat;
        void observe(TableView view, int selected) {
            var action = view.actions().get(selected);
            if (action.type() != Action.Type.DISCARD) return;
            var self = view.seats().get(view.viewerSeat());
            var shapes = HandAnalyzer.discardEfficiency(self.hand(), self.melds(), false);
            int minimum = shapes.values().stream().mapToInt(TileEfficiency::shanten).min().orElseThrow();
            if (shapes.get(Tile.kind(action.tiles().getFirst())).shanten() <= minimum) return;
            retreats++;
            if (view.seats().stream().noneMatch(TableView.Seat::riichi)) {
                unannouncedRetreats++;
                if (earlyRetreat == null && view.remaining() > view.rules().players() * 8) earlyRetreat = view;
            }
        }
        final java.util.ArrayList<Long> times = new java.util.ArrayList<>();
        final java.util.EnumMap<Action.Type, Integer> actions = new java.util.EnumMap<>(Action.Type.class);
        final java.util.EnumMap<Action.Type, Integer> offered = new java.util.EnumMap<>(Action.Type.class);
        final java.util.Map<String, Integer> yaku = new java.util.TreeMap<>();
        void print(String level) {
            times.sort(Long::compare);
            System.out.printf(Locale.ROOT,
                "%s seats=%d hands=%d win=%.4f deal=%.4f value=%.1f rank=%.3f points=%.1f decision_ms=%.3f p95_ms=%.3f max_ms=%.3f decisions=%d%n",
                level, matches, hands, wins / (double) hands, deals / (double) hands, value / (double) Math.max(1, wins),
                rank / (double) matches, points / (double) matches, nanos / (double) decisions / 1e6,
                times.get((int) (times.size() * .95)) / 1e6, times.getLast() / 1e6, decisions);
            System.out.printf("%s actions=%s opportunities=%s%n", level, actions, offered);
            System.out.printf("%s yaku=%s%n", level, yaku);
        }
    }
}

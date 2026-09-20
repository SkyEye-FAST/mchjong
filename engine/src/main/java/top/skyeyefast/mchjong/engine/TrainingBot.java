package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** Deterministic decisions from own/public information and engine-issued legal actions. */
final class TrainingBot {
    private final TableView view;
    private final BotDifficulty level;
    private final BotAnalysis analysis;
    private final BotDefence defence;
    private final BotAnalysis.State initial;

    private TrainingBot(TableView view, BotDifficulty level) {
        this.view = view; this.level = level;
        analysis = new BotAnalysis(view, level); defence = analysis.defence; initial = analysis.initial();
    }
    static int choose(TableView view, BotDifficulty level) {
        if (view.actions().isEmpty()) throw new IllegalArgumentException("A bot needs a legal decision");
        for (var type : List.of(RON, TSUMO, NEXT, READY, DRAW_WIND, SHUFFLE, BUILD_WALL, TAKE_PACKET, DRAW)) {
            int index = index(view.actions(), type);
            if (index >= 0) return index;
        }
        if (view.actions().size() == 1) return 0;
        return new TrainingBot(view, level).choose();
    }
    private static int index(List<Action> actions, Action.Type type) {
        for (int i = 0; i < actions.size(); i++) if (actions.get(i).type() == type) return i;
        return -1;
    }
    private record Choice(int index, BotAnalysis.State state, BotAnalysis.Evaluation evaluation,
                          int discard, boolean replacement, double adjustment, String key) {}
    private Choice choice(int index, BotAnalysis.State state, TileEfficiency shape, int discard, boolean replacement, double adjustment) {
        return new Choice(index, state, analysis.evaluate(state, shape, analysis.unseen), discard, replacement, adjustment,
            stable(view.actions().get(index)) + ":" + (discard < 0 ? -1 : BotAnalysis.face(discard)));
    }
    private int choose() {
        var choices = new ArrayList<Choice>();
        var unique = new HashSet<String>();
        var shapes = initial.hand().size() % 3 == 2 ? analysis.discards(initial) : java.util.Map.<Integer, TileEfficiency>of();
        var indices = java.util.stream.IntStream.range(0, view.actions().size()).boxed().sorted(
            Comparator.<Integer, String>comparing(i -> stable(view.actions().get(i)))
                .thenComparing(i -> view.actions().get(i).tiles().stream().sorted().toList().toString())).toList();
        for (int i : indices) {
            var action = view.actions().get(i);
            if (!unique.add(stable(action))) continue;
            switch (action.type()) {
                case DISCARD, RIICHI -> {
                    int tile = action.tiles().getFirst();
                    var next = initial.discard(tile, action.type() == RIICHI);
                    choices.add(choice(i, next, shapes.get(Tile.kind(tile)), tile, false, 0));
                }
                case PASS -> choices.add(choice(i, initial, analysis.shape(initial), Tile.ABSENT, false, 0));
                case CHI, PON -> addCall(choices, i, action);
                case OPEN_KAN, CLOSED_KAN, ADDED_KAN, NUKI -> {
                    var next = declaration(action);
                    var shape = analysis.shape(next);
                    double cost = declarationCost(action, next);
                    double opportunity = Math.min(8, BotAnalysis.live(shape.improving(), analysis.unseen) * .25);
                    choices.add(choice(i, next, shape, Tile.ABSENT, true, opportunity - cost));
                }
                default -> { }
            }
        }
        if (choices.isEmpty()) throw new IllegalStateException("No evaluated legal bot action");
        var baseline = choices.stream().filter(c -> view.actions().get(c.index).type() == DISCARD
            || view.actions().get(c.index).type() == PASS).min(Comparator.<Choice>comparingInt(c -> c.evaluation.shanten())
                .thenComparing(Comparator.comparingDouble((Choice c) -> c.evaluation.utility()).reversed())
                .thenComparing(Choice::key)).orElse(choices.getFirst());
        int minimum = choices.stream().filter(c -> view.actions().get(c.index).type() == DISCARD
            || view.actions().get(c.index).type() == PASS).mapToInt(c -> c.evaluation.shanten()).min().orElse(baseline.evaluation.shanten());
        var mode = defence.mode(baseline.evaluation);
        int abort = index(view.actions(), ABORT_NINE);
        if (abort >= 0 && baseline.evaluation.shanten() >= 4 && baseline.evaluation.live() < 18) return abort;
        Choice fold = null;
        if (mode == BotDefence.Mode.FOLD && !initial.riichi()) {
            int pass = index(view.actions(), PASS);
            // Safety can break completed groups and increase shanten.
            fold = pass >= 0 ? baseline : choices.stream().filter(c -> view.actions().get(c.index).type() == DISCARD)
                .min(Comparator.<Choice>comparingDouble(c -> defence.danger(c.discard))
                    .thenComparing(Comparator.comparingDouble((Choice c) -> c.evaluation.utility()).reversed())
                    .thenComparing(Choice::key)).orElse(baseline);
            var safe = fold;
            // Judge a call by the resulting hand. A valuable, fast continuation
            // can justify attacking even when the unchanged hand would fold.
            choices.removeIf(c -> c != safe && (!viable(c) || defence.mode(c.evaluation) != BotDefence.Mode.PUSH));
            if (choices.size() == 1) return safe.index;
        }
        choices.sort(Comparator.<Choice>comparingDouble(this::score).reversed().thenComparing(Choice::key));
        double bestScore = Double.NEGATIVE_INFINITY;
        Choice best = baseline;
        int roots = 0;
        for (var candidate : choices) {
            var type = view.actions().get(candidate.index).type();
            if ((type == CHI || type == PON || type == OPEN_KAN) && !viable(candidate)) continue;
            if (type == RIICHI && candidate.evaluation.waits().quality() == 0) continue;
            if (candidate != fold && candidate.evaluation.shanten() > minimum + 1) continue;
            // A larger raw ukeire count is not evidence that going backwards is
            // faster. Basic evaluators preserve an available viable route; HARD
            // must actually search a retreat, while dead/yakuless routes can escape.
            boolean safer = candidate.discard >= 0 && baseline.discard >= 0
                && defence.danger(candidate.discard) < defence.danger(baseline.discard);
            boolean retreat = candidate.evaluation.shanten() > minimum && !candidate.replacement && candidate != fold && !safer;
            if (retreat && viable(baseline) && baseline.evaluation.live() > 0
                && (level != BotDifficulty.HARD || roots >= BotAnalysis.SEARCH_ROOTS)) continue;
            double score = score(candidate);
            boolean expand = candidate != fold && (level == BotDifficulty.HARD || candidate.replacement);
            if (expand && roots < BotAnalysis.SEARCH_ROOTS
                && candidate.evaluation.shanten() <= minimum + 1) {
                double forward = analysis.forward(candidate.state, candidate.evaluation, candidate.replacement);
                score += forward - candidate.evaluation.utility() - (candidate.replacement ? Math.min(8, candidate.evaluation.live() * .25) : 0);
                roots++;
            }
            if (score > bestScore || score == bestScore && candidate.key.compareTo(best.key) < 0) {
                bestScore = score; best = candidate;
            }
        }
        return best.index;
    }
    private boolean viable(Choice candidate) {
        return candidate.evaluation.shanten() == 0 ? candidate.evaluation.waits().quality() > 0
            : analysis.value.potential(candidate.state).viable();
    }
    private double score(Choice candidate) {
        double score = candidate.evaluation.utility() + candidate.adjustment;
        if (candidate.discard >= 0) score -= defence.penalty(candidate.discard, defence.mode(candidate.evaluation));
        score += defence.reserve(candidate.state);
        var type = view.actions().get(candidate.index).type();
        if (type == RIICHI) score -= analysis.riichiCost(candidate.evaluation)
            + (defence.placementUrgency(candidate.evaluation.points()) < 1 ? 5 : 0);
        if (type == CHI || type == PON || type == OPEN_KAN) {
            // A closed hand retains a future riichi/tsumo path and defensive options.
            if (initial.melds().stream().allMatch(Meld::closed)) score -= level == BotDifficulty.EASY ? 4 : 8;
            score -= defence.pressure() * 2;
        }
        if (candidate.evaluation.shanten() == 0)
            score += Math.min(8, candidate.evaluation.waits().quality()) * view.riichiSticks() * .4;
        return score;
    }
    private void addCall(List<Choice> choices, int index, Action action) {
        var next = declaration(action);
        var forbidden = LegalActions.forbiddenAfterCall(action, view.focus().tile());
        var shapes = analysis.discards(next);
        var faces = new HashSet<Integer>();
        for (int tile : next.hand()) {
            if (forbidden.contains(Tile.kind(tile)) || !faces.add(BotAnalysis.face(tile))) continue;
            choices.add(choice(index, next.discard(tile, false), shapes.get(Tile.kind(tile)), tile, false, 0));
        }
    }
    private BotAnalysis.State declaration(Action action) {
        var hand = new ArrayList<>(initial.hand());
        for (int tile : action.tiles()) hand.remove(Integer.valueOf(tile));
        var melds = new ArrayList<>(initial.melds());
        var norths = new ArrayList<>(initial.norths());
        switch (action.type()) {
            case NUKI -> norths.addAll(action.tiles());
            case ADDED_KAN -> {
                for (int i = 0; i < melds.size(); i++) {
                    var old = melds.get(i);
                    if (old.type() == Meld.Type.PON && old.kind() == Tile.kind(action.tiles().getFirst())) {
                        var tiles = new ArrayList<>(old.tiles()); tiles.addAll(action.tiles());
                        melds.set(i, new Meld(Meld.Type.ADDED_KAN, tiles, old.fromSeat(), old.calledTile()));
                        break;
                    }
                }
            }
            default -> {
                boolean closed = action.type() == CLOSED_KAN;
                var tiles = new ArrayList<>(action.tiles());
                if (!closed) tiles.add(view.focus().tile());
                tiles.sort(Tile.ORDER);
                melds.add(new Meld(Meld.Type.valueOf(action.type().name()), tiles,
                    closed ? view.viewerSeat() : view.focus().seat(), closed ? Tile.ABSENT : view.focus().tile()));
            }
        }
        boolean blocked = initial.ronBlocked() && (initial.riichi() || !view.rules().callsClearFuriten());
        return new BotAnalysis.State(hand, melds, norths, initial.river(), initial.riichi(), blocked,
            initial.riichi() ? initial.riichiHan() : 1);
    }
    private double declarationCost(Action action, BotAnalysis.State after) {
        int tile = action.tiles().getFirst();
        boolean nuki = action.type() == NUKI;
        double risk = action.type() == OPEN_KAN ? 0 : defence.danger(tile);
        if (action.type() == CLOSED_KAN) risk *= view.rules().robConcealedKan() && Tile.terminalOrHonor(Tile.kind(tile)) ? .08 : 0;
        if (nuki && !view.rules().robNorthWithoutKokushi()) risk *= .08;
        double cost = risk * 15 + (nuki ? 0 : defence.pressure() * 3);
        if (!nuki && view.rules().kanDora()) cost += newIndicatorCost(after);
        return cost;
    }
    private double newIndicatorCost(BotAnalysis.State after) {
        // Unknown indicators are averaged over unseen faces, never revealed in advance.
        int total = java.util.Arrays.stream(analysis.unseen).sum();
        double own = 0, opponents = 0;
        for (int face = 0; face < analysis.unseen.length; face++) {
            if (analysis.unseen[face] == 0) continue;
            int kind = Tile.doraAfter(face % 34, view.rules().sanma());
            double weight = analysis.unseen[face] / (double) Math.max(1, total);
            own += weight * (after.hand().stream().filter(t -> Tile.kind(t) == kind).count()
                + after.melds().stream().flatMap(m -> m.tiles().stream()).filter(t -> Tile.kind(t) == kind).count()
                + after.norths().stream().filter(t -> Tile.kind(t) == kind).count());
            for (var threat : defence.threats) {
                var opponent = view.seats().get(threat.seat());
                double publicCount = opponent.melds().stream().flatMap(m -> m.tiles().stream()).filter(t -> Tile.kind(t) == kind).count()
                    + opponent.norths().stream().filter(t -> Tile.kind(t) == kind).count();
                double concealed = opponent.hand().size() * (analysis.unseen[kind] + analysis.unseen[kind + 34]) / (double) Math.max(1, total);
                opponents += weight * threat.pressure() * (publicCount + concealed);
            }
        }
        return opponents * 5 - own * 3;
    }
    private static String stable(Action action) {
        return action.type().name() + action.tiles().stream().map(BotAnalysis::face).sorted().toList();
    }
}

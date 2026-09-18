package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** Bounded tile-efficiency and push/fold heuristics. No access to Game, its seed, or hidden tiles. */
final class TrainingBot {
    private final TableView view;
    private final TableView.Seat self;
    private final BotDifficulty difficulty;
    private final int[] known = new int[34];
    private final Set<Integer> dora = new HashSet<>();

    private TrainingBot(TableView view, BotDifficulty difficulty) {
        this.view = view;
        this.self = view.seats().get(view.viewerSeat());
        this.difficulty = difficulty;
        Set<Integer> visible = new HashSet<>(self.hand());
        // Even when the administrator permits open hands, ignore opponents' concealed tiles.
        for (var seat : view.seats()) {
            seat.river().forEach(discard -> visible.add(discard.tile()));
            seat.melds().forEach(meld -> visible.addAll(meld.tiles()));
            visible.addAll(seat.norths());
        }
        for (int tile : view.wall()) if (tile >= 0) {
            visible.add(tile);
            dora.add(Tile.doraAfter(Tile.kind(tile), view.rules().sanma()));
        }
        if (view.focus() != null) visible.add(view.focus().tile());
        for (int tile : visible) if (tile >= 0) known[Tile.kind(tile)]++;
    }

    static int choose(TableView view, BotDifficulty difficulty) {
        var actions = view.actions();
        if (actions.isEmpty()) throw new IllegalArgumentException("A bot needs a legal decision");
        for (var type : List.of(RON, TSUMO, NEXT, READY, DRAW_WIND, SHUFFLE, BUILD_WALL, TAKE_PACKET, DRAW)) {
            int index = Game.indexOf(actions, type);
            if (index >= 0) return index;
        }
        if (actions.size() == 1) return 0;
        return new TrainingBot(view, difficulty).choose();
    }

    private int choose() {
        var actions = view.actions();
        int pass = Game.indexOf(actions, PASS);
        if (pass >= 0) return chooseCall(pass);
        var shapes = HandAnalyzer.discardEfficiency(self.hand(), self.melds());
        int minimum = shapes.values().stream().mapToInt(TileEfficiency::shanten).min().orElse(8);
        int nuki = Game.indexOf(actions, NUKI);
        if (nuki >= 0) return nuki;
        int abort = Game.indexOf(actions, ABORT_NINE);
        if (abort >= 0 && minimum >= 3) return abort;
        int best = -1;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            if (action.type() != DISCARD && action.type() != RIICHI) continue;
            int tile = action.tiles().getFirst();
            var shape = shapes.get(Tile.kind(tile));
            if (shape == null) continue;
            double score = discardScore(tile, shape, minimum);
            if (action.type() == RIICHI) {
                int live = live(shape.improving());
                boolean furiten = self.river().stream().anyMatch(discard -> shape.improving().contains(Tile.kind(discard.tile())))
                    || shape.improving().contains(Tile.kind(tile));
                if (live == 0 || furiten || difficulty == BotDifficulty.HARD && threats() > 0 && live < 4) continue;
                score += difficulty == BotDifficulty.EASY ? 12 : 16 + Math.min(8, live);
            }
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (threats() == 0) for (int i = 0; i < actions.size(); i++) {
            var action = actions.get(i);
            if (action.type() != CLOSED_KAN && action.type() != ADDED_KAN) continue;
            var hand = new ArrayList<>(self.hand());
            hand.removeAll(action.tiles());
            var melds = new ArrayList<>(self.melds());
            if (action.type() == CLOSED_KAN)
                melds.add(new Meld(Meld.Type.CLOSED_KAN, action.tiles(), view.viewerSeat(), Tile.ABSENT));
            else {
                int kind = Tile.kind(action.tiles().getFirst());
                int pon = -1;
                for (int m = 0; m < melds.size(); m++) if (melds.get(m).type() == Meld.Type.PON && melds.get(m).kind() == kind) pon = m;
                if (pon < 0) continue;
                var old = melds.get(pon);
                var tiles = new ArrayList<>(old.tiles());
                tiles.addAll(action.tiles());
                melds.set(pon, new Meld(Meld.Type.ADDED_KAN, tiles, old.fromSeat(), old.calledTile()));
            }
            var after = HandAnalyzer.handEfficiency(hand, melds);
            if (after.shanten() <= minimum && live(after.improving()) > 0) return i;
        }
        if (best < 0) throw new IllegalStateException("No evaluated legal bot discard");
        return best;
    }

    private double discardScore(int tile, TileEfficiency shape, int minimum) {
        int kind = Tile.kind(tile);
        double score = -120 * shape.shanten();
        if (difficulty != BotDifficulty.EASY) score += live(shape.improving()) * 1.5;
        if (difficulty == BotDifficulty.HARD) score += live(shape.goodShape()) * .7;
        score -= (Tile.red(tile) ? 5 : 0) + (dora.contains(kind) ? 5 : 0);
        double danger = danger(kind);
        if (difficulty == BotDifficulty.EASY) {
            if (minimum >= 3 && threats() > 0) score -= danger * 30;
        } else {
            int value = (int) self.hand().stream().filter(id -> Tile.red(id) || dora.contains(Tile.kind(id))).count();
            boolean fold = minimum >= 2 || difficulty == BotDifficulty.HARD && minimum >= 1 && (threats() >= 2 || value < 2);
            score -= danger * (fold ? 260 : minimum == 1 ? 42 : 12);
            if (difficulty == BotDifficulty.HARD && shape.shanten() == 0 &&
                (shape.improving().contains(kind) || self.river().stream().anyMatch(discard -> shape.improving().contains(Tile.kind(discard.tile())))))
                score -= 22;
        }
        return score;
    }

    private int chooseCall(int pass) {
        if (difficulty == BotDifficulty.EASY || threats() > 0 || view.focus() == null) return pass;
        if (view.actions().stream().noneMatch(action -> action.type() == CHI || action.type() == PON || action.type() == OPEN_KAN)) return pass;
        var current = HandAnalyzer.handEfficiency(self.hand(), self.melds());
        if (current.shanten() == 0) return pass;
        var candidates = HandAnalyzer.callEfficiency(self.hand(), view.focus().tile(), view.actions());
        double bestScore = Double.NEGATIVE_INFINITY;
        int best = pass;
        for (int i = 0; i < view.actions().size(); i++) {
            var action = view.actions().get(i);
            Map<Integer, TileEfficiency> outcomes = candidates.get(action);
            if (outcomes == null) continue;
            for (var outcome : outcomes.entrySet()) {
                var after = outcome.getValue();
                if (after.shanten() >= current.shanten() || live(after.improving()) == 0
                    || !hasOpenYaku(action, outcome.getKey())) continue;
                double score = -120 * after.shanten() + live(after.improving()) * 1.5;
                if (difficulty == BotDifficulty.HARD) score += live(after.goodShape()) * .7;
                if (score > bestScore) { best = i; bestScore = score; }
            }
        }
        return best;
    }

    private boolean hasOpenYaku(Action action, int discard) {
        int selfWind = Tile.EAST + Math.floorMod(view.viewerSeat() - view.dealer(), view.rules().players());
        int roundWind = Tile.EAST + view.round() / view.rules().players();
        int called = Tile.kind(view.focus().tile());
        boolean yakuhai = self.melds().stream().anyMatch(meld -> meld.type() != Meld.Type.CHI &&
            (meld.kind() >= Tile.WHITE || meld.kind() == selfWind || meld.kind() == roundWind));
        if (action.type() != CHI && (called >= Tile.WHITE || called == selfWind || called == roundWind)) yakuhai = true;
        if (yakuhai) return true;
        if (difficulty != BotDifficulty.HARD || !view.rules().kuitan()) return false;
        var all = new ArrayList<>(self.hand());
        if (discard >= 0) {
            Integer removed = all.stream().filter(tile -> !action.tiles().contains(tile) && Tile.kind(tile) == discard).findFirst().orElse(null);
            if (removed == null) return false;
            all.remove(removed);
        }
        all.add(view.focus().tile());
        self.melds().forEach(meld -> all.addAll(meld.tiles()));
        return all.stream().noneMatch(tile -> Tile.terminalOrHonor(Tile.kind(tile)));
    }

    private int live(Set<Integer> kinds) {
        return kinds.stream().filter(kind -> !view.rules().sanma() || kind == 0 || kind >= 8)
            .mapToInt(kind -> Math.max(0, 4 - known[kind])).sum();
    }

    private boolean threat(TableView.Seat opponent) {
        return opponent.riichi() || difficulty == BotDifficulty.HARD &&
            opponent.melds().stream().filter(meld -> !meld.closed()).count() >= 3;
    }

    private int threats() {
        int count = 0;
        for (int seat = 0; seat < view.seats().size(); seat++)
            if (seat != view.viewerSeat() && threat(view.seats().get(seat))) count++;
        return count;
    }

    private double danger(int kind) {
        double danger = 0;
        for (int seat = 0; seat < view.seats().size(); seat++) {
            if (seat == view.viewerSeat()) continue;
            var opponent = view.seats().get(seat);
            if (!threat(opponent)) continue;
            Set<Integer> river = new HashSet<>();
            opponent.river().forEach(discard -> river.add(Tile.kind(discard.tile())));
            if (river.contains(kind)) continue;
            double risk = Tile.terminalOrHonor(kind) ? .8 : 1.0;
            if (difficulty == BotDifficulty.HARD) {
                if (kind >= Tile.EAST) risk = known[kind] >= 3 ? .08 : known[kind] == 2 ? .35 : .8;
                else {
                    int number = kind % 9;
                    boolean suji = number < 3 ? river.contains(kind + 3) : number > 5 ? river.contains(kind - 3)
                        : river.contains(kind - 3) && river.contains(kind + 3);
                    if (suji) risk *= .55;
                    boolean sequence = false;
                    for (int low = Math.max(kind / 9 * 9, kind - 2); low <= Math.min(kind, kind / 9 * 9 + 6); low++) {
                        boolean possible = true;
                        for (int k = low; k < low + 3; k++) if (k != kind && known[k] >= 4) possible = false;
                        sequence |= possible;
                    }
                    if (!sequence) risk *= .25;
                    if (dora.contains(kind)) risk *= 1.25;
                }
                if (seat == view.dealer()) risk *= 1.35;
            }
            danger += risk;
        }
        return danger;
    }
}

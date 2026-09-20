package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** Public per-opponent evidence. Risk, pressure and estimated value are ordinal heuristics. */
final class BotDefence {
    enum Mode { PUSH, CAUTIOUS, FOLD }
    record Threat(int seat, long river, double pressure, double value, boolean closed, boolean riichi) {}
    private final TableView view;
    private final BotDifficulty level;
    private final BotValue value;
    private final int[] known;
    final List<Threat> threats = new ArrayList<>();
    private final double[][] risks;

    BotDefence(TableView view, BotDifficulty level, BotValue value) {
        this.view = view; this.level = level; this.value = value;
        known = VisibleTiles.counts(view);
        risks = new double[view.rules().players()][34];
        for (int seat = 0; seat < view.seats().size(); seat++) {
            if (seat == view.viewerSeat()) continue;
            var opponent = view.seats().get(seat);
            long river = 0;
            for (var discard : opponent.river()) river |= 1L << Tile.kind(discard.tile());
            int open = (int) opponent.melds().stream().filter(m -> !m.closed()).count();
            int han = opponent.riichi() ? 1 : 0, bonus = opponent.norths().size();
            for (int north : opponent.norths()) bonus += value.bonus(north);
            int wind = Tile.EAST + Math.floorMod(seat - view.dealer(), view.rules().players());
            for (var meld : opponent.melds()) {
                for (int tile : meld.tiles()) bonus += value.bonus(tile);
                if (meld.type() != Meld.Type.CHI) {
                    if (meld.kind() >= Tile.WHITE) han++;
                    if (meld.kind() == wind) han++;
                    if (meld.kind() == Tile.EAST + view.round() / view.rules().players()) han++;
                }
            }
            // Visible yaku/bonus content and elapsed turns strengthen an open-hand signal;
            // neither calls nor discards prove tenpai or concealed yaku.
            double progress = Math.min(1, opponent.river().size() / 16.0);
            double pressure = opponent.riichi() ? 1 : open == 0 ? Math.max(0, progress - .65) * .5
                : Math.min(.9, open * .14 + progress * .28 + (han > 0 ? .12 : 0) + Math.min(4, bonus) * .04);
            if (level == BotDifficulty.EASY && !opponent.riichi() && !(open >= 2 && han > 0 && bonus >= 2)) pressure = 0;
            double estimate = (1000 + 1000 * Math.max(view.rules().minHan(), han) + 800 * bonus)
                * (seat == view.dealer() ? 1.5 : 1);
            var threat = new Threat(seat, river, pressure, estimate, open == 0, opponent.riichi());
            threats.add(threat);
            for (int kind = 0; kind < 34; kind++) risks[seat][kind] = risk(threat, kind);
        }
    }
    private static boolean contains(long mask, int kind) { return (mask & 1L << kind) != 0; }
    private double risk(Threat threat, int kind) {
        if (contains(threat.river, kind)) return 0; // Genbutsu applies to this opponent only.
        double pair = known[kind] >= 3 ? .10 : known[kind] == 2 ? .22 : .38;
        if (level == BotDifficulty.EASY) return Tile.terminalOrHonor(kind) ? .8 : 1;
        // Keep a residual for tanki/shanpon, chiitoitsu and (closed hands) kokushi.
        double special = threat.closed && Tile.terminalOrHonor(kind) ? .06 : .02;
        if (kind >= 27) return pair + special;
        double sequence = .65;
        int number = kind % 9;
        boolean suji = number < 3 ? contains(threat.river, kind + 3) : number > 5 ? contains(threat.river, kind - 3)
            : contains(threat.river, kind - 3) && contains(threat.river, kind + 3);
        if (suji) sequence *= .55; // Only the sequence component, never a safety proof.
        if (level == BotDifficulty.HARD) {
            int possible = 0, forms = 0;
            for (int low = Math.max(kind / 9 * 9, kind - 2); low <= Math.min(kind, kind / 9 * 9 + 6); low++) {
                forms++;
                boolean blocked = false;
                for (int k = low; k < low + 3; k++) if (k != kind && known[k] >= 4) blocked = true;
                if (!blocked) possible++;
            }
            sequence *= possible / (double) forms;
        }
        return pair + sequence + special;
    }
    double riskAgainst(int seat, int kind) { return risks[seat][kind]; }
    double danger(int tile) {
        double total = 0;
        for (var threat : threats) total += risks[threat.seat][Tile.kind(tile)] * threat.pressure
            * (1 + value.bonus(tile) * .12) * threat.value / 3000;
        return total;
    }
    double pressure() { return threats.stream().mapToDouble(Threat::pressure).sum(); }
    double strongestValue() { return threats.stream().filter(t -> t.pressure >= .4).mapToDouble(Threat::value).max().orElse(0); }
    Mode mode(BotAnalysis.Evaluation hand) {
        if (pressure() < .5) return Mode.PUSH;
        int draws = view.remaining() / view.rules().players();
        if (level == BotDifficulty.EASY) return hand.shanten() >= 3 ? Mode.FOLD : Mode.CAUTIOUS;
        double urgency = placementUrgency(hand.points());
        boolean goodTenpai = hand.shanten() == 0 && hand.waits().quality() >= 3 && hand.points() * urgency >= strongestValue();
        boolean goodApproach = hand.shanten() == 1 && hand.live() >= 14 && hand.points() * urgency >= strongestValue() * 1.5 && draws >= 5;
        if (goodTenpai || goodApproach && pressure() < 1.5) return Mode.PUSH;
        if (hand.shanten() >= 2 || hand.live() == 0 || draws <= hand.shanten() + 1
            || level == BotDifficulty.HARD && hand.shanten() > 0 && (pressure() >= 1.5 || hand.points() * urgency < strongestValue())) return Mode.FOLD;
        return Mode.CAUTIOUS;
    }
    double placementUrgency(double points) {
        if (view.round() < view.rules().scheduledRounds() - 1) return 1;
        int own = view.seats().get(view.viewerSeat()).points();
        int above = Integer.MAX_VALUE, below = Integer.MAX_VALUE;
        for (int seat = 0; seat < view.seats().size(); seat++) if (seat != view.viewerSeat()) {
            int difference = view.seats().get(seat).points() - own;
            if (difference >= 0) above = Math.min(above, difference); else below = Math.min(below, -difference);
        }
        if (above == Integer.MAX_VALUE) return .75;
        if (points + view.riichiSticks() * 1000 >= above) return 1.25;
        return below == Integer.MAX_VALUE ? 1.15 : 1;
    }
    double penalty(int tile, Mode mode) { return danger(tile) * (mode == Mode.PUSH ? 7 : mode == Mode.CAUTIOUS ? 30 : 100); }
    double reserve(BotAnalysis.State state) {
        if (level != BotDifficulty.HARD || pressure() < .5) return 0;
        long safeKinds = 0;
        for (int tile : state.hand()) if (danger(tile) < .08) safeKinds |= 1L << Tile.kind(tile);
        return Math.min(2, Long.bitCount(safeKinds)) * 3;
    }
}

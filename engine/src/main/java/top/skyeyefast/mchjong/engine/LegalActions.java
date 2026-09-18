package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** Computes all choices, including alternate red-five consumption, before issuing a decision. */
final class LegalActions {
    private LegalActions() {}

    static List<Action> onTurn(Game game, int seat) {
        PlayerState player = game.players[seat];
        var actions = new ArrayList<Action>();
        if (player.drawn >= 0 && score(game, seat, player.drawn, true) != null) actions.add(new Action(TSUMO, player.drawn));
        var ordered = new ArrayList<>(player.hand);
        ordered.sort(Tile.ORDER);
        for (int tile : ordered) {
            if (player.riichi && tile != player.drawn) continue;
            if (!player.forbiddenDiscards.contains(Tile.kind(tile))) actions.add(new Action(DISCARD, tile));
        }
        if (!player.canDeclare) return List.copyOf(actions);
        if (!player.riichi && player.closed() && game.wall.remaining() >= game.rules.minRiichiWall()
            && (!game.rules.needsRiichiDeposit() || player.points >= 1000)) {
            Set<Integer> tenpai = HandAnalyzer.tenpaiDiscards(player.hand, player.melds);
            for (int tile : ordered) if (tenpai.contains(Tile.kind(tile))) actions.add(new Action(RIICHI, tile));
        }
        if (game.rules.abortiveDraws() && player.firstTurn && game.uninterrupted
            && player.hand.stream().map(Tile::kind).filter(Tile::terminalOrHonor).distinct().count() >= 9) {
            actions.add(new Action(ABORT_NINE));
        }
        if (!game.wall.canReplace()) return List.copyOf(actions);
        if (game.rules.sanma()) for (int tile : ordered) {
            if (Tile.kind(tile) == Tile.NORTH && (!player.riichi || tile == player.drawn)) actions.add(new Action(NUKI, tile));
        }
        if (game.kanCount() >= 4) return List.copyOf(actions);
        for (int kind = 0; kind < 34; kind++) {
            List<Integer> matching = matching(player.hand, kind);
            if (matching.size() == 4 && (!player.riichi || legalRiichiKan(game, seat, kind, matching))) {
                actions.add(new Action(CLOSED_KAN, matching));
            }
        }
        if (!player.riichi) for (Meld meld : player.melds) if (meld.type() == Meld.Type.PON) {
            for (int tile : matching(player.hand, meld.kind())) actions.add(new Action(ADDED_KAN, tile));
        }
        return List.copyOf(actions);
    }

    private static boolean legalRiichiKan(Game game, int seat, int kind, List<Integer> quad) {
        PlayerState player = game.players[seat];
        if (player.drawn < 0 || Tile.kind(player.drawn) != kind) return false;
        var before = new ArrayList<>(player.hand);
        before.remove(Integer.valueOf(player.drawn));
        var after = new ArrayList<>(player.hand);
        after.removeAll(quad);
        var melds = new ArrayList<>(player.melds);
        melds.add(new Meld(Meld.Type.CLOSED_KAN, quad, seat, Tile.ABSENT));
        var waits = HandAnalyzer.waits(before, player.melds);
        if (!waits.equals(HandAnalyzer.waits(after, melds))) return false;
        if (game.rules.riichiKanKeepsMelds() && !HandAnalyzer.riichiKanKeepsMelds(before, player.melds, kind)) return false;
        if (game.rules.riichiKanKeepsYaku()) for (int wait : waits) for (boolean tsumo : new boolean[]{false, true}) {
            var previous = HandAnalyzer.score(before, player.melds, wait * 4, tsumo, game.wind(seat), game.round / 4,
                0, List.of("Richi"), game.rules);
            var next = HandAnalyzer.score(after, melds, wait * 4, tsumo, game.wind(seat), game.round / 4,
                0, List.of("Richi"), game.rules);
            if (previous != null && (next == null || !next.yaku().containsAll(previous.yaku()))) return false;
        }
        return true;
    }

    static List<Action> onReaction(Game game, int seat) {
        PlayerState player = game.players[seat];
        var actions = new ArrayList<Action>();
        int kind = Tile.kind(game.lastTile);
        Set<Integer> waits = HandAnalyzer.waits(player.hand, player.melds);
        if (waits.contains(kind) && !player.temporaryFuriten && !player.riichiFuriten
            && player.river.stream().noneMatch(discard -> waits.contains(Tile.kind(discard.tile())))) {
            HandScore score = score(game, seat, game.lastTile, false);
            boolean kokushi = score != null && score.yaku().stream().anyMatch(yaku -> yaku.startsWith("Kokushi"));
            boolean permitted = game.pending == null || switch (game.pending.type()) {
                case ADDED_KAN -> true;
                case CLOSED_KAN -> game.rules.robConcealedKan() && kokushi;
                case NUKI -> game.rules.robNorthWithoutKokushi() || kokushi;
                default -> false;
            };
            if (score != null && permitted) actions.add(new Action(RON, game.lastTile));
        }
        if (game.pending == null && !player.riichi && game.wall.remaining() > 0 && !game.fourKanAbort) {
            List<Integer> matching = matching(player.hand, kind);
            for (int a = 0; a < matching.size(); a++) for (int b = a + 1; b < matching.size(); b++) {
                var used = List.of(matching.get(a), matching.get(b));
                if (canDiscardAfter(player, used, Set.of(kind))) actions.add(new Action(PON, used));
            }
            if (matching.size() == 3 && game.wall.canReplace() && game.kanCount() < 4) actions.add(new Action(OPEN_KAN, matching));
            if (!game.rules.sanma() && seat == game.next(game.lastFrom) && kind < 27) {
                for (int low = Math.max(kind / 9 * 9, kind - 2); low <= Math.min(kind, kind / 9 * 9 + 6); low++) {
                    var others = new ArrayList<Integer>();
                    for (int tileKind = low; tileKind <= low + 2; tileKind++) if (tileKind != kind) others.add(tileKind);
                    Set<Integer> forbidden = new HashSet<>();
                    forbidden.add(kind);
                    if (kind == low && low % 9 < 6) forbidden.add(low + 3);
                    if (kind == low + 2 && low % 9 > 0) forbidden.add(low - 1);
                    for (int first : matching(player.hand, others.get(0))) for (int second : matching(player.hand, others.get(1))) {
                        var used = List.of(first, second);
                        if (canDiscardAfter(player, used, forbidden)) actions.add(new Action(CHI, used));
                    }
                }
            }
        }
        if (!actions.isEmpty()) actions.add(new Action(PASS));
        return List.copyOf(actions);
    }

    private static boolean canDiscardAfter(PlayerState player, List<Integer> used, Set<Integer> forbidden) {
        return player.hand.stream().anyMatch(tile -> !used.contains(tile) && !forbidden.contains(Tile.kind(tile)));
    }

    private static List<Integer> matching(List<Integer> hand, int kind) {
        return hand.stream().filter(tile -> Tile.kind(tile) == kind).sorted().toList();
    }

    static HandScore score(Game game, int seat, int tile, boolean tsumo) {
        PlayerState player = game.players[seat];
        var extra = new ArrayList<String>();
        if (player.riichi) {
            extra.add(player.doubleRiichi ? "WRichi" : "Richi");
            if (game.rules.ippatsu() && player.ippatsu) extra.add("Ippatsu");
        }
        if (tsumo) {
            if (player.rinshan) extra.add("Rinshan");
            else if (player.lastDraw) extra.add("Haitei");
            if (player.firstTurn && game.uninterrupted) extra.add(seat == game.dealer ? "Tenhou" : "Chihou");
        } else if (game.pending != null && game.pending.type() == ADDED_KAN) extra.add("Chankan");
        else if (game.pending == null && game.wall.remaining() == 0) extra.add("Houtei");
        if (!tsumo && game.rules.renhouMangan() && seat != game.dealer && player.firstTurn && game.uninterrupted)
            extra.add("Renhou");
        var all = new ArrayList<>(player.hand);
        if (all.size() + player.melds.size() * 3 == 13) all.add(tile);
        player.melds.forEach(meld -> all.addAll(meld.tiles()));
        all.addAll(player.norths);
        int dora = player.norths.size() + (int) all.stream().filter(Tile::red).count();
        for (int indicator : game.wall.indicators(game.rules.uraDora() && player.riichi)) {
            int kind = Tile.doraAfter(Tile.kind(indicator), game.rules.sanma());
            dora += (int) all.stream().filter(id -> Tile.kind(id) == kind).count();
        }
        return HandAnalyzer.score(player.hand, player.melds, tile, tsumo, game.wind(seat),
            game.round / game.rules.players(), dora, extra, game.rules);
    }

    static boolean formalTenpai(Game game, int seat) {
        PlayerState player = game.players[seat];
        return !HandAnalyzer.waits(player.hand, game.rules.formalTenpaiIgnoresMelds() ? List.of() : player.melds).isEmpty();
    }
}

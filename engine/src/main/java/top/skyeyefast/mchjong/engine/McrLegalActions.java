package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** MCR-only action derivation; structural wins are offered before minimum qualification. */
final class McrLegalActions {
    private McrLegalActions() {}

    static List<Action> forSeat(McrGame game, int seat) {
        if (game.phase() == McrGame.Phase.DRAW)
            return seat == game.turn() ? List.of(new Action(DRAW)) : List.of();
        if (game.phase() == McrGame.Phase.TURN)
            return seat == game.turn() ? turn(game, seat) : List.of();
        if (game.phase() == McrGame.Phase.REACTION && seat != game.claimFrom()) return reaction(game, seat);
        return List.of();
    }

    private static List<Action> turn(McrGame game, int seat) {
        var player = game.player(seat);
        var actions = new ArrayList<Action>();
        for (int tile : player.hand) actions.add(new Action(DISCARD, tile));
        if (game.score(seat) != null) actions.add(new Action(TSUMO, player.drawn));
        // A chow/pung is followed by a discard, not a new concealed/added kong declaration.
        if (player.drawn >= 0 && game.remaining() > 0) {
            for (int kind : player.hand.stream().map(Tile::kind).distinct().toList()) {
                var matching = matching(player.hand, kind);
                if (matching.size() == 4) actions.add(new Action(CLOSED_KAN, matching));
            }
            for (var meld : player.melds) if (meld.type() == Meld.Type.PON) {
                var matching = matching(player.hand, meld.kind());
                if (!matching.isEmpty()) actions.add(new Action(ADDED_KAN, matching.get(0)));
            }
        }
        return List.copyOf(actions);
    }

    private static List<Action> reaction(McrGame game, int seat) {
        var actions = new ArrayList<Action>();
        if (game.score(seat) != null) actions.add(new Action(RON, game.claimTile()));
        if (!game.robbingKong() && game.remaining() > 0) {
            var player = game.player(seat);
            int kind = Tile.kind(game.claimTile());
            var matching = matching(player.hand, kind);
            if (matching.size() >= 2) actions.add(new Action(PON, matching.subList(0, 2)));
            if (matching.size() == 3) actions.add(new Action(OPEN_KAN, matching));
            if (seat == (game.claimFrom() + 1) % 4 && kind < 27) {
                for (int low = Math.max(kind / 9 * 9, kind - 2); low <= Math.min(kind, kind / 9 * 9 + 6); low++) {
                    var used = new ArrayList<Integer>(2);
                    for (int needed = low; needed <= low + 2; needed++) if (needed != kind) {
                        var tiles = matching(player.hand, needed);
                        if (!tiles.isEmpty()) used.add(tiles.get(0));
                    }
                    if (used.size() == 2) actions.add(new Action(CHI, used));
                }
            }
        }
        if (!actions.isEmpty()) actions.add(new Action(PASS));
        return List.copyOf(actions);
    }

    private static List<Integer> matching(List<Integer> hand, int kind) {
        return hand.stream().filter(tile -> Tile.kind(tile) == kind).sorted().toList();
    }
}

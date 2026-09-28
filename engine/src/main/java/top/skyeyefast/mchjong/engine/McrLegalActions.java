package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import static top.skyeyefast.mchjong.engine.McrAction.Type.*;

/** MCR-only action derivation; structural wins are offered before minimum qualification. */
final class McrLegalActions {
    private McrLegalActions() {}

    static List<McrAction> forSeat(McrGame game, int seat) {
        if (game.phase() == McrGame.Phase.DRAW)
            return seat == game.turn() ? List.of(new McrAction(DRAW)) : List.of();
        if (game.phase() == McrGame.Phase.TURN)
            return seat == game.turn() ? turn(game, seat) : List.of();
        if (game.phase() == McrGame.Phase.REACTION && seat != game.claimFrom()) return reaction(game, seat);
        return List.of();
    }

    private static List<McrAction> turn(McrGame game, int seat) {
        var player = game.player(seat);
        var actions = new ArrayList<McrAction>();
        for (int tile : player.hand) actions.add(new McrAction(DISCARD, tile));
        if (game.score(seat) != null) actions.add(new McrAction(WIN, player.drawn));
        // A chow/pung is followed by a discard, not a new concealed/added kong declaration.
        if (player.drawn >= 0 && game.remaining() > 0) {
            for (int kind : player.hand.stream().map(Tile::kind).distinct().toList()) {
                var matching = matching(player.hand, kind);
                if (matching.size() == 4) actions.add(new McrAction(CONCEALED_KONG, matching));
            }
            for (var meld : player.melds) if (meld.type() == Meld.Type.TRIPLET) {
                var matching = matching(player.hand, meld.kind());
                if (!matching.isEmpty()) actions.add(new McrAction(MELDED_KONG, matching.get(0)));
            }
        }
        return List.copyOf(actions);
    }

    private static List<McrAction> reaction(McrGame game, int seat) {
        var actions = new ArrayList<McrAction>();
        if (game.score(seat) != null) actions.add(new McrAction(WIN, game.claimTile()));
        if (!game.robbingKong() && game.remaining() > 0) {
            var player = game.player(seat);
            int kind = Tile.kind(game.claimTile());
            var matching = matching(player.hand, kind);
            if (matching.size() >= 2) actions.add(new McrAction(PUNG, matching.subList(0, 2)));
            if (matching.size() == 3) actions.add(new McrAction(MELDED_KONG, matching));
            if (seat == (game.claimFrom() + 1) % 4 && kind < 27) {
                for (int low = Math.max(kind / 9 * 9, kind - 2); low <= Math.min(kind, kind / 9 * 9 + 6); low++) {
                    var used = new ArrayList<Integer>(2);
                    for (int needed = low; needed <= low + 2; needed++) if (needed != kind) {
                        var tiles = matching(player.hand, needed);
                        if (!tiles.isEmpty()) used.add(tiles.get(0));
                    }
                    if (used.size() == 2) actions.add(new McrAction(CHOW, used));
                }
            }
        }
        if (!actions.isEmpty()) actions.add(new McrAction(PASS));
        return List.copyOf(actions);
    }

    private static List<Integer> matching(List<Integer> hand, int kind) {
        return hand.stream().filter(tile -> Tile.kind(tile) == kind).sorted().toList();
    }
}

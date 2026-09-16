package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** Deliberately modest closed-hand opponent for local practice, not a competitive mahjong AI. */
final class TrainingBot {
    private TrainingBot() {}

    static int choose(Game game, int seat, List<Action> actions) {
        for (Action.Type type : List.of(RON, TSUMO, NEXT, READY, SHUFFLE, BUILD_WALL, TAKE_PACKET, DRAW, NUKI, CLOSED_KAN, RIICHI, ABORT_NINE, PASS)) {
            int index = Game.indexOf(actions, type);
            if (index >= 0) return index;
        }
        PlayerState player = game.players[seat];
        Set<Integer> best = HandAnalyzer.bestDiscardKinds(player.hand, player.melds);
        var candidates = new ArrayList<Integer>();
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            if (action.type() == DISCARD && best.contains(Tile.kind(action.tiles().getFirst()))) candidates.add(i);
        }
        if (candidates.isEmpty()) return Math.max(0, Game.indexOf(actions, DISCARD));
        return candidates.get(new Random(game.seed ^ game.decision ^ seat).nextInt(candidates.size()));
    }
}

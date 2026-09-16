package top.skyeyefast.mchjong.engine;

import java.util.List;
import static top.skyeyefast.mchjong.engine.Action.Type.*;

/** Physical handling only; the existing engine still judges claims and settles points. */
final class ManualHandling {
    int builtWalls;
    int packets;
    boolean replacement;
    boolean kan;

    static boolean active(Game.Phase phase) {
        return phase == Game.Phase.SHUFFLE || phase == Game.Phase.BUILD_WALL
            || phase == Game.Phase.DEAL || phase == Game.Phase.DRAW;
    }

    void begin(Game game) {
        builtWalls = packets = 0;
        replacement = kan = false;
        game.wall = null;
        game.turn = game.dealer;
        game.newDecision(Game.Phase.SHUFFLE);
    }

    List<Action> actions(Game game, int seat) {
        return switch (game.phase) {
            case SHUFFLE -> seat == game.dealer ? List.of(new Action(SHUFFLE)) : List.of();
            case BUILD_WALL -> (builtWalls & 1 << seat) == 0 ? List.of(new Action(BUILD_WALL)) : List.of();
            case DEAL -> seat == game.turn ? List.of(new Action(TAKE_PACKET)) : List.of();
            case DRAW -> seat == game.turn ? List.of(new Action(DRAW)) : List.of();
            default -> List.of();
        };
    }

    void act(Game game, int seat, Action action) {
        switch (action.type()) {
            case SHUFFLE -> {
                game.createWall();
                game.newDecision(Game.Phase.BUILD_WALL);
            }
            case BUILD_WALL -> {
                builtWalls |= 1 << seat;
                game.newDecision(builtWalls == (1 << game.rules.players()) - 1 ? Game.Phase.DEAL : Game.Phase.BUILD_WALL);
            }
            case TAKE_PACKET -> {
                int count = packets < 3 * game.rules.players() ? 4 : 1;
                for (int i = 0; i < count; i++) game.players[seat].hand.add(game.wall.draw());
                packets++;
                if (packets == 4 * game.rules.players()) {
                    game.recorder = game.replay == null ? null : new ReplayRecorder(game);
                    game.draw(game.dealer, false, false);
                } else {
                    game.turn = game.next(seat);
                    game.newDecision(Game.Phase.DEAL);
                }
            }
            case DRAW -> game.drawNow(seat, replacement, kan);
            default -> throw new IllegalStateException("Not a physical handling action");
        }
    }

    List<Integer> wallView(Game game, boolean ura) {
        var view = game.wall.publicTiles(ura);
        if (game.phase == Game.Phase.BUILD_WALL || game.phase == Game.Phase.DEAL) {
            int sideSize = view.size() / game.rules.players();
            for (int i = 0; i < view.size(); i++) {
                int side = (i + game.wall.breakOffset) % view.size() / sideSize;
                if (game.phase == Game.Phase.BUILD_WALL && (builtWalls & 1 << side) == 0) view.set(i, Tile.ABSENT);
                else if (view.get(i) >= 0) view.set(i, Tile.HIDDEN);
            }
        }
        return view;
    }

    void validate(Game game) {
        if (builtWalls < 0 || builtWalls >= 1 << game.rules.players() || packets < 0 || packets > 4 * game.rules.players())
            throw new IllegalStateException("Invalid manual handling state");
        if (active(game.phase) && !game.manual) throw new IllegalStateException("Automatic table in manual phase");
        if (game.phase == Game.Phase.SHUFFLE && game.wall != null) throw new IllegalStateException("Unshuffled wall already exists");
        if ((game.phase == Game.Phase.BUILD_WALL || game.phase == Game.Phase.DEAL || game.phase == Game.Phase.DRAW) && game.wall == null)
            throw new IllegalStateException("Manual wall missing");
    }
}

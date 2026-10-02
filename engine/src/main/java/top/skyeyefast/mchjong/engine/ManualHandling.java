package top.skyeyefast.mchjong.engine;

import java.util.List;
import static top.skyeyefast.mchjong.engine.RiichiAction.Type.*;

/** Physical handling only; the existing engine still judges claims and settles points. */
final class ManualHandling {
    int builtWalls;
    int packets;
    boolean replacement;
    boolean kan;
    boolean diceHeld;

    record Saved(int builtWalls, int packets, boolean replacement, boolean kan, boolean diceHeld) {}

    Saved save() { return new Saved(builtWalls, packets, replacement, kan, diceHeld); }

    static ManualHandling restore(Saved saved) {
        var handling = new ManualHandling();
        handling.builtWalls = saved.builtWalls();
        handling.packets = saved.packets();
        handling.replacement = saved.replacement();
        handling.kan = saved.kan();
        handling.diceHeld = saved.diceHeld();
        return handling;
    }

    static boolean active(RiichiGame.Phase phase) {
        return phase == RiichiGame.Phase.SHUFFLE || phase == RiichiGame.Phase.BUILD_WALL
            || phase == RiichiGame.Phase.DEAL || phase == RiichiGame.Phase.DRAW;
    }

    void begin(RiichiGame game) {
        builtWalls = packets = 0;
        replacement = kan = false;
        diceHeld = false;
        game.wall = null;
        game.turn = game.dealer;
        game.newDecision(RiichiGame.Phase.SHUFFLE);
    }

    List<RiichiAction> actions(RiichiGame game, int seat) {
        return switch (game.phase) {
            case SHUFFLE -> seat == game.dealer ? List.of(new RiichiAction(SHUFFLE)) : List.of();
            case BUILD_WALL -> builtWalls == (1 << game.rules.players()) - 1
                ? seat == game.dealer ? List.of(new RiichiAction(diceHeld ? ROLL_DICE : PICK_UP_DICE)) : List.of()
                : (builtWalls & 1 << seat) == 0 ? List.of(new RiichiAction(BUILD_WALL)) : List.of();
            case DEAL -> seat == game.turn ? List.of(new RiichiAction(TAKE_PACKET)) : List.of();
            case DRAW -> seat == game.turn ? List.of(new RiichiAction(DRAW)) : List.of();
            default -> List.of();
        };
    }

    RiichiView.Handling view(RiichiGame game) {
        int count = game.phase == RiichiGame.Phase.DEAL ? packetSize(game) : game.phase == RiichiGame.Phase.DRAW ? 1 : 0;
        int source = count == 0 ? -1 : game.phase == RiichiGame.Phase.DRAW && replacement
            ? game.wall.nextReplacementSlot() : game.wall.cursor;
        return new RiichiView.Handling(builtWalls, source, count,
            game.wall == null ? 0 : game.wall.diceOne, game.wall == null ? 0 : game.wall.diceTwo, diceHeld);
    }

    private int packetSize(RiichiGame game) { return packets < 3 * game.rules.players() ? 4 : 1; }

    void act(RiichiGame game, int seat, RiichiAction action) {
        switch (action.type()) {
            case SHUFFLE -> {
                game.createWall();
                game.newDecision(RiichiGame.Phase.BUILD_WALL);
            }
            case BUILD_WALL -> {
                builtWalls |= 1 << seat;
                if (builtWalls == (1 << game.rules.players()) - 1) game.newDecision(RiichiGame.Phase.BUILD_WALL);
                // Each seat builds its own wall independently. Keep other players' held drags valid.
                else game.session.revision++;
            }
            case ROLL_DICE -> {
                game.wall.open(game.rules, game.dealer);
                diceHeld = false;
                game.newDecision(RiichiGame.Phase.DEAL);
            }
            case PICK_UP_DICE -> {
                diceHeld = true;
                game.newDecision(RiichiGame.Phase.BUILD_WALL);
            }
            case TAKE_PACKET -> {
                int count = packetSize(game);
                for (int i = 0; i < count; i++) game.players[seat].hand.add(game.wall.draw());
                packets++;
                if (packets == 4 * game.rules.players()) {
                    game.recorder = game.replay == null ? null : new ReplayRecorder(game);
                    game.draw(game.dealer, false, false);
                } else {
                    game.turn = game.next(seat);
                    game.newDecision(RiichiGame.Phase.DEAL);
                }
            }
            case DRAW -> game.drawNow(seat, replacement, kan);
            default -> throw new IllegalStateException("Not a physical handling action");
        }
    }

    List<Integer> wallView(RiichiGame game, boolean ura) {
        var view = game.wall.publicTiles(ura);
        if (game.phase == RiichiGame.Phase.BUILD_WALL || game.phase == RiichiGame.Phase.DEAL) {
            for (int i = 0; i < view.size(); i++) {
                int side = WallLayout.side(i, game.wall.breakOffset, view.size(), game.rules.players());
                if (game.phase == RiichiGame.Phase.BUILD_WALL && (builtWalls & 1 << side) == 0) view.set(i, Tile.ABSENT);
                else if (view.get(i) >= 0) view.set(i, Tile.HIDDEN);
            }
        }
        return view;
    }

    void validate(RiichiGame game) {
        if (builtWalls < 0 || builtWalls >= 1 << game.rules.players() || packets < 0 || packets > 4 * game.rules.players())
            throw new IllegalStateException("Invalid manual handling state");
        if (active(game.phase) && !game.manual) throw new IllegalStateException("Automatic table in manual phase");
        if (game.manual && game.wall != null && (game.wall.diceOne < 0 || game.wall.diceOne > 6
            || game.wall.diceTwo < 0 || game.wall.diceTwo > 6
            || (game.wall.diceOne == 0) != (game.wall.diceTwo == 0)
            || game.phase == RiichiGame.Phase.BUILD_WALL && game.wall.diceOne != 0
            || game.phase != RiichiGame.Phase.BUILD_WALL && game.wall.diceOne == 0))
            throw new IllegalStateException("Invalid wall opening dice");
        if (game.phase == RiichiGame.Phase.SHUFFLE && game.wall != null) throw new IllegalStateException("Unshuffled wall already exists");
        if ((game.phase == RiichiGame.Phase.BUILD_WALL || game.phase == RiichiGame.Phase.DEAL || game.phase == RiichiGame.Phase.DRAW) && game.wall == null)
            throw new IllegalStateException("Manual wall missing");
    }
}

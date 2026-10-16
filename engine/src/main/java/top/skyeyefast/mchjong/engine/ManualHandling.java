package top.skyeyefast.mchjong.engine;

import java.util.List;

/** Physical handling only; the existing engine still judges claims and settles points. */
final class ManualHandling {
    enum Stage { SHUFFLE, BUILD_WALL, DEAL, DRAW, NONE }
    enum Action { SHUFFLE, BUILD_WALL, PICK_UP_DICE, ROLL_DICE, TAKE_PACKET, DRAW }

    interface Host {
        Stage stage();
        int seats();
        int dealer();
        int turn();
        void phase(Stage stage);
        void shuffled();
        void opened();
        void packet(int seat, int count);
        void dealt();
        void draw();
        void next(int seat);
        void revised();
    }
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

    void reset() {
        builtWalls = packets = 0;
        replacement = kan = diceHeld = false;
    }

    List<Action> actions(Host game, int seat) {
        return switch (game.stage()) {
            case SHUFFLE -> seat == game.dealer() ? List.of(Action.SHUFFLE) : List.of();
            case BUILD_WALL -> builtWalls == (1 << game.seats()) - 1
                ? seat == game.dealer() ? List.of(diceHeld ? Action.ROLL_DICE : Action.PICK_UP_DICE) : List.of()
                : (builtWalls & 1 << seat) == 0 ? List.of(Action.BUILD_WALL) : List.of();
            case DEAL -> seat == game.turn() ? List.of(Action.TAKE_PACKET) : List.of();
            case DRAW -> seat == game.turn() ? List.of(Action.DRAW) : List.of();
            case NONE -> List.of();
        };
    }

    int packetSize(int seats) { return packets < 3 * seats ? 4 : 1; }

    void act(Host game, int seat, Action action) {
        switch (action) {
            case SHUFFLE -> { game.shuffled(); game.phase(Stage.BUILD_WALL); }
            case BUILD_WALL -> {
                builtWalls |= 1 << seat;
                // Independent wall builders retain their current drag tokens.
                if (builtWalls == (1 << game.seats()) - 1) game.phase(Stage.BUILD_WALL);
                else game.revised();
            }
            case PICK_UP_DICE -> { diceHeld = true; game.phase(Stage.BUILD_WALL); }
            case ROLL_DICE -> { game.opened(); diceHeld = false; game.phase(Stage.DEAL); }
            case TAKE_PACKET -> {
                game.packet(seat, packetSize(game.seats()));
                if (++packets == 4 * game.seats()) game.dealt();
                else { game.next(seat); game.phase(Stage.DEAL); }
            }
            case DRAW -> game.draw();
        }
    }

    static boolean active(RiichiGame.Phase phase) {
        return phase == RiichiGame.Phase.SHUFFLE || phase == RiichiGame.Phase.BUILD_WALL
            || phase == RiichiGame.Phase.DEAL || phase == RiichiGame.Phase.DRAW;
    }

    void begin(RiichiGame game) {
        reset();
        game.wall = null;
        game.turn = game.dealer;
        game.newDecision(RiichiGame.Phase.SHUFFLE);
    }

    List<RiichiAction> actions(RiichiGame game, int seat) {
        return actions(host(game), seat).stream().map(action -> new RiichiAction(RiichiAction.Type.valueOf(action.name()))).toList();
    }

    ManualHandlingView view(RiichiGame game) {
        int count = game.phase == RiichiGame.Phase.DEAL ? packetSize(game) : game.phase == RiichiGame.Phase.DRAW ? 1 : 0;
        int source = count == 0 ? -1 : game.phase == RiichiGame.Phase.DRAW && replacement
            ? game.wall.nextReplacementSlot() : game.wall.cursor;
        return new ManualHandlingView(builtWalls, source, count,
            game.wall == null ? 0 : game.wall.diceOne, game.wall == null ? 0 : game.wall.diceTwo, diceHeld);
    }

    private int packetSize(RiichiGame game) { return packetSize(game.rules.players()); }

    void act(RiichiGame game, int seat, RiichiAction action) {
        act(host(game), seat, Action.valueOf(action.type().name()));
    }

    private Host host(RiichiGame game) {
        return new Host() {
            public Stage stage() { return active(game.phase) ? Stage.valueOf(game.phase.name()) : Stage.NONE; }
            public int seats() { return game.rules.players(); }
            public int dealer() { return game.dealer; }
            public int turn() { return game.turn; }
            public void phase(Stage stage) { game.newDecision(RiichiGame.Phase.valueOf(stage.name())); }
            public void shuffled() { game.createWall(); }
            public void opened() { game.wall.open(game.rules, game.dealer); }
            public void packet(int seat, int count) {
                for (int i = 0; i < count; i++) game.players[seat].hand.add(game.wall.draw());
            }
            public void dealt() {
                game.recorder = game.replay == null ? null : new ReplayRecorder(game);
                game.draw(game.dealer, false, false);
            }
            public void draw() { game.drawNow(game.turn, replacement, kan); }
            public void next(int seat) { game.turn = game.next(seat); }
            public void revised() { game.session.revision++; }
        };
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

package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record SichuanView(long revision, long decision, SichuanRules rules, SichuanGame.Phase phase,
                          int viewerSeat, int turn, Wall wall, List<Seat> seats, int focus, int supplier,
                          boolean robbingKong, boolean submitted, List<SichuanAction> actions,
                          List<Winner> winners, List<SichuanSettlement.Entry> ledger, SichuanSettlement.Result result) {
    public SichuanView {
        Objects.requireNonNull(rules); Objects.requireNonNull(phase); Objects.requireNonNull(wall);
        seats = List.copyOf(seats); actions = List.copyOf(actions); winners = List.copyOf(winners); ledger = List.copyOf(ledger);
        if (revision < 1 || decision < 1 || viewerSeat < -1 || viewerSeat > 3 || turn < 0 || turn > 3 || seats.size() != 4
            || viewerSeat < 0 && (!actions.isEmpty() || submitted) || (phase == SichuanGame.Phase.HAND_END) != (result != null))
            throw new IllegalArgumentException("Invalid Sichuan view");
        boolean ended = phase == SichuanGame.Phase.HAND_END;
        for (int seat = 0; seat < 4; seat++) {
            var player = seats.get(seat);
            if (!ended && seat != viewerSeat && (player.hand().stream().anyMatch(tile -> tile != Tile.HIDDEN)
                || player.drawn() != Tile.ABSENT && player.drawn() != Tile.HIDDEN
                || phase == SichuanGame.Phase.VOIDING && player.voidSuit() != -1))
                throw new IllegalArgumentException("Concealed Sichuan data in view");
            if (!ended && seat != viewerSeat && player.melds().stream().anyMatch(meld -> meld.closed()
                && (meld.tiles().size() != 4 || meld.tiles().get(0) != Tile.HIDDEN || meld.tiles().get(3) != Tile.HIDDEN)))
                throw new IllegalArgumentException("Concealed Sichuan kong in view");
        }
        if (!ended && winners.stream().anyMatch(win -> win.selfDraw() && win.tile() != Tile.HIDDEN))
            throw new IllegalArgumentException("Concealed Sichuan winning tile in view");
    }

    static SichuanView project(SichuanGame game, int viewer, boolean interactive) {
        if (viewer < -1 || viewer > 3) throw new IllegalArgumentException("Invalid Sichuan viewer");
        var state = game.save();
        boolean ended = state.phase() == SichuanGame.Phase.HAND_END;
        var seats = new ArrayList<Seat>();
        for (int seat = 0; seat < 4; seat++) {
            var player = state.players().get(seat);
            boolean privateHand = seat == viewer || ended;
            var melds = new ArrayList<Meld>();
            for (var meld : player.melds()) {
                var tiles = new ArrayList<>(meld.tiles());
                if (meld.closed() && !privateHand) { tiles.set(0, Tile.HIDDEN); tiles.set(3, Tile.HIDDEN); }
                melds.add(new Meld(meld.type(), tiles, meld.fromSeat(), meld.calledTile()));
            }
            seats.add(new Seat(privateHand ? player.hand() : java.util.Collections.nCopies(player.hand().size(), Tile.HIDDEN),
                melds, player.river(), state.phase() == SichuanGame.Phase.VOIDING && seat != viewer ? -1 : player.voidSuit(),
                player.won(), privateHand || player.drawn() == Tile.ABSENT ? player.drawn() : Tile.HIDDEN));
        }
        var wall = state.wall();
        var winners = state.wins().stream().map(win -> new Winner(win.seat(), win.supplier(),
            win.selfDraw() && !ended ? Tile.HIDDEN : win.tile(), win.selfDraw(), win.robbingKong(), win.score())).toList();
        return new SichuanView(state.revision(), state.decision(), state.rules(), state.phase(), viewer, state.turn(),
            new Wall(wall.slots().stream().map(tile -> tile == Tile.ABSENT ? Tile.ABSENT : Tile.HIDDEN).toList(),
                wall.dealer(), wall.die1(), wall.die2()), seats, state.focus(), state.supplier(), state.pendingKong() >= 0,
            viewer >= 0 && state.responses().stream().anyMatch(response -> response.seat() == viewer),
            interactive ? game.actions(viewer) : List.of(), winners, state.ledger(), state.result());
    }
    public record Wall(List<Integer> slots, int dealer, int die1, int die2) {
        public Wall {
            slots = List.copyOf(slots);
            if (slots.size() != 108 || slots.stream().anyMatch(tile -> tile != Tile.HIDDEN && tile != Tile.ABSENT))
                throw new IllegalArgumentException("Invalid Sichuan public wall");
            SichuanWallLayout.traversal(dealer, die1, die2);
        }
        public int remaining() { return (int) slots.stream().filter(tile -> tile == Tile.HIDDEN).count(); }
    }
    public record Seat(List<Integer> hand, List<Meld> melds, List<SichuanPlayerState.Discard> river,
                       int voidSuit, boolean won, int drawn) {
        public Seat {
            hand = List.copyOf(hand); melds = List.copyOf(melds); river = List.copyOf(river);
            if (voidSuit < -1 || voidSuit > 2 || hand.stream().anyMatch(tile -> tile < Tile.HIDDEN || tile >= 108)
                || drawn < Tile.ABSENT || drawn >= 108) throw new IllegalArgumentException("Invalid Sichuan public seat");
        }
    }
    public record Winner(int seat, int supplier, int tile, boolean selfDraw, boolean robbingKong, SichuanSettlement.Score score) {
        public Winner {
            Objects.requireNonNull(score);
            if (seat < 0 || seat > 3 || supplier < 0 || supplier > 3 || tile < Tile.HIDDEN || tile >= 108
                || selfDraw != (seat == supplier)) throw new IllegalArgumentException("Invalid Sichuan public winner");
        }
    }
}

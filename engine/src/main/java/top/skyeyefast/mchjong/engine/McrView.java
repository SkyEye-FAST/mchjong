package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Recipient-safe MCR data. A viewer of -1 is an unprivileged spectator, never a player seat. */
public record McrView(long revision, long decision, int handNumber, McrGame.Phase phase,
                      int viewerSeat, int dealer, int roundWind, int turn, int remaining,
                      List<Integer> wall, Focus focus, List<Seat> seats, List<McrAction> actions,
                      boolean responded, McrSettlement.Result result, List<McrSettlement.Penalty> penalties) {
    public McrView {
        Objects.requireNonNull(phase);
        wall = List.copyOf(wall);
        seats = List.copyOf(seats);
        actions = List.copyOf(actions);
        penalties = List.copyOf(penalties);
        if (revision < 1 || decision < 1 || handNumber < 1 || handNumber > 16 || viewerSeat < -1 || viewerSeat > 3
            || dealer != (handNumber - 1) % 4 || roundWind != Tile.EAST + (handNumber - 1) / 4
            || turn < 0 || turn > 3 || remaining < 0 || remaining > 91
            || seats.size() != 4 || wall.size() != 144 || actions.size() > 32 || penalties.size() > 64)
            throw new IllegalArgumentException("Invalid MCR view bounds");
        if (wall.stream().anyMatch(tile -> tile != Tile.HIDDEN && tile != Tile.ABSENT)
            || wall.stream().filter(tile -> tile == Tile.HIDDEN).count() != remaining)
            throw new IllegalArgumentException("MCR views must not expose wall identities");
        if (viewerSeat == -1 && (!actions.isEmpty() || responded) || responded && !actions.isEmpty())
            throw new IllegalArgumentException("Actions do not belong to this recipient");
        boolean ended = phase == McrGame.Phase.HAND_END || phase == McrGame.Phase.MATCH_END;
        if (ended != (result != null) || (phase == McrGame.Phase.REACTION) != (focus != null)
            || focus != null && focus.seat() != turn
            || ended && ((phase == McrGame.Phase.MATCH_END) != (handNumber == 16) || !actions.isEmpty() || responded)
            || phase != McrGame.Phase.REACTION && responded
            || (phase == McrGame.Phase.TURN || phase == McrGame.Phase.DRAW) && viewerSeat != turn && !actions.isEmpty()
            || phase == McrGame.Phase.REACTION && viewerSeat == turn && (!actions.isEmpty() || responded))
            throw new IllegalArgumentException("Inconsistent MCR view phase");
        int winner = result instanceof McrSettlement.Win win ? win.winner() : -1;
        if (winner > 3 || result instanceof McrSettlement.Win && winner < 0)
            throw new IllegalArgumentException("Invalid MCR winner");
        for (int seat = 0; seat < 4; seat++) {
            var player = seats.get(seat);
            if (player.wind() != Tile.EAST + Math.floorMod(seat - dealer, 4))
                throw new IllegalArgumentException("MCR view winds disagree with the dealer");
            boolean visible = seat == viewerSeat || seat == winner;
            if (visible ? player.hand().contains(Tile.HIDDEN) || player.drawn() == Tile.HIDDEN
                : player.hand().stream().anyMatch(tile -> tile != Tile.HIDDEN) || player.drawn() >= 0)
                throw new IllegalArgumentException("MCR concealed hand escaped recipient filtering");
            for (var meld : player.melds()) {
                boolean hidden = meld.closed() && !visible;
                if (meld.tiles().stream().anyMatch(tile -> hidden ? tile != Tile.HIDDEN : !ordinary(tile)))
                    throw new IllegalArgumentException("Invalid MCR meld visibility");
            }
        }
    }

    public record Seat(int wind, int points, List<Integer> hand, int drawn, List<Meld> melds,
                       List<Discard> river, List<Integer> flowers, boolean winForbidden) {
        public Seat {
            hand = List.copyOf(hand);
            melds = List.copyOf(melds);
            river = List.copyOf(river);
            flowers = List.copyOf(flowers);
            if (wind < Tile.EAST || wind > Tile.NORTH || hand.size() > 14 || melds.size() > 4
                || river.size() > 136 || flowers.size() > 8
                || hand.stream().anyMatch(tile -> tile != Tile.HIDDEN && !ordinary(tile))
                || drawn != Tile.ABSENT && drawn != Tile.HIDDEN && !ordinary(drawn)
                || flowers.stream().anyMatch(tile -> !Tile.isFlower(tile))
                || river.stream().anyMatch(discard -> !ordinary(discard.tile()) || discard.riichi()))
                throw new IllegalArgumentException("Invalid MCR seat view");
            for (var meld : melds) {
                boolean kong = switch (meld.type()) {
                    case SEQUENCE, TRIPLET -> false;
                    case OPEN_QUAD, CONCEALED_QUAD, ADDED_QUAD -> true;
                };
                if (meld.tiles().size() != (kong ? 4 : 3) || meld.fromSeat() < 0 || meld.fromSeat() > 3
                    || (meld.closed() ? meld.calledTile() != Tile.ABSENT : !meld.tiles().contains(meld.calledTile())))
                    throw new IllegalArgumentException("Invalid MCR meld view");
            }
        }
    }

    /** A publicly offered discard or added-kong tile, not an opponent's submitted response. */
    public record Focus(int seat, int tile, boolean addedKong) {
        public Focus {
            if (seat < 0 || seat > 3 || !ordinary(tile)) throw new IllegalArgumentException("Invalid MCR focus");
        }
    }

    static McrView project(McrGame game, int viewerSeat) {
        return project(game, viewerSeat, true);
    }

    static McrView project(McrGame game, int viewerSeat, boolean allowActions) {
        if (viewerSeat < -1 || viewerSeat > 3) throw new IllegalArgumentException("Invalid MCR viewer seat");
        int winner = game.result() instanceof McrSettlement.Win win ? win.winner() : -1;
        var seats = new ArrayList<Seat>(4);
        for (int seat = 0; seat < 4; seat++) {
            boolean visible = seat == viewerSeat || seat == winner;
            var hand = game.hand(seat);
            var melds = new ArrayList<Meld>();
            for (var meld : game.melds(seat)) melds.add(meld.closed() && !visible
                ? new Meld(meld.type(), Collections.nCopies(4, Tile.HIDDEN), meld.fromSeat(), Tile.ABSENT) : meld);
            int drawn = game.drawn(seat);
            seats.add(new Seat(game.seatWind(seat), game.points(seat), visible ? hand : Collections.nCopies(hand.size(), Tile.HIDDEN),
                visible || drawn == Tile.ABSENT ? drawn : Tile.HIDDEN, melds, game.river(seat), game.flowers(seat), game.winForbidden(seat)));
        }
        Focus focus = game.phase() == McrGame.Phase.REACTION
            ? new Focus(game.claimFrom(), game.claimTile(), game.robbingKong()) : null;
        return new McrView(game.revision(), game.decision(), game.handNumber(), game.phase(), viewerSeat,
            game.dealer(), game.roundWind(), game.turn(), game.remaining(), game.publicWall(), focus, seats,
            viewerSeat == -1 || !allowActions ? List.of() : game.actions(viewerSeat), viewerSeat != -1 && game.responded(viewerSeat),
            game.result(), game.penalties());
    }

    private static boolean ordinary(int tile) { return tile >= 0 && tile < 136; }
}

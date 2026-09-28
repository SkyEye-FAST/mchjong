package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

/** Private save data, never a client view. Only the current format is accepted. */
public record McrGameState(int format, long seed, long revision, long decision, int handNumber,
                           int turn, McrGame.Phase phase, Wall wall, List<Player> players,
                           int claimTile, int claimFrom, Action pendingKong, boolean drawWallLast,
                           McrWinContext.KongWin drawKong, List<Reply> replies,
                           List<McrSettlement.Penalty> penalties, McrSettlement.Result result) {
    public static final int FORMAT = 1;

    public McrGameState {
        if (format != FORMAT) throw new IllegalArgumentException("Unsupported MCR save format: " + format);
        if (revision < 1 || revision == Long.MAX_VALUE || decision < 1 || decision == Long.MAX_VALUE
            || handNumber < 1 || handNumber > 16 || turn < 0 || turn > 3)
            throw new IllegalArgumentException("Invalid MCR save position");
        Objects.requireNonNull(phase);
        Objects.requireNonNull(wall);
        Objects.requireNonNull(drawKong);
        players = List.copyOf(players);
        replies = List.copyOf(replies);
        penalties = List.copyOf(penalties);
        if (players.size() != 4 || replies.size() > 3 || penalties.size() > 64)
            throw new IllegalArgumentException("Invalid MCR save collections");
    }

    /** Taken slots are ABSENT; all remaining slots lie in [head, tail). */
    public record Wall(List<Integer> tiles, int head, int tail) {
        public Wall {
            tiles = List.copyOf(tiles);
            if (tiles.size() != 144 || head < 0 || head > tail || tail > 144)
                throw new IllegalArgumentException("Invalid MCR wall bounds");
            for (int slot = 0; slot < 144; slot++) {
                int tile = tiles.get(slot);
                if (slot >= head && slot < tail ? tile < 0 || tile >= 144 : tile != Tile.ABSENT)
                    throw new IllegalArgumentException("Invalid MCR wall slot: " + slot);
            }
        }
    }

    /** Only MCR's shared physical zones and MCR-specific stop-win state are persisted. */
    public record Player(List<Integer> hand, List<Meld> melds, List<Discard> river, List<Integer> flowers,
                         int drawn, int points, boolean winForbidden) {
        public Player {
            hand = List.copyOf(hand);
            melds = List.copyOf(melds);
            river = List.copyOf(river);
            flowers = List.copyOf(flowers);
            if (hand.size() > 14 || melds.size() > 4 || river.size() > 136 || flowers.size() > 8)
                throw new IllegalArgumentException("Invalid MCR player collections");
        }
    }

    /** Store the chosen action, not an index into a serialized legal-action cache. */
    public record Reply(int seat, Action action) {
        public Reply {
            if (seat < 0 || seat > 3) throw new IllegalArgumentException("Invalid MCR responder");
            Objects.requireNonNull(action);
        }
    }
}

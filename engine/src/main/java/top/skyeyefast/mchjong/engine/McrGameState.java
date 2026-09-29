package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

/** Private save data, never a client view. Only the current format is accepted. */
public record McrGameState(int format, long seed, long revision, long decision, int handNumber,
                           int turn, McrGame.Phase phase, Wall wall, List<Player> players,
                           int claimTile, int claimFrom, McrAction pendingKong, boolean drawWallLast,
                           McrWinContext.KongWin drawKong, List<Reply> replies,
                           List<McrSettlement.Penalty> penalties, McrSettlement.Result result) {
    public static final int FORMAT = 4;

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

    /** Fixed physical slots; traversal cursors skip already-taken upper/lower positions. */
    public record Wall(List<Integer> tiles, McrOpening opening, int front, int back) {
        public Wall {
            tiles = List.copyOf(tiles);
            Objects.requireNonNull(opening);
            if (tiles.size() != McrWallLayout.SLOTS || front < 0 || front > McrWallLayout.SLOTS
                || back < 0 || back > McrWallLayout.SLOTS)
                throw new IllegalArgumentException("Invalid MCR wall bounds");
            var seen = new java.util.HashSet<Integer>();
            for (int slot = 0; slot < McrWallLayout.SLOTS; slot++) {
                int tile = tiles.get(slot);
                if (tile != Tile.ABSENT && (tile < 0 || tile >= 144 || !seen.add(tile))
                    || slot % 2 == 1 && tile == Tile.ABSENT && tiles.get(slot - 1) != Tile.ABSENT)
                    throw new IllegalArgumentException("Invalid MCR wall slot: " + slot);
                if (slot < front && tiles.get(McrWallLayout.drawSlot(opening, slot)) != Tile.ABSENT
                    || slot < back && tiles.get(McrWallLayout.replacementSlot(opening, slot)) != Tile.ABSENT)
                    throw new IllegalArgumentException("MCR wall cursor skips an occupied slot");
            }
            if (front < McrWallLayout.SLOTS && tiles.get(McrWallLayout.drawSlot(opening, front)) == Tile.ABSENT
                || back < McrWallLayout.SLOTS && tiles.get(McrWallLayout.replacementSlot(opening, back)) == Tile.ABSENT)
                throw new IllegalArgumentException("MCR wall cursors must point to the next occupied slots");
        }
    }

    /** Only MCR's shared physical zones and MCR-specific stop-win state are persisted. */
    public record Player(List<Integer> hand, List<Meld> melds, List<McrDiscard> river, List<Integer> flowers,
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
    public record Reply(int seat, McrAction action) {
        public Reply {
            if (seat < 0 || seat > 3) throw new IllegalArgumentException("Invalid MCR responder");
            Objects.requireNonNull(action);
        }
    }
}

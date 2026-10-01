package top.skyeyefast.mchjong.engine;

import java.util.List;

public record SichuanPlayerState(List<Integer> hand, List<Meld> melds, List<Discard> river,
                                 int voidSuit, boolean won, int drawn, int passedFan, int firstDiscard) {
    public SichuanPlayerState {
        hand = List.copyOf(hand);
        melds = List.copyOf(melds);
        river = List.copyOf(river);
        if (voidSuit < -1 || voidSuit > 2 || drawn < Tile.ABSENT || drawn == Tile.HIDDEN
            || drawn >= 108 || passedFan < -1 || passedFan > 16 || firstDiscard < Tile.ABSENT || firstDiscard >= 108)
            throw new IllegalArgumentException("Invalid Sichuan player state");
    }

    public record Discard(int tile, boolean claimed) {
        public Discard {
            if (tile < 0 || tile >= 108) throw new IllegalArgumentException("Invalid Sichuan discard");
        }
    }
}

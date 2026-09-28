package top.skyeyefast.mchjong.engine;

import java.util.List;

/** MCR fan points and qualification, independent of Riichi han/fu and table payments. */
public record McrHandScore(int totalFan, int nonFlowerFan, boolean meetsMinimum, List<Fan> fans) {
    public McrHandScore { fans = List.copyOf(fans); }

    /** id is the library's named fan identifier; points is its awarded subtotal. */
    public record Fan(String id, int count, int points, boolean mixedKongPair) {}
}

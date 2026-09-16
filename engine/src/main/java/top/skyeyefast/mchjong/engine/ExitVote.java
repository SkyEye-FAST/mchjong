package top.skyeyefast.mchjong.engine;

import java.util.List;

/** A table-wide vote, deliberately independent of the current tile decision. */
public record ExitVote(long id, int requester, int ticksLeft, int required, List<Integer> agreed) {
    public static final int DURATION_TICKS = 30 * 20;
    public ExitVote { agreed = List.copyOf(agreed); }
    public int secondsLeft() { return (ticksLeft + 19) / 20; }
}

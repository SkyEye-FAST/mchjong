package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

/** Fixed MCR physical identities for positions, independent of tile identities and rendering units. */
public final class McrWallLayout {
    public static final int SIDES = 4;
    public static final int STACKS_PER_SIDE = 18;
    public static final int STACKS = SIDES * STACKS_PER_SIDE;
    public static final int SLOTS = STACKS * 2;
    public enum Layer { UPPER, LOWER }

    private McrWallLayout() {}

    /** Columns run from the owning player's right to left. Seats run in player turn order. */
    public static int stack(int seat, int column) {
        if (seat < 0 || seat >= SIDES || column < 0 || column >= STACKS_PER_SIDE)
            throw new IllegalArgumentException("Invalid MCR wall position");
        return seat * STACKS_PER_SIDE + column;
    }

    public static int seat(int stack) { checkStack(stack); return stack / STACKS_PER_SIDE; }
    public static int column(int stack) { checkStack(stack); return stack % STACKS_PER_SIDE; }

    public static int slot(int stack, Layer layer) {
        checkStack(stack);
        return stack * 2 + switch (layer) { case UPPER -> 0; case LOWER -> 1; };
    }

    public static int stackOfSlot(int slot) { checkSlot(slot); return slot / 2; }
    public static Layer layer(int slot) { checkSlot(slot); return slot % 2 == 0 ? Layer.UPPER : Layer.LOWER; }

    /** Clockwise traversal is opposite to player turn order; signed steps also locate the tail. */
    public static int advance(int stack, int steps) {
        int local = column(stack) + Math.floorMod(steps, STACKS);
        return stack(Math.floorMod(seat(stack) - local / STACKS_PER_SIDE, SIDES), local % STACKS_PER_SIDE);
    }

    public static int drawSlot(McrOpening opening, int offset) {
        checkSlot(offset);
        return slot(advance(opening.breakStack(), offset / 2), offset % 2 == 0 ? Layer.UPPER : Layer.LOWER);
    }

    /** Tail stacks run backwards, but each stack is still taken upper before lower. */
    public static int replacementSlot(McrOpening opening, int offset) {
        checkSlot(offset);
        return slot(advance(opening.breakStack(), -1 - offset / 2), offset % 2 == 0 ? Layer.UPPER : Layer.LOWER);
    }

    public record Take(int seat, int slot) {}

    /** Three packet rounds, East's first/third upper tiles, then South, West and North. */
    public static List<Take> initialDeal(McrOpening opening) {
        var result = new ArrayList<Take>(53);
        int dealer = opening.dealer();
        for (int packet = 0; packet < 3; packet++) for (int wind = 0; wind < SIDES; wind++)
            for (int tile = 0; tile < 4; tile++)
                result.add(new Take((dealer + wind) % SIDES, drawSlot(opening, packet * 16 + wind * 4 + tile)));
        result.add(new Take(dealer, drawSlot(opening, 48)));
        result.add(new Take(dealer, drawSlot(opening, 52)));
        for (int wind = 1; wind < SIDES; wind++)
            result.add(new Take((dealer + wind) % SIDES, drawSlot(opening, 48 + wind)));
        return List.copyOf(result);
    }

    private static void checkStack(int stack) {
        if (stack < 0 || stack >= STACKS) throw new IllegalArgumentException("Invalid MCR stack");
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("Invalid MCR slot");
    }
}

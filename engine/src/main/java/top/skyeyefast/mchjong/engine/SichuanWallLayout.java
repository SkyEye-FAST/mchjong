package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;

public final class SichuanWallLayout {
    private SichuanWallLayout() {}
    public static int stacks(int seat) { checkSeat(seat); return seat % 2 == 0 ? 14 : 13; }
    public static int slot(int seat, int stack, int layer) {
        checkSeat(seat);
        if (stack < 0 || stack >= stacks(seat) || layer < 0 || layer > 1)
            throw new IllegalArgumentException("Invalid Sichuan wall slot");
        int offset = 0;
        for (int owner = 0; owner < seat; owner++) offset += stacks(owner) * 2;
        return offset + stack * 2 + layer;
    }
    public static List<Integer> traversal(int dealer, int die1, int die2) {
        checkSeat(dealer);
        if (die1 < 1 || die1 > 6 || die2 < 1 || die2 > 6) throw new IllegalArgumentException("Invalid dice");
        int owner = (dealer + die1 + die2 - 1) % 4;
        int first = Math.min(die1, die2);
        var slots = new ArrayList<Integer>(108);
        for (int step = 0; step < 4; step++) {
            int seat = Math.floorMod(owner - step, 4);
            for (int stack = step == 0 ? first : 0; stack < stacks(seat); stack++)
                for (int layer = 0; layer < 2; layer++) slots.add(slot(seat, stack, layer));
        }
        for (int stack = 0; stack < first; stack++)
            for (int layer = 0; layer < 2; layer++) slots.add(slot(owner, stack, layer));
        return List.copyOf(slots);
    }
    private static void checkSeat(int seat) {
        if (seat < 0 || seat > 3) throw new IllegalArgumentException("Invalid Sichuan seat");
    }
}

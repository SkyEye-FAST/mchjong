package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

public final class SichuanWall {
    private final ArrayList<Integer> slots;
    private final List<Integer> traversal;
    private final int dealer;
    private final int die1;
    private final int die2;
    private int cursor;

    public SichuanWall(long seed, int dealer, List<Integer> stock) {
        if (!Tile.validSichuanSet(stock)) throw new IllegalArgumentException("Sichuan needs 108 suited tiles");
        this.dealer = dealer;
        slots = new ArrayList<>(stock);
        var random = new Random(seed);
        Collections.shuffle(slots, random);
        die1 = random.nextInt(6) + 1;
        die2 = random.nextInt(6) + 1;
        traversal = SichuanWallLayout.traversal(dealer, die1, die2);
    }
    private SichuanWall(State state) {
        slots = new ArrayList<>(state.slots());
        dealer = state.dealer(); die1 = state.die1(); die2 = state.die2(); cursor = state.cursor();
        traversal = SichuanWallLayout.traversal(dealer, die1, die2);
        if (cursor < 0 || cursor > 108 || slots.size() != 108) throw new IllegalArgumentException("Invalid wall bounds");
        var seen = new HashSet<Integer>();
        for (int index = 0; index < 108; index++) {
            int tile = slots.get(traversal.get(index));
            if (tile != Tile.ABSENT && (tile < 0 || tile >= 108 || !seen.add(tile)))
                throw new IllegalArgumentException("Invalid wall tile");
            if (index < cursor && tile != Tile.ABSENT || index >= cursor && tile == Tile.ABSENT)
                throw new IllegalArgumentException("Invalid wall cursor");
        }
    }
    public static SichuanWall restore(State state) { return new SichuanWall(state); }
    public int remaining() { return (int) slots.stream().filter(tile -> tile != Tile.ABSENT).count(); }
    public int draw() { return cursor == 108 ? Tile.ABSENT : take(cursor); }
    int take(int index) {
        int slot = traversal.get(index);
        int tile = slots.get(slot);
        if (tile == Tile.ABSENT) throw new IllegalStateException("Wall slot already taken");
        slots.set(slot, Tile.ABSENT);
        while (cursor < 108 && slots.get(traversal.get(cursor)) == Tile.ABSENT) cursor++;
        return tile;
    }
    public State save() { return new State(slots, dealer, die1, die2, cursor); }
    public record State(List<Integer> slots, int dealer, int die1, int die2, int cursor) {
        public State { slots = List.copyOf(slots); }
    }
}

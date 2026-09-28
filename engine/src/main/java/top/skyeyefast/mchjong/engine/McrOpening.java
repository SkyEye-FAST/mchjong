package top.skyeyefast.mchjong.engine;

import java.util.Objects;
import java.util.Random;

/** Public two-roll opening. breakStack is the first stack on the draw side of the break. */
public record McrOpening(Roll firstRoll, Roll secondRoll, int secondRoller, int breakStack) {
    public record Roll(int first, int second) {
        public Roll {
            if (first < 1 || first > 6 || second < 1 || second > 6)
                throw new IllegalArgumentException("A die must be in 1..6");
        }
        public int total() { return first + second; }
    }

    public McrOpening {
        Objects.requireNonNull(firstRoll);
        Objects.requireNonNull(secondRoll);
        if (secondRoller < 0 || secondRoller >= 4
            || breakStack != McrWallLayout.advance(McrWallLayout.stack(secondRoller, 0), firstRoll.total() + secondRoll.total()))
            throw new IllegalArgumentException("Opening break disagrees with the dice");
    }

    public static McrOpening of(int dealer, Roll first, Roll second) {
        if (dealer < 0 || dealer >= 4) throw new IllegalArgumentException("Invalid opening dealer");
        int roller = (dealer + first.total() - 1) % 4;
        return new McrOpening(first, second, roller,
            McrWallLayout.advance(McrWallLayout.stack(roller, 0), first.total() + second.total()));
    }

    static McrOpening roll(Random random, int dealer) {
        return of(dealer, new Roll(random.nextInt(6) + 1, random.nextInt(6) + 1),
            new Roll(random.nextInt(6) + 1, random.nextInt(6) + 1));
    }

    public int dealer() { return Math.floorMod(secondRoller - firstRoll.total() + 1, 4); }
    public int total() { return firstRoll.total() + secondRoll.total(); }
}

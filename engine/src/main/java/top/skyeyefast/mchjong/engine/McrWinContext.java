package top.skyeyefast.mchjong.engine;

import java.util.Objects;

/** Server-derived winning facts. Winds use Tile.EAST/SOUTH/WEST/NORTH kind constants. */
public record McrWinContext(Method method, int seatWind, int roundWind, boolean wallLast,
                            KongWin kongWin, boolean lastCopy, int flowerCount) {
    public enum Method { SELF_DRAW, DISCARD }
    /** Flower replacement alone is NONE, even though it also draws from the wall tail. */
    public enum KongWin { NONE, REPLACEMENT, ROBBED }

    public McrWinContext {
        Objects.requireNonNull(method);
        Objects.requireNonNull(kongWin);
        if (seatWind < Tile.EAST || seatWind > Tile.NORTH || roundWind < Tile.EAST || roundWind > Tile.NORTH)
            throw new IllegalArgumentException("MCR winds must be ordinary wind kinds");
        if (flowerCount < 0 || flowerCount > 8) throw new IllegalArgumentException("Flower count must be in 0..8");
        if (kongWin == KongWin.REPLACEMENT && method != Method.SELF_DRAW
            || kongWin == KongWin.ROBBED && method != Method.DISCARD)
            throw new IllegalArgumentException("The kong event must match the winning method");
    }
}

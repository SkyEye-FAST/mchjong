package top.skyeyefast.mchjong.engine;

import java.util.Set;

/** Library-derived shape information. Availability is counted separately from public tiles. */
public record TileEfficiency(int shanten, Set<Integer> improving, Set<Integer> goodShape) {
    public TileEfficiency {
        improving = Set.copyOf(improving);
        goodShape = Set.copyOf(goodShape);
    }
}

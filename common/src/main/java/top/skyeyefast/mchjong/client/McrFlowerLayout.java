package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import top.skyeyefast.mchjong.engine.Tile;

/** A single public flower run alongside the melds and standing hand. */
public final class McrFlowerLayout {
    public record Part(int tile, int index, double x, double z) {}
    private McrFlowerLayout() {}

    public static List<Part> of(List<Integer> flowers) {
        if (flowers.size() > 8 || flowers.stream().distinct().count() != flowers.size())
            throw new IllegalArgumentException("Invalid flower area");
        var parts = new ArrayList<Part>();
        for (int index = 0; index < flowers.size(); index++) {
            int tile = flowers.get(index);
            if (!Tile.isFlower(tile)) throw new IllegalArgumentException("Only flowers belong in the flower area");
            parts.add(new Part(tile, index, (index + .5) * TileMesh.WIDTH, 0));
        }
        return List.copyOf(parts);
    }
}

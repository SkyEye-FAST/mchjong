package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** MCR's finite, two-ended wall. Logical order starts at the opened wall's draw edge. */
final class McrWall {
    private final List<Integer> tiles;
    private int head;
    private int tail;

    McrWall(long seed) {
        this(Tile.mcrSet());
        Collections.shuffle(tiles, new Random(seed));
    }

    /** A complete, already ordered wall, also useful for deterministic dealing and replay. */
    McrWall(List<Integer> ordered) {
        if (!Tile.validMcrSet(ordered)) throw new IllegalArgumentException("An MCR wall requires one standard 144-tile set");
        tiles = new ArrayList<>(ordered);
        tail = tiles.size();
    }

    int remaining() { return tail - head; }

    /** Private physical slots, including ABSENT for tiles already taken. */
    List<Integer> tiles() { return List.copyOf(tiles); }

    int draw(PlayerState player) { return draw(player, false); }

    int replace(PlayerState player) { return draw(player, true); }

    /**
     * Moves flowers directly to their owner's flower area. The caller receives an ordinary
     * tile to add to the concealed hand, or ABSENT on exhaustion. Collected flowers remain
     * owned even when the wall ends during replacement; no dead wall is reserved.
     */
    private int draw(PlayerState player, boolean fromTail) {
        Objects.requireNonNull(player);
        while (remaining() > 0) {
            int slot = fromTail ? --tail : head++;
            int tile = tiles.set(slot, Tile.ABSENT);
            if (!Tile.isFlower(tile)) return tile;
            player.flowers.add(tile);
            fromTail = true;
        }
        return Tile.ABSENT;
    }

    /** Call after placing the returned ordinary tile in its destination zone. */
    void assertConservation(PlayerState... players) {
        var seen = new HashSet<Integer>();
        for (int tile : tiles) {
            if (tile != Tile.ABSENT && !seen.add(tile)) throw new IllegalStateException("Duplicated wall tile: " + tile);
        }
        for (var player : players) {
            if (player.flowers.stream().anyMatch(tile -> !Tile.isFlower(tile)))
                throw new IllegalStateException("Only flowers belong in the flower area");
            if (player.hand.stream().anyMatch(Tile::isFlower)
                || player.melds.stream().flatMap(meld -> meld.tiles().stream()).anyMatch(Tile::isFlower)
                || player.river.stream().anyMatch(discard -> Tile.isFlower(discard.tile()))
                || player.norths.stream().anyMatch(Tile::isFlower))
                throw new IllegalStateException("Flowers belong only in the flower area");
            for (int tile : player.physicalTiles()) {
                if (!seen.add(tile)) throw new IllegalStateException("Duplicated physical tile: " + tile);
            }
        }
        if (!seen.equals(new HashSet<>(Tile.mcrSet()))) throw new IllegalStateException("MCR tile conservation failed");
    }
}

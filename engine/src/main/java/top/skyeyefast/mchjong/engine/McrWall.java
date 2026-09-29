package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/** One continuous physical MCR wall, traversed from both sides of its opening. */
final class McrWall {
    private final List<Integer> tiles;
    private final McrOpening opening;
    private int front;
    private int back;
    private int remaining;

    McrWall(long seed) {
        this(seed, 0, Tile.mcrSet());
    }

    McrWall(long seed, int dealer, List<Integer> stock) {
        if (!Tile.validMcrSet(stock)) throw new IllegalArgumentException("An MCR wall requires one standard 144-tile set");
        tiles = new ArrayList<>(stock);
        var random = new Random(seed);
        Collections.shuffle(tiles, random);
        opening = McrOpening.roll(random, dealer);
        remaining = McrWallLayout.SLOTS;
    }

    /** A complete physical placement and an explicit opening, for deterministic dealing. */
    McrWall(List<Integer> physical, McrOpening opening) {
        if (!Tile.validMcrSet(physical)) throw new IllegalArgumentException("An MCR wall requires one standard 144-tile set");
        tiles = new ArrayList<>(physical);
        this.opening = Objects.requireNonNull(opening);
        remaining = McrWallLayout.SLOTS;
    }

    int remaining() { return remaining; }
    McrOpening opening() { return opening; }
    int nextDrawSlot() { return front == McrWallLayout.SLOTS ? Tile.ABSENT : McrWallLayout.drawSlot(opening, front); }
    int nextReplacementSlot() { return back == McrWallLayout.SLOTS ? Tile.ABSENT : McrWallLayout.replacementSlot(opening, back); }

    private McrWall(McrGameState.Wall state) {
        tiles = new ArrayList<>(state.tiles());
        opening = state.opening();
        front = state.front();
        back = state.back();
        remaining = (int) tiles.stream().filter(tile -> tile != Tile.ABSENT).count();
    }

    static McrWall restore(McrGameState.Wall state) { return new McrWall(state); }

    McrGameState.Wall save() { return new McrGameState.Wall(tiles, opening, front, back); }

    /** Private physical slots, including ABSENT for tiles already taken. */
    List<Integer> tiles() { return List.copyOf(tiles); }

    /** Initial dealing only: take one physical tile without exposing or replacing flowers. */
    int drawRaw() {
        return remaining == 0 ? Tile.ABSENT : takeRaw(nextDrawSlot());
    }

    /** A packet or jump explicitly takes its real source position before flowers are processed. */
    int takeRaw(int slot) {
        var layer = McrWallLayout.layer(slot);
        if (tiles.get(slot) == Tile.ABSENT || layer == McrWallLayout.Layer.LOWER && tiles.get(slot - 1) != Tile.ABSENT)
            throw new IllegalStateException("Cannot take this physical wall slot");
        int tile = tiles.set(slot, Tile.ABSENT);
        remaining--;
        while (front < McrWallLayout.SLOTS && tiles.get(McrWallLayout.drawSlot(opening, front)) == Tile.ABSENT) front++;
        while (back < McrWallLayout.SLOTS && tiles.get(McrWallLayout.replacementSlot(opening, back)) == Tile.ABSENT) back++;
        return tile;
    }

    int draw(McrPlayerState player) { return draw(player, false); }

    int replace(McrPlayerState player) { return draw(player, true); }

    /**
     * Moves flowers directly to their owner's flower area. The caller receives an ordinary
     * tile to add to the concealed hand, or ABSENT on exhaustion. Collected flowers remain
     * owned even when the wall ends during replacement; no dead wall is reserved.
     */
    private int draw(McrPlayerState player, boolean fromTail) {
        Objects.requireNonNull(player);
        while (remaining() > 0) {
            int tile = takeRaw(fromTail ? nextReplacementSlot() : nextDrawSlot());
            if (!Tile.isFlower(tile)) return tile;
            player.flowers.add(tile);
            fromTail = true;
        }
        return Tile.ABSENT;
    }

    /** Call after placing the returned ordinary tile in its destination zone. */
    void assertConservation(McrPlayerState... players) {
        var seen = new HashSet<Integer>();
        for (int tile : tiles) {
            if (tile != Tile.ABSENT && !seen.add(tile)) throw new IllegalStateException("Duplicated wall tile: " + tile);
        }
        for (var player : players) {
            if (player.flowers.stream().anyMatch(tile -> !Tile.isFlower(tile)))
                throw new IllegalStateException("Only flowers belong in the flower area");
            if (player.hand.stream().anyMatch(Tile::isFlower)
                || player.melds.stream().flatMap(meld -> meld.tiles().stream()).anyMatch(Tile::isFlower)
                || player.river.stream().anyMatch(discard -> Tile.isFlower(discard.tile())))
                throw new IllegalStateException("Flowers belong only in the flower area");
            for (int tile : player.physicalTiles()) {
                if (!seen.add(tile)) throw new IllegalStateException("Duplicated physical tile: " + tile);
            }
        }
        if (!seen.equals(new HashSet<>(Tile.mcrSet()))) throw new IllegalStateException("MCR tile conservation failed");
    }
}

package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The 14-tile dead wall is replenished from the back of the live wall after each replacement draw. */
final class Wall {
    List<Integer> tiles;
    List<Integer> replacements = new ArrayList<>();
    List<Integer> dora = new ArrayList<>();
    List<Integer> ura = new ArrayList<>();
    int cursor;
    int liveEnd;
    int replacementIndex;
    int revealed = 1;
    int pendingIndicators;
    int breakOffset;

    Wall(RuleConfig rules, long seed) {
        this(rules, seed, Tile.set(false, rules.defaultRedFives()));
    }

    Wall(RuleConfig rules, long seed, List<Integer> supplied) {
        if (!Tile.validSet(supplied) || !rules.allows(RedFives.of(supplied)))
            throw new IllegalArgumentException("A wall requires one complete supplied set");
        tiles = new ArrayList<>(supplied.stream().filter(tile -> !rules.sanma() || Tile.kind(tile) == 0 || Tile.kind(tile) >= 8).toList());
        var random = new Random(seed);
        Collections.shuffle(tiles, random);
        liveEnd = tiles.size() - 14;
        breakOffset = random.nextInt(tiles.size() / 2) * 2;
        int end = tiles.size();
        // Replacement tiles are paired so the upper tile of each stack is taken first.
        for (int i = 0; i < rules.replacementCapacity(); i++) replacements.add(end - 1 - i % 4);
        for (int i = 0; i < 5; i++) {
            dora.add(end - 5 - i * 2);
            ura.add(end - 6 - i * 2);
        }
        // Additional replacements reuse replenished positions, never indicator slots.
    }

    int remaining() { return Math.max(0, liveEnd - cursor); }
    int draw() {
        if (remaining() == 0) throw new IllegalStateException("The live wall is empty");
        return take(cursor++);
    }

    boolean canReplace() {
        return remaining() > 0 && replacementIndex < replacements.size()
            && replacements.subList(replacementIndex, replacements.size()).stream().anyMatch(this::usableReplacement);
    }

    private boolean usableReplacement(int slot) {
        return tiles.get(slot) >= 0 && !dora.subList(0, revealed).contains(slot)
            && !ura.subList(0, revealed).contains(slot);
    }

    int nextReplacementSlot() {
        return replacements.subList(replacementIndex, replacements.size()).stream()
            .filter(this::usableReplacement).findFirst().orElseThrow(() -> new IllegalStateException("No replacement draw available"));
    }

    int replace() {
        if (!canReplace()) throw new IllegalStateException("No replacement draw available");
        while (!usableReplacement(replacements.get(replacementIndex))) replacementIndex++;
        int slot = replacements.get(replacementIndex++);
        int tile = take(slot);
        // Move the last live tile into the vacated dead-wall position, preserving 14 reserve tiles.
        tiles.set(slot, take(--liveEnd));
        return tile;
    }

    private int take(int index) {
        int tile = tiles.set(index, Tile.ABSENT);
        if (tile < 0) throw new IllegalStateException("A wall tile was drawn twice");
        return tile;
    }

    void reveal() {
        if (revealed >= 5) throw new IllegalStateException("Too many kan indicators");
        revealed++;
    }

    void revealPending() {
        while (pendingIndicators > 0) { reveal(); pendingIndicators--; }
    }

    List<Integer> indicators(boolean includeUra) {
        var result = new ArrayList<Integer>();
        for (int i = 0; i < revealed; i++) {
            result.add(tiles.get(dora.get(i)));
            if (includeUra) result.add(tiles.get(ura.get(i)));
        }
        return result;
    }

    List<Integer> publicTiles(boolean includeUra) {
        var result = new ArrayList<Integer>(Collections.nCopies(tiles.size(), Tile.HIDDEN));
        for (int i = 0; i < tiles.size(); i++) if (tiles.get(i) < 0) result.set(i, Tile.ABSENT);
        for (int i = 0; i < revealed; i++) {
            result.set(dora.get(i), tiles.get(dora.get(i)));
            if (includeUra) result.set(ura.get(i), tiles.get(ura.get(i)));
        }
        return result;
    }
}

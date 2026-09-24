package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** The dead wall grows into the live wall after each replacement draw. */
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
    long openingSeed;
    int diceOne, diceTwo;

    Wall(RuleConfig rules, long seed, int dealer) {
        this(rules, seed, dealer, Tile.set(rules.sanma(), rules.redFives()));
    }

    Wall(RuleConfig rules, long seed, int dealer, List<Integer> supplied) {
        this(rules, seed, dealer, supplied, true);
    }

    Wall(RuleConfig rules, long seed, int dealer, List<Integer> supplied, boolean open) {
        if (!Tile.validSet(supplied) || supplied.size() != (rules.sanma() ? 108 : 136) || !rules.allows(RedFives.of(supplied)))
            throw new IllegalArgumentException("A wall requires one complete supplied set");
        tiles = new ArrayList<>(supplied);
        var random = new Random(seed);
        Collections.shuffle(tiles, random);
        liveEnd = tiles.size() - 14;
        openingSeed = random.nextLong();
        if (open) open(rules, dealer);
        int end = tiles.size();
        // Four replacement tiles sit at the far end. Extra three-player draws use
        // tiles that have since joined the dead wall from the live-wall edge.
        for (int i = 0; i < rules.replacementCapacity(); i++)
            replacements.add(i < 4 ? end - 1 - i : end - 11 - i);
        for (int i = 0; i < 5; i++) {
            dora.add(end - 5 - i * 2);
            ura.add(end - 6 - i * 2);
        }
        // Replacement slots never overlap the indicator stacks.
    }

    int remaining() { return Math.max(0, liveEnd - cursor); }
    void open(RuleConfig rules, int dealer) {
        if (diceOne != 0 || cursor != 0) throw new IllegalStateException("Wall already opened");
        var random = new Random(openingSeed);
        diceOne = random.nextInt(6) + 1;
        diceTwo = random.nextInt(6) + 1;
        breakOffset = WallLayout.breakOffset(dealer, diceOne + diceTwo, tiles.size(), rules.players());
    }
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
        // The last live tile becomes dead in place; the drawn replacement slot stays empty.
        // Moving it into that slot would make the physical wall and the next draw disagree.
        liveEnd--;
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

    static ReplayWall replay(RuleConfig rules, long seed, int dealer, List<Integer> supplied) {
        Wall wall = new Wall(rules, seed, dealer, supplied, true);
        return new ReplayWall(List.copyOf(wall.tiles), wall.breakOffset, List.copyOf(wall.replacements),
            List.copyOf(wall.dora), List.copyOf(wall.ura));
    }
}

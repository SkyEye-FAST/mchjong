package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class WallInvariantTest {
    @Test void openingCountsFromTheSelectedWallsRightAndFollowsEveryDealer() {
        for (RuleSet rules : new RuleSet[]{RuleSet.TENHOU_3, RuleSet.TENHOU_4}) {
            int players = rules.players(), size = rules.sanma() ? 108 : 136;
            int stacks = size / (2 * players);
            var base = new Wall(rules.config(), 123, 0);
            for (int dealer = 0; dealer < players; dealer++) {
                var wall = new Wall(rules.config(), 123, dealer);
                assertEquals(base.tiles, wall.tiles, "Dealer rotation changes geometry, not the shuffled tile order");
                assertEquals(Math.floorMod(base.breakOffset + dealer * stacks * 2, size), wall.breakOffset);
                for (int dice = 2; dice <= 12; dice++) {
                    int side = dealer;
                    for (int pip = 1; pip < dice; pip++) side = (side + 1) % players;
                    int offset = WallLayout.breakOffset(dealer, dice, size, players);
                    int first = WallLayout.stack(0, offset, size);
                    assertEquals(side, WallLayout.side(0, offset, size, players));
                    assertEquals(stacks - dice - 1, first % stacks, "Skip the counted stacks before taking the live wall");
                    assertEquals(first - 1, WallLayout.stack(2, offset, size), "Live draws move toward the owner's left");
                    assertEquals(Math.floorMod(first + 1, size / 2), WallLayout.stack(size - 1, offset, size));
                    assertEquals(Math.floorMod(first + 3, size / 2), WallLayout.stack(size - 5, offset, size),
                        "The first indicator is the third stack to the right of the opening");
                }
            }
        }
    }

    @ParameterizedTest @EnumSource(RuleSet.class)
    void everyPhysicalTileIsAccountedForEvenAfterAllReplacements(RuleSet rules) {
        for (var composition : RedFives.values()) {
            var supplied = Tile.set(rules.sanma(), composition);
            assertTrue(Tile.validSet(supplied));
            if (!rules.allows(composition)) {
                assertThrows(IllegalArgumentException.class, () -> new Wall(rules.config(), 12, 0, supplied));
                continue;
            }
            Wall wall = new Wall(rules.config().with(RuleOption.RED_FIVES, composition.ordinal()), 12, 0, supplied);
            var taken = new HashSet<Integer>();
            for (int i = 0; i < rules.players() * 13 + 1; i++) assertTrue(taken.add(wall.draw()));
            assertEquals(rules.sanma() ? 54 : 69, wall.remaining());
            for (int i = 0; i < rules.replacementCapacity(); i++) {
                assertTrue(wall.canReplace());
                assertTrue(taken.add(wall.replace()));
            }
            assertFalse(wall.canReplace());
            while (wall.remaining() > 0) assertTrue(taken.add(wall.draw()));
            assertEquals(14, wall.tiles.stream().filter(t -> t >= 0).count());
            for (int tile : wall.tiles) if (tile >= 0) assertTrue(taken.add(tile));
            assertEquals(new HashSet<>(Tile.set(rules.sanma(), composition)), taken);
            assertEquals(composition.total() - (rules.sanma() ? composition.count(0) : 0), taken.stream().filter(Tile::red).count());
        }
    }

    @ParameterizedTest @EnumSource(value = RuleSet.class, names = {"TENHOU_4", "TENHOU_3"})
    void replacementSlotsStayEmptyAndTheLastLiveTileStaysAtTheWallEnd(RuleSet rules) {
        Wall wall = new Wall(rules.config(), 211, 0);
        int end = wall.tiles.size();
        for (int i = 0; i < rules.replacementCapacity(); i++) {
            int slot = i < 4 ? end - 1 - i : end - 11 - i;
            assertEquals(slot, wall.nextReplacementSlot());
            int replacement = wall.tiles.get(slot);
            int lastLive = wall.tiles.get(wall.liveEnd - 1);
            int oldLiveEnd = wall.liveEnd;
            assertEquals(replacement, wall.replace());
            assertEquals(Tile.ABSENT, wall.tiles.get(slot), "The drawn rinshan slot must remain vacant");
            assertEquals(lastLive, wall.tiles.get(oldLiveEnd - 1), "The last live tile becomes dead in place");
            assertEquals(oldLiveEnd - 1, wall.liveEnd);
        }
        int haitei = wall.tiles.get(wall.liveEnd - 1);
        while (wall.remaining() > 1) wall.draw();
        assertEquals(haitei, wall.draw());
        assertEquals(0, wall.remaining());
    }

    @ParameterizedTest @EnumSource(value = RuleSet.class, names = {"TENHOU_4", "TENHOU_3"})
    void publicWallOnlyExposesDeclaredIndicators(RuleSet rules) {
        Wall wall = new Wall(rules.config(), 123, 0);
        assertEquals(1, wall.publicTiles(false).stream().filter(t -> t >= 0).count());
        for (int i = 0; i < 4; i++) wall.reveal();
        assertEquals(5, wall.publicTiles(false).stream().filter(t -> t >= 0).count());
        assertEquals(10, wall.publicTiles(true).stream().filter(t -> t >= 0).count());
        assertEquals(rules.sanma() ? 108 : 136, wall.publicTiles(false).size());
        if (rules.sanma()) {
            assertEquals(8, Tile.doraAfter(0, true));
            assertEquals(0, Tile.doraAfter(8, true));
        }
    }
}

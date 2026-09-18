package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class WallInvariantTest {
    @ParameterizedTest @EnumSource(RuleSet.class)
    void everyPhysicalTileIsAccountedForEvenAfterAllReplacements(RuleSet rules) {
        for (var composition : RedFives.values()) {
            var supplied = Tile.set(false, composition);
            assertTrue(Tile.validSet(supplied));
            if (!rules.allows(composition)) {
                assertThrows(IllegalArgumentException.class, () -> new Wall(rules.config(), 12, supplied));
                continue;
            }
            Wall wall = new Wall(rules.config(), 12, supplied);
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

    @ParameterizedTest @EnumSource(RuleSet.class)
    void publicWallOnlyExposesDeclaredIndicators(RuleSet rules) {
        Wall wall = new Wall(rules.config(), 123);
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

package top.skyeyefast.mchjong.engine;

import java.util.HashSet;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class WallInvariantTest {
    @ParameterizedTest @EnumSource(RuleSet.class)
    void everyPhysicalTileIsAccountedForEvenAfterAllReplacements(RuleSet rules) {
        for (int seed = 0; seed < 12; seed++) {
            Wall wall = new Wall(rules, seed);
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
            assertEquals(new HashSet<>(Tile.set(rules.sanma())), taken);
        }
    }

    @ParameterizedTest @EnumSource(RuleSet.class)
    void publicWallOnlyExposesDeclaredIndicators(RuleSet rules) {
        Wall wall = new Wall(rules, 123);
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

package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McrWallTest {
    @Test void standardStockSeparatesEightPhysicalFlowersFromTheOrdinaryKinds() {
        var stock = Tile.mcrSet();
        assertEquals(144, stock.size());
        assertEquals(144, new HashSet<>(stock).size());
        assertEquals(Tile.set(false, RedFives.NONE), stock.stream().filter(tile -> !Tile.isFlower(tile)).toList());
        for (var flower : FlowerTile.values()) {
            assertSame(flower, FlowerTile.of(flower.id()));
            assertEquals(1, Collections.frequency(stock, flower.id()));
            assertThrows(IllegalArgumentException.class, () -> Tile.kind(flower.id()));
        }
        assertNull(FlowerTile.of(Tile.HIDDEN));
        assertFalse(Tile.isFlower(Tile.ABSENT));
        assertTrue(Tile.validMcrSet(stock));
        assertFalse(Tile.validSet(stock));
        assertFalse(Tile.validMcrSet(Tile.set(true)));
        assertThrows(IllegalArgumentException.class, () -> new McrWall(Tile.set(false)));
        var invalid = new ArrayList<>(stock);
        invalid.set(143, invalid.get(142));
        assertFalse(Tile.validMcrSet(invalid));
        invalid = new ArrayList<>(stock);
        invalid.set(16, Tile.id(4, 0, true));
        assertFalse(Tile.validMcrSet(invalid));
    }

    @Test void allZonesConserve144TilesAcrossFrontAndTailDraws() {
        var wall = new McrWall(711);
        var players = new PlayerState[]{new PlayerState(), new PlayerState(), new PlayerState(), new PlayerState()};
        int draws = 0;
        wall.assertConservation(players);
        while (wall.remaining() > 0) {
            var player = players[draws % 4];
            int tile = draws % 2 == 0 ? wall.draw(player) : wall.replace(player);
            if (tile != Tile.ABSENT) {
                assertFalse(Tile.isFlower(tile));
                player.hand.add(tile);
                player.drawn = tile; // Alias, never a second owned copy.
            }
            wall.assertConservation(players);
            draws++;
        }
        assertEquals(136, java.util.Arrays.stream(players).mapToInt(p -> p.hand.size()).sum());
        assertEquals(8, java.util.Arrays.stream(players).mapToInt(p -> p.flowers.size()).sum());
        assertTrue(wall.tiles().stream().allMatch(tile -> tile == Tile.ABSENT));
        var meldTiles = List.of(0, 1, 2);
        for (var player : players) player.hand.removeAll(meldTiles);
        players[0].melds.add(new Meld(Meld.Type.PON, meldTiles, 3, 0));
        players[3].river.add(new Discard(0, false, true, false));
        int discarded = players[1].hand.removeFirst();
        players[1].river.add(new Discard(discarded, false, false, false));
        wall.assertConservation(players); // The called discard aliases the meld, not another owned tile.
        int duplicate = players[0].hand.getFirst();
        players[1].hand.add(duplicate);
        assertThrows(IllegalStateException.class, () -> wall.assertConservation(players));
        players[1].hand.removeLast();
        var flowerOwner = java.util.Arrays.stream(players).filter(p -> !p.flowers.isEmpty()).findFirst().orElseThrow();
        flowerOwner.hand.add(flowerOwner.flowers.removeFirst());
        assertThrows(IllegalStateException.class, () -> wall.assertConservation(players));
    }

    @Test void aFrontFlowerReplacesRepeatedlyFromTheTailWithoutEnteringTheHand() {
        var order = new ArrayList<>(Tile.mcrSet());
        Collections.swap(order, 0, order.indexOf(FlowerTile.SPRING.id()));
        var wall = new McrWall(order);
        var player = new PlayerState();
        int tile = wall.draw(player);
        assertEquals(Tile.id(0, 0, false), tile);
        assertTrue(player.hand.isEmpty());
        assertEquals(List.of(FlowerTile.SPRING.id(), FlowerTile.CHRYSANTHEMUM.id(), FlowerTile.BAMBOO.id(),
            FlowerTile.ORCHID.id(), FlowerTile.PLUM.id(), FlowerTile.WINTER.id(), FlowerTile.AUTUMN.id(),
            FlowerTile.SUMMER.id()), player.flowers);
        assertEquals(135, wall.remaining());
        player.hand.add(tile);
        wall.assertConservation(player);
        int next = wall.draw(player);
        assertEquals(0, Tile.kind(next), "The next front tile is still the second ordinary copy");
        player.hand.add(next);
        wall.assertConservation(player);
    }

    @Test void flowerOnlyExhaustionKeepsOwnershipAndResetClearsTheFlowerArea() {
        var wall = new McrWall(Tile.mcrSet());
        var player = new PlayerState();
        for (int i = 0; i < 136; i++) player.hand.add(wall.draw(player));
        assertEquals(8, wall.remaining());
        assertEquals(Tile.ABSENT, wall.draw(player));
        assertEquals(0, wall.remaining());
        assertEquals(8, player.flowers.size());
        assertEquals(Tile.ABSENT, wall.replace(player));
        wall.assertConservation(player);
        player.resetHand();
        assertTrue(player.flowers.isEmpty());
    }
}

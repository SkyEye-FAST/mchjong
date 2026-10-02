package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.McrGameTest.OPENING;
import static top.skyeyefast.mchjong.engine.McrGameTest.physical;

class McrWallTest {
    @Test void rawDealingDoesNotReplaceOrSkipFlowers() {
        var order = new ArrayList<>(Tile.mcrSet());
        Collections.swap(order, 0, order.indexOf(FlowerTile.SPRING.id()));
        var wall = new McrWall(physical(order), OPENING);
        assertEquals(FlowerTile.SPRING.id(), wall.drawRaw());
        assertEquals(143, wall.remaining());
        assertEquals(order.get(1), wall.drawRaw());
        assertEquals(order.get(143), wall.tiles().get(McrWallLayout.drawSlot(OPENING, 143)), "Raw dealing leaves the tail untouched");
        for (int i = 2; i < 144; i++) assertEquals(order.get(i), wall.drawRaw());
        assertEquals(Tile.ABSENT, wall.drawRaw());
    }

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
        assertThrows(IllegalArgumentException.class, () -> new McrWall(Tile.set(false), OPENING));
        var invalid = new ArrayList<>(stock);
        invalid.set(143, invalid.get(142));
        assertFalse(Tile.validMcrSet(invalid));
        invalid = new ArrayList<>(stock);
        invalid.set(16, Tile.id(4, 0, true));
        assertFalse(Tile.validMcrSet(invalid));
    }

    @Test void allZonesConserve144TilesAcrossFrontAndTailDraws() {
        var wall = new McrWall(711);
        var players = new McrPlayerState[]{new McrPlayerState(), new McrPlayerState(), new McrPlayerState(), new McrPlayerState()};
        int draws = 0;
        wall.assertConservation(players);
        while (wall.remaining() > 0) {
            var player = players[draws % 4];
            int tile = draws % 2 == 0 ? wall.drawRaw() : wall.replaceRaw();
            if (Tile.isFlower(tile)) player.flowers.add(tile);
            else {
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
        players[0].melds.add(new Meld(Meld.Type.TRIPLET, meldTiles, 3, 0));
        players[3].river.add(new McrDiscard(0, true, false));
        int discarded = players[1].hand.removeFirst();
        players[1].river.add(new McrDiscard(discarded, false, false));
        wall.assertConservation(players); // The called discard aliases the meld, not another owned tile.
        int duplicate = players[0].hand.getFirst();
        players[1].hand.add(duplicate);
        assertThrows(IllegalStateException.class, () -> wall.assertConservation(players));
        players[1].hand.removeLast();
        var flowerOwner = java.util.Arrays.stream(players).filter(p -> !p.flowers.isEmpty()).findFirst().orElseThrow();
        flowerOwner.hand.add(flowerOwner.flowers.removeFirst());
        assertThrows(IllegalStateException.class, () -> wall.assertConservation(players));
    }

    @Test void rawTailTakesExactlyOneTileWithoutSkippingFlowers() {
        var order = new ArrayList<>(Tile.mcrSet());
        Collections.swap(order, 0, order.indexOf(FlowerTile.SUMMER.id()));
        var wall = new McrWall(physical(order), OPENING);
        assertEquals(FlowerTile.SUMMER.id(), wall.drawRaw());
        assertEquals(FlowerTile.BAMBOO.id(), wall.replaceRaw());
        assertEquals(142, wall.remaining());
        assertEquals(FlowerTile.CHRYSANTHEMUM.id(), wall.replaceRaw());
        assertEquals(0, Tile.kind(wall.drawRaw()), "The next front tile is still the second ordinary copy");
    }

    @Test void flowerOnlyExhaustionKeepsOwnershipAndResetClearsTheFlowerArea() {
        var wall = new McrWall(physical(Tile.mcrSet()), OPENING);
        var player = new McrPlayerState();
        for (int i = 0; i < 136; i++) player.hand.add(wall.drawRaw());
        assertEquals(8, wall.remaining());
        for (int i = 0; i < 8; i++) player.flowers.add(wall.replaceRaw());
        assertEquals(Tile.ABSENT, wall.drawRaw());
        assertEquals(0, wall.remaining());
        assertEquals(8, player.flowers.size());
        assertEquals(Tile.ABSENT, wall.replaceRaw());
        wall.assertConservation(player);
        player.resetHand();
        assertTrue(player.flowers.isEmpty());
    }
}

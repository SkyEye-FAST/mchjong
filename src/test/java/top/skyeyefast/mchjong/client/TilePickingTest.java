package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class TilePickingTest {
    private static TableAnimation.Frame tile(Vec3 position, float yaw, float pitch) {
        return new TableAnimation.Frame(new TableScene.Piece(0, 0, TableScene.Area.HAND, 0, position, yaw, false, false), pitch);
    }

    @Test void picksAllSeatOrientationsAndAnimatedTilts() {
        for (int seat = 0; seat < 4; seat++) for (float pitch : new float[]{0, -30, -60, -90}) {
            var position = TableGeometry.orient(0, 1, 1.25, seat);
            var origin = TableGeometry.orient(0, 2.25, 3.15, seat);
            assertTrue(Double.isFinite(TilePicking.distanceSquared(tile(position, seat * 90, pitch), origin, position.subtract(origin), false)));
        }
    }

    @Test void doesNotSelectTheGapOrTilesBehindTheCamera() {
        var tile = tile(Vec3.ZERO, 0, 0);
        assertEquals(Double.POSITIVE_INFINITY, TilePicking.distanceSquared(tile, new Vec3(.055, 0, 2), new Vec3(0, 0, -1), false));
        assertEquals(Double.POSITIVE_INFINITY, TilePicking.distanceSquared(tile, new Vec3(0, 0, 2), new Vec3(0, 0, 1), false));
    }

    @Test void tracksRaisedSelectionAndReturnsNearestHitDistance() {
        var tile = tile(Vec3.ZERO, 0, 0);
        var origin = new Vec3(0, .09, 2);
        assertEquals(Double.POSITIVE_INFINITY, TilePicking.distanceSquared(tile, origin, new Vec3(0, 0, -1), false));
        assertTrue(Double.isFinite(TilePicking.distanceSquared(tile, origin, new Vec3(0, 0, -1), true)));
        var farther = tile(new Vec3(0, 0, -1), 0, 0);
        assertTrue(TilePicking.distanceSquared(tile, new Vec3(0, 0, 2), new Vec3(0, 0, -1), false)
            < TilePicking.distanceSquared(farther, new Vec3(0, 0, 2), new Vec3(0, 0, -1), false));
    }

    @Test void nearestSideMatchesSeatsWithoutSelectingAnArbitraryEmptySide() {
        assertEquals(0, TableGeometry.nearestSide(new Vec3(.1, 0, 3)));
        assertEquals(1, TableGeometry.nearestSide(new Vec3(3, 0, .1)));
        assertEquals(2, TableGeometry.nearestSide(new Vec3(-.1, 0, -3)));
        assertEquals(3, TableGeometry.nearestSide(new Vec3(-3, 0, -.1)));
    }
}

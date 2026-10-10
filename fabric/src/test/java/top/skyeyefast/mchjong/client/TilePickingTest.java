package top.skyeyefast.mchjong.client;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class TilePickingTest {
    private static RiichiAnimation.Frame tile(Vec3 position, float yaw, float pitch) {
        return new RiichiAnimation.Frame(new RiichiTableScene.Piece(0, 0, RiichiTableScene.Area.HAND, 0, position, yaw, false, false), pitch);
    }

    @Test void picksAllSeatOrientationsAndAnimatedTilts() {
        var settings = new TableSettings();
        for (int seat = 0; seat < 4; seat++) for (float pitch : new float[]{0, -30, -60, -90}) {
            var position = TableGeometry.orient(0, TableGeometry.FELT_Y + TileDimensions.SMALL.height() / 2, RiichiTableScene.HAND_Z, seat);
            var origin = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, seat);
            assertTrue(Double.isFinite(TilePicking.distanceSquared(tile(position, seat * 90, pitch), origin, position.subtract(origin), false)));
        }
    }

    @Test void rightCornerTilesArePickableFromEachSeatedCameraIncludingSidewaysAndStackedKans() {
        var settings = new TableSettings();
        for (int seat = 0; seat < 4; seat++) for (boolean sideways : new boolean[]{false, true})
            for (boolean stacked : new boolean[]{false, true}) {
                double width = (sideways ? TileDimensions.SMALL.height() : TileDimensions.SMALL.width());
                var position = TableGeometry.orient(RiichiTableScene.MELD_RIGHT - width / 2,
                    TableGeometry.FELT_Y + TileDimensions.SMALL.depth() * (stacked ? 1.5 : .5), RiichiTableScene.HAND_Z, seat);
                var piece = new RiichiTableScene.Piece(0, seat, RiichiTableScene.Area.MELD, 0, position,
                    seat * 90 + (sideways ? 90 : 0), true, false);
                var frame = new RiichiAnimation.Frame(piece, -90);
                var origin = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, seat);
                assertTrue(Double.isFinite(TilePicking.distanceSquared(frame, origin, position.subtract(origin), false)), piece.toString());
            }
    }

    @Test void doesNotSelectTheGapOrTilesBehindTheCamera() {
        var tile = tile(Vec3.ZERO, 0, 0);
        assertEquals(Double.POSITIVE_INFINITY, TilePicking.distanceSquared(tile, new Vec3(.055, 0, 2), new Vec3(0, 0, -1), false));
        assertEquals(Double.POSITIVE_INFINITY, TilePicking.distanceSquared(tile, new Vec3(0, 0, 2), new Vec3(0, 0, 1), false));
    }

    @Test void projectedWorldTileReturnsTheSamePickingRay() {
        var origin = new Vec3(.2, 2.1, 2);
        var forward = new Vec3(-.1, -.65, -1).normalize();
        var right = new Vec3(-forward.z, 0, forward.x).normalize();
        var projection = new SeatedTableProjection(origin, forward, right, 350, 640, 400);
        var position = new Vec3(.4, TableGeometry.FELT_Y, .5);
        var point = projection.project(position, .05);
        assertNotNull(point);
        var pointer = projection.pointer(point.x(), point.y());
        assertEquals(origin, pointer.origin());
        assertTrue(pointer.ray().normalize().distanceTo(position.subtract(origin).normalize()) < 1e-9);
        assertTrue(Double.isFinite(TilePicking.distanceSquared(tile(position, 0, 0),
            pointer.origin(), pointer.ray(), false)));
        assertNull(projection.project(origin.subtract(forward), .05));
    }

    @Test void pannedCamerasPickFarPublicFacesFromEverySeat() {
        var camera = new SeatedCameraState(2, 2.2);
        camera.pan(.2, -.15);
        camera.scroll(1);
            for (int seat = 0; seat < 4; seat++) for (RiichiTableScene.Area area :
                new RiichiTableScene.Area[]{RiichiTableScene.Area.RIVER, RiichiTableScene.Area.MELD}) {
                int opposite = (seat + 2) % 4;
                var position = TableGeometry.orient(area == RiichiTableScene.Area.RIVER ? .25 : RiichiTableScene.MELD_RIGHT - .1,
                    TableGeometry.FELT_Y + TileDimensions.SMALL.depth() / 2,
                    area == RiichiTableScene.Area.RIVER ? .45 : RiichiTableScene.HAND_Z, opposite);
                var piece = new RiichiTableScene.Piece(0, opposite, area, 0, position, opposite * 90, true, false);
                var origin = camera.eye(seat);
                assertTrue(Double.isFinite(TilePicking.distanceSquared(new RiichiAnimation.Frame(piece, -90),
                    origin, position.subtract(origin), false)), seat + " " + area);
            }
    }

    @Test void tracksRaisedSelectionAndReturnsNearestHitDistance() {
        var tile = tile(Vec3.ZERO, 0, 0);
        var origin = new Vec3(0, TileDimensions.SMALL.height() / 2 + .02, 2);
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

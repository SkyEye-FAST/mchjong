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
        var settings = new TableSettings();
        for (int seat = 0; seat < 4; seat++) for (float pitch : new float[]{0, -30, -60, -90}) {
            var position = TableGeometry.orient(0, TableGeometry.FELT_Y + .081 * TableScene.TILE_SCALE, TableScene.HAND_Z, seat);
            var origin = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, seat);
            assertTrue(Double.isFinite(TilePicking.distanceSquared(tile(position, seat * 90, pitch), origin, position.subtract(origin), false)));
        }
    }

    @Test void rightCornerTilesArePickableFromEachSeatedCameraIncludingSidewaysAndStackedKans() {
        var settings = new TableSettings();
        for (int seat = 0; seat < 4; seat++) for (boolean sideways : new boolean[]{false, true})
            for (boolean stacked : new boolean[]{false, true}) {
                double width = (sideways ? TileMesh.HEIGHT : TileMesh.WIDTH) * TableScene.TILE_SCALE;
                var position = TableGeometry.orient(TableScene.MELD_RIGHT - width / 2,
                    TableGeometry.FELT_Y + TileMesh.DEPTH * TableScene.TILE_SCALE * (stacked ? 1.5 : .5), TableScene.HAND_Z, seat);
                var piece = new TableScene.Piece(0, seat, TableScene.Area.MELD, 0, position,
                    seat * 90 + (sideways ? 90 : 0), true, false);
                var frame = new TableAnimation.Frame(piece, -90);
                var origin = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, seat);
                assertTrue(Double.isFinite(TilePicking.distanceSquared(frame, origin, position.subtract(origin), false)), piece.toString());
            }
    }

    @Test void doesNotSelectTheGapOrTilesBehindTheCamera() {
        var tile = tile(Vec3.ZERO, 0, 0);
        assertEquals(Double.POSITIVE_INFINITY, TilePicking.distanceSquared(tile, new Vec3(.055, 0, 2), new Vec3(0, 0, -1), false));
        assertEquals(Double.POSITIVE_INFINITY, TilePicking.distanceSquared(tile, new Vec3(0, 0, 2), new Vec3(0, 0, 1), false));
    }

    @Test void pannedAndInspectingCamerasPickFarPublicFacesFromEverySeat() {
        var camera = new SeatedCameraState(2, 2.2);
        camera.pan(.2, -.15);
        camera.scroll(1);
        for (boolean inspect : new boolean[]{false, true}) {
            for (int tick = 0; tick < 30; tick++) camera.tick(inspect);
            camera.sample(1);
            for (int seat = 0; seat < 4; seat++) for (TableScene.Area area :
                new TableScene.Area[]{TableScene.Area.RIVER, TableScene.Area.MELD}) {
                int opposite = (seat + 2) % 4;
                var position = TableGeometry.orient(area == TableScene.Area.RIVER ? .25 : TableScene.MELD_RIGHT - .1,
                    TableGeometry.FELT_Y + TileMesh.DEPTH * TableScene.TILE_SCALE / 2,
                    area == TableScene.Area.RIVER ? .45 : TableScene.HAND_Z, opposite);
                var piece = new TableScene.Piece(0, opposite, area, 0, position, opposite * 90, true, false);
                var origin = camera.eye(seat);
                assertTrue(Double.isFinite(TilePicking.distanceSquared(new TableAnimation.Frame(piece, -90),
                    origin, position.subtract(origin), false)), seat + " " + area + " inspect=" + inspect);
            }
        }
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

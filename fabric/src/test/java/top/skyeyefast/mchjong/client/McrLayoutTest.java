package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.McrDiscard;
import top.skyeyefast.mchjong.engine.FlowerTile;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.engine.McrView;
import top.skyeyefast.mchjong.engine.McrWallLayout;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class McrLayoutTest {
    @Test void fourRotatedStraightWallsKeepTheirOffsetAndDoNotIntersect() {
        var pieces = McrTableScene.fullWall();
        assertEquals(144, pieces.size());
        for (int seat = 0; seat < 4; seat++) for (int column = 0; column < 18; column++) {
            int stack = McrWallLayout.stack(seat, column);
            var upper = pieces.get(McrWallLayout.slot(stack, McrWallLayout.Layer.UPPER));
            var lower = pieces.get(McrWallLayout.slot(stack, McrWallLayout.Layer.LOWER));
            var local = TableGeometry.orient(upper.position().x, upper.position().y, upper.position().z, (4 - seat) % 4);
            assertEquals((8.5 - column) * McrTableScene.WALL_STEP - McrTableScene.WALL_OFFSET, local.x, 1e-8);
            assertEquals(McrTableScene.WALL_Z, local.z, 1e-8);
            assertEquals(McrTableScene.DEPTH, upper.position().y - lower.position().y, 1e-8);
            assertEquals(seat, upper.seat());
            assertTrue(upper.back() && lower.back());
            assertTrue(Math.abs(local.x) + McrTableScene.WIDTH / 2 < TableGeometry.FELT_HALF_WIDTH);
        }
        double left = -McrTableScene.WALL_LENGTH / 2 - McrTableScene.WALL_OFFSET;
        double right = McrTableScene.WALL_LENGTH / 2 - McrTableScene.WALL_OFFSET;
        assertTrue(left < -McrTableScene.WALL_Z - McrTableScene.HEIGHT / 2, "One end passes the adjacent wall");
        assertTrue(right < McrTableScene.WALL_Z - McrTableScene.HEIGHT / 2, "The other end stops short");
        assertEquals(McrTableScene.WALL_STEP - McrTableScene.WIDTH,
            McrTableScene.WALL_Z - McrTableScene.HEIGHT / 2 - right, 1e-8,
            "Corners must close with the same fine seam as neighboring stacks");
        for (int i = 0; i < pieces.size(); i++) for (int j = i + 1; j < pieces.size(); j++)
            assertFalse(bounds(pieces.get(i)).deflate(1e-7).intersects(bounds(pieces.get(j)).deflate(1e-7)),
                "Wall slots intersect: " + i + ", " + j);
    }

    @Test void riversCompactCalledHistoryIntoCenteredSixColumns() {
        var history = new ArrayList<McrDiscard>();
        for (int i = 0; i < 26; i++) history.add(new McrDiscard(i, i == 2 || i == 7, false));
        var parts = McrRiverLayout.of(history);
        assertEquals(24, parts.size());
        for (int index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            assertFalse(history.get(part.historyIndex()).called());
            assertEquals(index % 6, part.column());
            assertEquals(index / 6, part.row());
            assertEquals((index % 6 - 2.5) * TileMesh.WIDTH, part.x(), 1e-8);
            assertEquals(index / 6 * (double) TileMesh.HEIGHT, part.z(), 1e-8);
        }
        assertEquals(3, parts.get(2).historyIndex());
        assertEquals(14, parts.get(12).historyIndex());
        assertEquals(25, parts.get(23).historyIndex());
        assertEquals(RiichiTableScene.RIVER_Z, McrTableScene.RIVER_Z);
        assertTrue(McrRiverLayout.COLUMNS * McrTableScene.WIDTH / 2
            < McrTableScene.RIVER_Z - McrTableScene.HEIGHT / 2, "Adjacent rotated rivers must not meet at their corners");
    }

    @Test void sourcesChooseLeftMiddleRightAndEveryKongIsOneFlatRow() {
        for (int from : new int[]{3, 2, 1}) {
            var triplet = McrMeldLayout.of(new Meld(Meld.Type.TRIPLET, List.of(0, 1, 2), from, 0), 0, false);
            int called = from == 3 ? 0 : from == 2 ? 1 : 2;
            assertTrue(triplet.parts().get(called).sideways());
            for (var type : List.of(Meld.Type.OPEN_QUAD, Meld.Type.ADDED_QUAD)) {
                var layout = McrMeldLayout.of(new Meld(type, List.of(0, 1, 2, 3), from, 0), 0, false);
                assertEquals(4, layout.parts().size());
                assertEquals(3 * (double) TileMesh.WIDTH + TileMesh.HEIGHT, layout.width(), 1e-8);
                assertTrue(layout.parts().get(from == 1 ? 3 : called).sideways());
                assertEquals(1, layout.parts().stream().filter(McrMeldLayout.Part::sideways).count());
                for (int i = 1; i < 4; i++) assertTrue(layout.parts().get(i).x() > layout.parts().get(i - 1).x());
            }
        }
        var chow = new Meld(Meld.Type.SEQUENCE, List.of(0, 4, 8), 3, 4);
        assertTrue(McrMeldLayout.of(chow, 0, false).parts().get(0).sideways());
        assertThrows(IllegalArgumentException.class, () -> McrMeldLayout.of(new Meld(chow.type(), chow.tiles(), 1, 4), 0, false));
        var concealed = new Meld(Meld.Type.CONCEALED_QUAD, List.of(0, 1, 2, 3), 0, Tile.ABSENT);
        assertTrue(McrMeldLayout.of(concealed, 0, false).parts().stream().allMatch(part -> part.back() && !part.sideways()));
        assertTrue(McrMeldLayout.of(concealed, 0, true).parts().stream().noneMatch(McrMeldLayout.Part::back));
    }

    @Test void flowersHaveTheirOwnAreaAndHandIndicesKeepTheirSourceIdentity() {
        var flowers = java.util.Arrays.stream(FlowerTile.values()).map(FlowerTile::id).toList();
        var layout = McrFlowerLayout.of(flowers);
        assertEquals(8, layout.size());
        assertTrue(layout.stream().allMatch(part -> part.z() == 0));
        assertEquals(7 * (double) TileMesh.WIDTH, layout.getLast().x() - layout.getFirst().x(), 1e-8);
        assertThrows(IllegalArgumentException.class, () -> McrFlowerLayout.of(List.of(0)));
        var game = new McrGame(711);
        var scene = McrTableScene.build(game.view(0));
        assertEquals(144, scene.size());
        for (var piece : scene) {
            if (piece.area() == McrTableScene.Area.HAND && piece.seat() == 0)
                assertEquals(game.hand(0).get(piece.index()), piece.tile());
            if (piece.area() == McrTableScene.Area.FLOWER) {
                assertTrue(Tile.isFlower(piece.tile()));
                assertTrue(piece.flat());
                assertFalse(piece.back());
            }
        }
    }

    @Test void leftCornerMeldsClearHandsAndFlowersAtEverySeat() {
        var base = new McrGame(711).view(0);
        var seats = new ArrayList<McrView.Seat>();
        for (int seat = 0; seat < 4; seat++) {
            var melds = new ArrayList<Meld>();
            for (int group = 0; group < 4; group++) {
                int tile = seat * 32 + group * 4;
                melds.add(new Meld(Meld.Type.OPEN_QUAD, List.of(tile, tile + 1, tile + 2, tile + 3),
                    (seat + 3) % 4, tile));
            }
            seats.add(new McrView.Seat(Tile.EAST + seat, 0,
                seat == 0 ? List.of(16, 17) : List.of(Tile.HIDDEN, Tile.HIDDEN), seat == 0 ? 17 : Tile.HIDDEN,
                melds, List.of(new McrDiscard(seat * 32 + 18, false, false)),
                seat == 0 ? java.util.Arrays.stream(FlowerTile.values()).map(FlowerTile::id).toList() : List.of(), false));
        }
        var view = new McrView(1, 1, 1, McrGame.Phase.TURN, 0, 0, Tile.EAST, 0, 0, base.opening(),
            java.util.Collections.nCopies(144, Tile.ABSENT), null, seats, List.of(), false, null, List.of());
        var scene = McrTableScene.build(view);
        assertEquals(scene, McrTableScene.immersive(view));
        assertTrue(McrTableScene.immersive(base).stream().noneMatch(piece -> piece.area() == McrTableScene.Area.WALL));
        for (var piece : scene) {
            var local = TableGeometry.orient(piece.position().x, piece.position().y, piece.position().z, (4 - piece.seat()) % 4);
            if (piece.area() == McrTableScene.Area.MELD && piece.index() == 0)
                assertEquals(-RiichiTableScene.MELD_RIGHT + TileMesh.HEIGHT * (double) piece.scale() / 2, local.x, 1e-8);
            if (piece.area() == McrTableScene.Area.RIVER) {
                assertEquals(-2.5 * RiichiTableScene.RIVER_STEP, local.x, 1e-8);
                assertEquals(RiichiTableScene.RIVER_Z, local.z, 1e-8);
            }
            if (piece.area() == McrTableScene.Area.FLOWER) assertEquals(McrTableScene.HAND_Z, local.z, 1e-8);
            var bounds = bounds(piece);
            assertTrue(Math.max(Math.abs(bounds.minX), Math.abs(bounds.maxX)) <= TableGeometry.FELT_HALF_WIDTH);
            assertTrue(Math.max(Math.abs(bounds.minZ), Math.abs(bounds.maxZ)) <= TableGeometry.FELT_HALF_WIDTH);
        }
        for (int i = 0; i < scene.size(); i++) for (int j = i + 1; j < scene.size(); j++)
            assertFalse(bounds(scene.get(i)).deflate(1e-7).intersects(bounds(scene.get(j)).deflate(1e-7)),
                "Hand, flower and meld areas overlap");
    }

    private static AABB bounds(McrTableScene.Piece piece) {
        boolean sideways = Math.floorMod(Math.round(piece.yaw()), 180) == 90;
        double width = TileMesh.WIDTH * (double) piece.scale();
        double height = (piece.flat() ? TileMesh.DEPTH : TileMesh.HEIGHT) * (double) piece.scale();
        double depth = (piece.flat() ? TileMesh.HEIGHT : TileMesh.DEPTH) * (double) piece.scale();
        double halfX = (sideways ? depth : width) / 2;
        double halfZ = (sideways ? width : depth) / 2;
        var p = piece.position();
        return new AABB(p.x - halfX, p.y - height / 2, p.z - halfZ,
            p.x + halfX, p.y + height / 2, p.z + halfZ);
    }
}

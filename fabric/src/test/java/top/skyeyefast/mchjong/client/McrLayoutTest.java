package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.McrDiscard;
import top.skyeyefast.mchjong.engine.McrGame;
import top.skyeyefast.mchjong.engine.McrWallLayout;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class McrLayoutTest {
    @Test void centerUsesNativeWindKindsAndKeepsOwnerLabelsWhenViewerChanges() {
        var indicator = TableBoardState.live(new McrGame(711).view(-1)).indicator();
        var title = (net.minecraft.network.chat.contents.TranslatableContents) indicator.title().getContents();
        assertEquals("mcr.mchjong.indicator_round", title.getKey());
        var prevailing = (net.minecraft.network.chat.Component) title.getArgs()[0];
        assertEquals("wind.mchjong.east.short", ((net.minecraft.network.chat.contents.TranslatableContents) prevailing.getContents()).getKey());
        assertEquals(List.of("east", "south", "west", "north"), indicator.seats().stream()
            .map(label -> ((net.minecraft.network.chat.contents.TranslatableContents) label.getContents()).getKey().replace("wind.mchjong.", "").replace(".short", "")).toList());
        assertEquals(indicator, TableBoardState.live(new McrGame(711).view(0)).replay(2).indicator());
    }

    @Test void fourTiltedStraightWallsRotateTogetherAndDoNotIntersect() {
        var pieces = McrTableScene.fullWall();
        assertEquals(144, pieces.size());
        assertEquals(144, pieces.stream().map(McrTableScene.Piece::index).distinct().count());
        var firstDirection = pieces.get(0).position().subtract(pieces.get(34).position()).normalize();
        for (int seat = 0; seat < 4; seat++) {
            int owner = seat;
            var upper = pieces.stream().filter(piece -> piece.seat() == owner
                && McrWallLayout.layer(piece.index()) == McrWallLayout.Layer.UPPER).toList();
            assertEquals(18, upper.size());
            WallGeometryAssertions.tiltedSide(upper.stream().map(McrTableScene.Piece::position).toList(),
                upper.stream().map(McrTableScene.Piece::yaw).toList(), seat, firstDirection, McrTableScene.WIDTH);
            WallGeometryAssertions.liftRail(upper.stream().map(McrTableScene.Piece::position).toList(), seat, McrTableScene.HEIGHT);
        }
        for (int seat = 0; seat < 4; seat++) for (int column = 0; column < 18; column++) {
            int stack = McrWallLayout.stack(seat, column);
            var upper = pieces.get(McrWallLayout.slot(stack, McrWallLayout.Layer.UPPER));
            var lower = pieces.get(McrWallLayout.slot(stack, McrWallLayout.Layer.LOWER));
            assertEquals(upper.position().x, lower.position().x);
            assertEquals(upper.position().z, lower.position().z);
            assertEquals(upper.yaw(), lower.yaw());
            assertEquals(McrWallLayout.slot(stack, McrWallLayout.Layer.UPPER), upper.index());
            assertEquals(McrWallLayout.slot(stack, McrWallLayout.Layer.LOWER), lower.index());
            assertEquals(McrTableScene.DEPTH, upper.position().y - lower.position().y, 1e-8);
            assertEquals(seat, upper.seat());
            assertTrue(upper.back() && lower.back());
        }
        for (var piece : pieces) {
            var box = bounds(piece);
            assertTrue(Math.max(Math.abs(box.minX), Math.abs(box.maxX)) < TableGeometry.FELT_HALF_WIDTH);
            assertTrue(Math.max(Math.abs(box.minZ), Math.abs(box.maxZ)) < TableGeometry.FELT_HALF_WIDTH);
            WallGeometryAssertions.clearCenter(piece.position(), piece.yaw(), McrTableScene.WIDTH,
                McrTableScene.HEIGHT, McrTableScene.DEPTH);
        }
        for (int i = 0; i < pieces.size(); i++) for (int j = i + 1; j < pieces.size(); j++)
            assertFalse(WallGeometryAssertions.intersects(pieces.get(i).position(), pieces.get(i).yaw(),
                pieces.get(j).position(), pieces.get(j).yaw(), McrTableScene.WIDTH, McrTableScene.HEIGHT, McrTableScene.DEPTH),
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
        }
        assertEquals(3, parts.get(2).historyIndex());
        assertEquals(14, parts.get(12).historyIndex());
        assertEquals(25, parts.get(23).historyIndex());
        assertEquals(TableIndicator.HALF_WIDTH + McrTableScene.HEIGHT / 2 + .005, McrTableScene.RIVER_Z);

    }

    @Test void sourcesChooseLeftMiddleRightAndEveryKongIsOneFlatRow() {
        for (int from : new int[]{3, 2, 1}) {
            var triplet = ChineseMeldLayout.of(new Meld(Meld.Type.TRIPLET, List.of(0, 1, 2), from, 0), 0, false, TileDimensions.LARGE);
            int called = from == 3 ? 0 : from == 2 ? 1 : 2;
            assertTrue(triplet.parts().get(called).sideways());
            for (var type : List.of(Meld.Type.OPEN_QUAD, Meld.Type.ADDED_QUAD)) {
                var layout = ChineseMeldLayout.of(new Meld(type, List.of(0, 1, 2, 3), from, 0), 0, false, TileDimensions.LARGE);
                assertEquals(4, layout.parts().size());
                assertEquals(3 * (double) TileDimensions.LARGE.width() + TileDimensions.LARGE.height(), layout.width(), 1e-8);
                assertTrue(layout.parts().get(from == 1 ? 3 : called).sideways());
                assertEquals(1, layout.parts().stream().filter(ChineseMeldLayout.Part::sideways).count());
                for (int i = 1; i < 4; i++) assertTrue(layout.parts().get(i).x() > layout.parts().get(i - 1).x());
            }
        }
        var chow = new Meld(Meld.Type.SEQUENCE, List.of(0, 4, 8), 3, 4);
        assertTrue(ChineseMeldLayout.of(chow, 0, false, TileDimensions.LARGE).parts().get(0).sideways());
        assertThrows(IllegalArgumentException.class, () -> ChineseMeldLayout.of(new Meld(chow.type(), chow.tiles(), 1, 4), 0, false, TileDimensions.LARGE));
        var concealed = new Meld(Meld.Type.CONCEALED_QUAD, List.of(0, 1, 2, 3), 0, Tile.ABSENT);
        assertTrue(ChineseMeldLayout.of(concealed, 0, false, TileDimensions.LARGE).parts().stream().allMatch(part -> part.back() && !part.sideways()));
        assertTrue(ChineseMeldLayout.of(concealed, 0, true, TileDimensions.LARGE).parts().stream().noneMatch(ChineseMeldLayout.Part::back));
    }

    @Test void flowersShareTheLeftPublicAreaAndHandIndicesKeepTheirSourceIdentity() {
        var publicArea = ChineseTableLayout.publicArea(List.of(), 8, 13, false, 0,
            new ArrayList<>(), TileDimensions.LARGE);
        assertEquals(8, publicArea.flowers().size());
        assertTrue(publicArea.flowers().getFirst().x < 0);
        var game = new McrGame(711);
        var scene = McrTableScene.build(game.view(0));
        assertEquals(144, scene.size());
        WallGeometryAssertions.noIntersections(scene.stream().map(WallGeometryAssertions::solid).toList());
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

    @Test void realGamesConserveStockAndClearAllPhysicalZones() {
        assertEquals(TileDimensions.LARGE, McrTableScene.DIMENSIONS);
        for (long seed : new long[]{1, 2, 3, 4, 5, 6, 19, 20, 21, 22, 42, 43, 711, 712, 2025, 2026}) {
            top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures.mcr(seed, -1, 0, view -> {
                var scene = McrTableScene.build(view);
                assertEquals(144, scene.size(), "Physical stock must survive every accepted action");
                assertTrue(scene.stream().allMatch(piece -> piece.dimensions().equals(TileDimensions.LARGE)));
                for (var piece : scene) WallGeometryAssertions.onFelt(WallGeometryAssertions.solid(piece));
                WallGeometryAssertions.leftMeldsAndMinimalHandShift(scene.stream().map(piece ->
                    new WallGeometryAssertions.PublicPiece(WallGeometryAssertions.solid(piece), piece.seat(),
                        piece.area() == McrTableScene.Area.HAND, piece.area() == McrTableScene.Area.MELD ? piece.index() / 4 : piece.area() == McrTableScene.Area.FLOWER ? 4 + piece.index() : -1)).toList(),
                    view.seats().stream().map(player -> player.hand().size() * McrTableScene.WIDTH
                        + (player.drawn() != Tile.ABSENT && !player.hand().isEmpty() ? RiichiTableScene.DRAW_GAP : 0)).toList(), McrTableScene.HAND_Z);
                assertDoesNotThrow(() -> { for (int i=0; i<scene.size(); i++) for(int j=i+1;j<scene.size();j++)
                    assertFalse(WallGeometryAssertions.intersects(WallGeometryAssertions.solid(scene.get(i)), WallGeometryAssertions.solid(scene.get(j))), scene.get(i)+" / "+scene.get(j)); },
                    "MCR seed=" + seed + " revision=" + view.revision() + " remaining=" + view.remaining());
            });
        }
    }

    @Test void eightFlowersAndFourKongsUseARealConservedStockWithoutScaling() {
        var view = top.skyeyefast.mchjong.fixture.ChineseGameplayFixtures.fourKongsAndEightFlowers();
        assertEquals(8, view.seats().get(0).flowers().size());
        assertEquals(4, view.seats().get(0).melds().size());
        assertEquals(2, view.seats().get(0).hand().size());
        var scene = McrTableScene.build(view);
        assertEquals(144, scene.size());
        assertTrue(scene.stream().allMatch(piece -> piece.dimensions().equals(TileDimensions.LARGE)));
        for (var piece : scene) WallGeometryAssertions.onFelt(WallGeometryAssertions.solid(piece));
        WallGeometryAssertions.noIntersections(scene.stream().map(WallGeometryAssertions::solid).toList());
    }

    private static AABB bounds(McrTableScene.Piece piece) {
        double cosine = Math.abs(Math.cos(Math.toRadians(piece.yaw())));
        double sine = Math.abs(Math.sin(Math.toRadians(piece.yaw())));
        double width = TileDimensions.LARGE.width();
        double height = (piece.flat() ? TileDimensions.LARGE.depth() : TileDimensions.LARGE.height());
        double depth = (piece.flat() ? TileDimensions.LARGE.height() : TileDimensions.LARGE.depth());
        double halfX = (width * cosine + depth * sine) / 2;
        double halfZ = (width * sine + depth * cosine) / 2;
        var p = piece.position();
        return new AABB(p.x - halfX, p.y - height / 2, p.z - halfZ,
            p.x + halfX, p.y + height / 2, p.z + halfZ);
    }
}

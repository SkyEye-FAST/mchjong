package top.skyeyefast.mchjong.client;

import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.*;
import static org.junit.jupiter.api.Assertions.*;

class TaiwanPresentationTest {
    @Test void fixedSlotsRetainBothStockSizesAndUpperLowerGeometry() {
        for (int size : new int[]{136, 144}) {
            var pieces = TaiwanTableScene.fullWall(size);
            assertEquals(size, pieces.size());
            assertEquals(size, pieces.stream().map(TaiwanTableScene.Piece::index).distinct().count());
            for (int slot = 0; slot < size; slot += 2) {
                var top = pieces.get(slot); var bottom = pieces.get(slot + 1);
                assertEquals(TaiwanWallLayout.side(size, slot), top.seat());
                assertEquals(top.position().x, bottom.position().x);
                assertEquals(top.position().z, bottom.position().z);
                assertEquals(TaiwanTableScene.DEPTH, top.position().y - bottom.position().y, 1e-8);
            }
            for (var piece : pieces) WallGeometryAssertions.onFelt(solid(piece));
            WallGeometryAssertions.noIntersections(pieces.stream().map(TaiwanPresentationTest::solid).toList());
        }
    }
    private static WallGeometryAssertions.Solid solid(TaiwanTableScene.Piece p) {
        return new WallGeometryAssertions.Solid(p.position(), p.yaw(), TileDimensions.SMALL.width(),
            (p.flat() ? TileDimensions.SMALL.depth() : TileDimensions.SMALL.height()),
            (p.flat() ? TileDimensions.SMALL.height() : TileDimensions.SMALL.depth()));
    }
}

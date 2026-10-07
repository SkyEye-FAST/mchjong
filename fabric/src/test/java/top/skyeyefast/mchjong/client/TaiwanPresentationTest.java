package top.skyeyefast.mchjong.client;

import java.util.List;
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

    @Test void seededNativeActionsKeepPhysicalStockAndPrivateHandsThroughSettlement() {
        boolean called = false;
        for (var preset : TaiwanPreset.values()) for (long seed : new long[]{1, 4, 19}) {
            var game = TaiwanGame.shuffled(seed, preset.rules(), 0, Tile.EAST, 0);
            int steps = 0;
            while (true) {
                for (int recipient : new int[]{-1, 0, 1, 2, 3}) {
                    var view = game.view(recipient); var scene = TaiwanTableScene.build(view);
                    assertEquals(preset.rules().getFlowers() == TaiwanRules.Flowers.NONE ? 136 : 144, scene.size());
                    for (var piece : scene) {
                        WallGeometryAssertions.onFelt(solid(piece));
                        if (piece.area() == TaiwanTableScene.Area.HAND && piece.seat() != recipient) assertEquals(Tile.HIDDEN, piece.tile());
                    }
                    for (int i = 0; i < scene.size(); i++) for (int j = i + 1; j < scene.size(); j++) assertFalse(WallGeometryAssertions.intersects(solid(scene.get(i)), solid(scene.get(j))), "seed=" + seed + " step=" + steps + " " + scene.get(i) + " / " + scene.get(j));
                    assertTrue(TaiwanTableScene.immersive(view).stream().noneMatch(p -> p.area() == TaiwanTableScene.Area.WALL));
                }
                if (game.getPhase() == TaiwanGame.Phase.FINISHED) break;
                assertTrue(++steps < 1000);
                var decision = game.decisions().getFirst();
                var actions = decision.getActions();
                int choice = -1;
                for (int i = 0; i < actions.size(); i++) if (actions.get(i).getType() == TaiwanAction.Type.WIN) { choice = i; break; }
                if (choice < 0) for (int i = 0; i < actions.size(); i++) if (List.of(TaiwanAction.Type.CHOW, TaiwanAction.Type.PONG, TaiwanAction.Type.OPEN_KONG, TaiwanAction.Type.CONCEALED_KONG, TaiwanAction.Type.ADDED_KONG).contains(actions.get(i).getType())) { choice = i; break; }
                if (choice < 0) for (int i = 0; i < actions.size(); i++) if (actions.get(i).getType() == (game.getPhase() == TaiwanGame.Phase.REACTION ? TaiwanAction.Type.PASS : TaiwanAction.Type.DISCARD)) { choice = i; break; }
                assertTrue(choice >= 0);
                game.submit(decision.getSeat(), decision.getToken(), choice);
                called |= game.view(-1).seats().stream().anyMatch(s -> !s.melds().isEmpty());
            }
        }
        assertTrue(called);
    }

    private static WallGeometryAssertions.Solid solid(TaiwanTableScene.Piece p) {
        return new WallGeometryAssertions.Solid(p.position(), p.yaw(), TileDimensions.SMALL.width(),
            (p.flat() ? TileDimensions.SMALL.depth() : TileDimensions.SMALL.height()),
            (p.flat() ? TileDimensions.SMALL.height() : TileDimensions.SMALL.depth()));
    }
}

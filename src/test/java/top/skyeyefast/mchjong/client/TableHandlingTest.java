package top.skyeyefast.mchjong.client;

import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class TableHandlingTest {
    private static UUID player(int seat) { return new UUID(391, seat); }
    private static Game start(RuleSet rules) {
        Game game = new Game(UUID.randomUUID(), rules, 12);
        game.configureEquipment(true, Tile.set(rules.sanma(), rules.defaultRedFives()));
        for (int seat = 0; seat < rules.players(); seat++) {
            game.join(player(seat), "Player " + seat, seat);
            act(game, seat, Action.Type.READY);
        }
        return game;
    }
    private static void act(Game game, int seat, Action.Type type) {
        TableView view = game.view(player(seat));
        int index = -1;
        for (int i = 0; i < view.actions().size(); i++) if (view.actions().get(i).type() == type) index = i;
        assertTrue(index >= 0 && game.act(player(seat), view.decision(), index));
        game.validate();
    }

    @Test void everyPhysicalTileIsPresentAndHiddenThroughoutShuffleAndWallBuilding() {
        for (RuleSet rules : RuleSet.values()) {
            Game game = start(rules);
            int size = rules.sanma() ? 108 : 136;
            for (int step = -1; step < rules.players(); step++) {
                var view = game.view(player(game.view(null).dealer()));
                var pieces = TableScene.build(view);
                assertEquals(size, pieces.size());
                assertEquals(size, pieces.stream().map(TableScene.Piece::index).distinct().count());
                assertTrue(pieces.stream().allMatch(piece -> piece.tile() == Tile.HIDDEN && piece.back()));
                assertTrue(pieces.stream().allMatch(piece -> Math.abs(piece.position().x) < TableGeometry.FELT_HALF_WIDTH
                    && Math.abs(piece.position().z) < TableGeometry.FELT_HALF_WIDTH));
                if (step < 0) act(game, view.dealer(), Action.Type.SHUFFLE);
                else act(game, step, Action.Type.BUILD_WALL);
            }
            assertEquals(size, TableScene.build(game.view(null)).stream().filter(piece -> piece.area() == TableScene.Area.WALL).count());
        }
    }

    @Test void gesturesRequireTheCorrectObjectAndDestinationForEverySeat() {
        for (RuleSet rules : RuleSet.values()) {
            Game game = start(rules);
            int dealer = game.view(null).dealer();
            var shuffle = game.view(player(dealer));
            assertEquals(-1, TableHandling.action(game.view(null)));
            assertFalse(TableHandling.completes(shuffle, Vec3.ZERO, new Vec3(.1, 0, .1)));
            assertTrue(TableHandling.completes(shuffle, Vec3.ZERO, new Vec3(.6, 0, .1)));
            assertFalse(TableHandling.completes(shuffle, Vec3.ZERO, new Vec3(2, 0, 0)));
            act(game, dealer, Action.Type.SHUFFLE);
            for (int seat = 0; seat < rules.players(); seat++) {
                var view = game.view(player(seat));
                var pieces = TableScene.build(view);
                assertNotNull(TableHandling.source(view, pieces));
                for (var piece : pieces) assertEquals(piece.area() == TableScene.Area.LOOSE && piece.seat() == seat,
                    TableHandling.source(view, piece));
                assertTrue(TableHandling.completes(view, Vec3.ZERO, TableHandling.destination(view)));
                assertFalse(TableHandling.completes(view, Vec3.ZERO, TableHandling.destination(view).scale(-1)));
                assertFalse(TableHandling.completes(view, Vec3.ZERO, new Vec3(Double.NaN, 0, 0)));
                act(game, seat, Action.Type.BUILD_WALL);
            }
            for (int packet = 0; packet < 4 * rules.players(); packet++) {
                var view = game.view(player(game.view(null).turn()));
                assertEquals(packet < 3 * rules.players() ? 4 : 1, view.handling().packetSize());
                for (var piece : TableScene.build(view)) if (piece.area() == TableScene.Area.WALL)
                    assertEquals(piece.index() / 2 == view.handling().sourceSlot() / 2, TableHandling.source(view, piece));
                assertTrue(TableHandling.completes(view, Vec3.ZERO, TableHandling.destination(view)));
                assertFalse(TableHandling.completes(view, Vec3.ZERO, Vec3.ZERO));
                act(game, view.viewerSeat(), Action.Type.TAKE_PACKET);
            }
            var draw = game.view(player(dealer));
            assertEquals(Game.Phase.DRAW, draw.phase());
            assertNotNull(TableHandling.source(draw, TableScene.build(draw)));
            assertTrue(TableHandling.completes(draw, Vec3.ZERO, TableHandling.destination(draw)));
            act(game, dealer, Action.Type.DRAW);
            assertEquals(-1, TableHandling.action(game.view(player(dealer))));
            assertEquals(-1, game.view(null).handling().sourceSlot());
        }
    }
}

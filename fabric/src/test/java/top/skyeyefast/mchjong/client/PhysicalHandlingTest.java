package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.Action;
import top.skyeyefast.mchjong.engine.Game;
import top.skyeyefast.mchjong.engine.RuleSet;
import top.skyeyefast.mchjong.engine.TableView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalHandlingTest {
    private static UUID id(int seat) { return new UUID(904, seat); }

    private static Game manual(RuleSet rules) {
        var game = new Game(UUID.randomUUID(), rules, 4432);
        game.configureEquipment(true, Tile.set(rules.sanma(), rules.defaultRedFives()));
        for (int seat = 0; seat < rules.players(); seat++) {
            assertTrue(game.join(id(seat), "Player " + seat, seat));
        }
        top.skyeyefast.mchjong.engine.PositionedFixture.assign(game);
        for (int seat = 0; seat < rules.players(); seat++) act(game, seat, Action.Type.READY);
        return game;
    }

    private static void act(Game game, int seat, Action.Type type) {
        var view = game.view(id(seat));
        int index = IntStream.range(0, view.actions().size()).filter(i -> view.actions().get(i).type() == type).findFirst().orElseThrow();
        assertTrue(game.act(id(seat), view.decision(), index));
        if (type == Action.Type.BUILD_WALL && game.view(null).handling().builtWalls() == (1 << game.rules().players()) - 1) {
            act(game, game.view(null).dealer(), Action.Type.PICK_UP_DICE);
            act(game, game.view(null).dealer(), Action.Type.ROLL_DICE);
        }
    }

    private static void conserved(TableView view) {
        assertEquals(view.rules().sanma() ? 108 : 136, TableScene.build(view).size(), "Visible physical tiles must be conserved");
    }

    @Test void everyPresetUsesRealSourcesAndTheCorrectSeatDestination() {
        for (var rules : RuleSet.values()) {
            var game = manual(rules);
            int dealer = game.view(null).dealer();
            var view = game.view(id(dealer));
            var loose = TableScene.build(view);
            conserved(view);
            assertTrue(loose.stream().allMatch(piece -> piece.area() == TableScene.Area.LOOSE && piece.tile() == Tile.HIDDEN && piece.back()));
            assertEquals(-1, TableHandling.action(game.view(null)), "Spectators cannot handle tiles");
            var source = TableHandling.source(view, loose);
            assertNotNull(source);
            var sweep = new Vec3(source.position().x < 0 ? .6 : -.6, TableGeometry.FELT_Y, 0);
            assertTrue(TableHandling.completes(view, source.position(), sweep));
            assertFalse(TableHandling.completes(view, source.position(), source.position()));
            assertFalse(TableHandling.completes(view, source.position(), new Vec3(4, TableGeometry.FELT_Y, 4)));
            assertFalse(TableHandling.completes(view, null, sweep));
            assertFalse(TableHandling.completes(view, source.position(), new Vec3(Double.NaN, 0, 0)));
            act(game, dealer, Action.Type.SHUFFLE);
            for (int seat = 0; seat < rules.players(); seat++) {
                view = game.view(id(seat));
                conserved(view);
                source = TableHandling.source(view, TableScene.build(view));
                assertNotNull(source);
                assertEquals(TableScene.Area.LOOSE, source.area());
                assertEquals(seat, source.seat());
                assertTrue(TableHandling.completes(view, source.position(), TableHandling.destination(view)));
                var otherWall = TableGeometry.orient(0, TableGeometry.FELT_Y, TableScene.WALL_Z, (seat + 1) % rules.players());
                assertFalse(TableHandling.completes(view, source.position(), otherWall));
                assertFalse(TableHandling.completes(view, source.position(), source.position()));
                act(game, seat, Action.Type.BUILD_WALL);
                var after = TableScene.build(game.view(id(seat)));
                int built = seat;
                assertTrue(after.stream().noneMatch(piece -> piece.area() == TableScene.Area.LOOSE && piece.seat() == built));
                assertEquals((seat + 1) * (rules.sanma() ? 36 : 34), after.stream().filter(piece -> piece.area() == TableScene.Area.WALL).count());
            }
            for (int packet = 0; packet < 4 * rules.players(); packet++) {
                int turn = game.view(null).turn();
                view = game.view(id(turn));
                conserved(view);
                source = TableHandling.source(view, TableScene.build(view));
                assertNotNull(source);
                assertEquals(TableScene.Area.WALL, source.area());
                assertEquals(view.handling().sourceSlot(), source.index());
                assertPickable(view);
                assertTrue(TableHandling.completes(view, source.position(), TableHandling.destination(view)));
                assertFalse(TableHandling.completes(view, source.position(), source.position()));
                assertFalse(TableHandling.completes(view, source.position(), new Vec3(0, TableGeometry.FELT_Y, 0)));
                assertEquals(-1, TableHandling.action(game.view(id((turn + 1) % rules.players()))));
                act(game, turn, Action.Type.TAKE_PACKET);
            }
            view = game.view(id(dealer));
            conserved(view);
            assertEquals(Game.Phase.DRAW, view.phase());
            source = TableHandling.source(view, TableScene.build(view));
            assertNotNull(source);
            assertEquals(view.handling().sourceSlot(), source.index());
            assertPickable(view);
            act(game, dealer, Action.Type.DRAW);
            conserved(game.view(id(dealer)));
            assertEquals(-1, TableHandling.action(game.view(id(dealer))));
        }
    }

    @Test void automaticTablesKeepTheirOwnControlsAndHaveNoLoosePile() {
        var game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 4432);
        game.configureEquipment(false, Tile.set(false));
        for (int seat = 0; seat < 4; seat++) {
            game.join(id(seat), "Player " + seat, seat);
        }
        top.skyeyefast.mchjong.engine.PositionedFixture.assign(game);
        for (int seat = 0; seat < 4; seat++) act(game, seat, Action.Type.READY);
        var view = game.view(id(0));
        assertNull(view.handling());
        assertEquals(-1, TableHandling.action(view));
        assertFalse(TableHandling.physical(view, new Action(Action.Type.NEXT)));
        assertTrue(TableScene.build(view).stream().noneMatch(piece -> piece.area() == TableScene.Area.LOOSE));
    }

    @Test void buildingMovesTheExistingLooseTilesRatherThanSpawningAnotherWall() {
        var game = manual(RuleSet.TENHOU_4);
        int dealer = game.view(null).dealer();
        act(game, dealer, Action.Type.SHUFFLE);
        var before = game.view(id(0));
        var animation = new TableAnimation();
        animation.accept(before, 100);
        act(game, 0, Action.Type.BUILD_WALL);
        animation.accept(game.view(id(0)), 1000);
        var moved = animation.sample(1000).stream().filter(frame -> frame.piece().area() == TableScene.Area.WALL).findFirst().orElseThrow();
        var prior = TableScene.build(before).stream().filter(piece -> piece.index() == moved.piece().index()).findFirst().orElseThrow();
        assertEquals(prior.position(), moved.piece().position());
        assertEquals(Tile.HIDDEN, moved.piece().tile());
        assertEquals(136, animation.sample(1100).size());
        assertNotEquals(prior.position(), animation.sample(1400).stream()
            .filter(frame -> frame.piece().index() == moved.piece().index()).findFirst().orElseThrow().piece().position());
    }

    @Test void replacementAnimationStartsAtTheRefilledDeadWallSlot() {
        var game = manual(RuleSet.MAHJONG_SOUL_3);
        int dealer = game.view(null).dealer();
        act(game, dealer, Action.Type.SHUFFLE);
        for (int seat = 0; seat < 3; seat++) act(game, seat, Action.Type.BUILD_WALL);
        for (int packet = 0; packet < 12; packet++) act(game, game.view(null).turn(), Action.Type.TAKE_PACKET);
        var base = game.view(id(dealer));
        int deadSlot = base.wall().size() - 1;
        var before = snapshot(base, base.revision() + 1, Game.Phase.DRAW, base.wall(), base.seats(),
            new TableView.Handling(7, deadSlot, 1, 1, 1, false), List.of(new Action(Action.Type.DRAW)));
        var wall = new ArrayList<>(before.wall());
        wall.set(wall.size() - 15, Tile.ABSENT);
        var seats = new ArrayList<>(before.seats());
        var player = seats.get(dealer);
        var hand = new ArrayList<>(player.hand());
        int drawn = IntStream.range(0, 136).filter(tile -> !hand.contains(tile)).findFirst().orElseThrow();
        hand.add(drawn);
        seats.set(dealer, new TableView.Seat(false, player.name(), true, false, false, player.points(), hand, drawn,
            player.melds(), player.river(), player.norths(), false, false));
        var after = snapshot(before, before.revision() + 1, Game.Phase.TURN, wall, seats, new TableView.Handling(7, -1, 0, 1, 1, false), List.of());
        var animation = new TableAnimation();
        animation.accept(before, 0);
        animation.accept(after, 1000);
        var actual = animation.sample(1000).stream().filter(frame -> frame.piece().area() == TableScene.Area.HAND
            && frame.piece().seat() == dealer && frame.piece().index() == 13).findFirst().orElseThrow();
        assertEquals(TableScene.wallPiece(before, deadSlot, false).position(), actual.piece().position());
    }

    @Test void settlementCollectionMovesOnlyTheReadyPlayersTilesAndKeepsEveryTile() {
        var game = manual(RuleSet.TENHOU_4);
        int dealer = game.view(null).dealer();
        act(game, dealer, Action.Type.SHUFFLE);
        for (int seat = 0; seat < 4; seat++) act(game, seat, Action.Type.BUILD_WALL);
        for (int packet = 0; packet < 16; packet++) act(game, game.view(null).turn(), Action.Type.TAKE_PACKET);
        var base = game.view(id(dealer));
        var receipt = snapshot(base, base.revision() + 1, Game.Phase.HAND_END, base.wall(), base.seats(),
            new TableView.Handling(15, -1, 0, 1, 1, false), List.of(new Action(Action.Type.NEXT)));
        var before = TableScene.build(receipt);
        var source = TableHandling.source(receipt, before);
        assertNotNull(source);
        assertEquals(dealer, source.seat());
        assertTrue(TableHandling.completes(receipt, source.position(), TableHandling.destination(receipt)));
        assertFalse(TableHandling.completes(receipt, source.position(), source.position()));
        var seats = new ArrayList<>(base.seats());
        var player = seats.get(dealer);
        seats.set(dealer, new TableView.Seat(false, player.name(), true, false, true, player.points(), player.hand(),
            player.drawn(), player.melds(), player.river(), player.norths(), player.riichi(), player.exposed()));
        var collected = snapshot(receipt, receipt.revision() + 1, Game.Phase.HAND_END, receipt.wall(), seats,
            receipt.handling(), List.of());
        var after = TableScene.build(collected);
        conserved(collected);
        assertEquals(-1, TableHandling.action(collected));
        assertTrue(after.stream().filter(piece -> piece.area() == TableScene.Area.HAND && piece.seat() == dealer)
            .allMatch(piece -> piece.flat() && piece.back() && Math.abs(piece.position().x) < .6 && Math.abs(piece.position().z) < .6));
        assertEquals(before.stream().filter(piece -> piece.seat() != dealer || piece.area() == TableScene.Area.WALL).toList(),
            after.stream().filter(piece -> piece.seat() != dealer || piece.area() == TableScene.Area.WALL).toList());
    }

    @Test void drawersStayInsideTheCompactFootprintAndHaveDistinctHitRegions() {
        for (int side = 0; side < 4; side++) {
            var bounds = TableGeometry.drawerBounds(side);
            assertEquals(side, TableGeometry.drawerSide(bounds.getCenter()));
            assertTrue(bounds.maxY < TableGeometry.FELT_Y);
            assertTrue(bounds.minX >= -TableGeometry.OUTER_HALF_WIDTH && bounds.maxX <= TableGeometry.OUTER_HALF_WIDTH);
            assertTrue(bounds.minZ >= -TableGeometry.OUTER_HALF_WIDTH && bounds.maxZ <= TableGeometry.OUTER_HALF_WIDTH);
        }
        assertEquals(-1, TableGeometry.drawerSide(new Vec3(0, TableGeometry.FELT_Y, 0)));
    }

    private static TableView snapshot(TableView base, long revision, Game.Phase phase, List<Integer> wall,
            List<TableView.Seat> seats, TableView.Handling handling, List<Action> actions) {
        return new TableView(base.tableId(), revision, revision, base.handNumber(), base.rules(), phase, base.viewerSeat(),
            base.dealer(), base.round(), base.honba(), base.riichiSticks(), base.turn(), base.remaining(), base.wallBreak(), wall,
            base.focus(), seats, actions, base.wins(), base.result(), base.deltas(), base.finalScores(), base.timeControl(),
            base.clocks(), base.finalRanks(), base.handVisibility(), base.exitVote(), handling, null, base.ronBlocked(), base.riichiHan());
    }

    private static void assertPickable(TableView view) {
        var settings = new TableSettings();
        var origin = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, view.viewerSeat());
        var frames = TableScene.build(view).stream().map(piece -> new TableAnimation.Frame(piece,
            piece.flat() ? piece.back() ? 90 : -90 : 0)).toList();
        boolean visible = frames.stream().filter(frame -> TableHandling.source(view, frame.piece())).anyMatch(frame -> {
            var ray = TableHandling.grip(frame.piece(), origin).subtract(origin);
            var nearest = frames.stream().min(java.util.Comparator.comparingDouble(other ->
                TilePicking.distanceSquared(other, origin, ray, false))).orElseThrow();
            return TableHandling.source(view, nearest.piece());
        });
        assertTrue(visible, "Physical wall source is occluded: " + view.rules() + " / " + view.viewerSeat() + " / " + view.handling());
    }
}

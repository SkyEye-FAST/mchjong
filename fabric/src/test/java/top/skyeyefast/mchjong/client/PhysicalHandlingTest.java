package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.RiichiSession;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RoomAction;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalHandlingTest {
    private static UUID id(int seat) { return new UUID(904, seat); }

    private static RiichiGame manual(RiichiPreset rules) {
        var session = new RiichiSession(UUID.randomUUID(), rules, 4432);
        session.configureEquipment(true, Tile.set(rules.sanma(), rules.defaultRedFives()));
        for (int seat = 0; seat < rules.players(); seat++) {
            assertTrue(session.join(id(seat), "Player " + seat, seat));
        }
        top.skyeyefast.mchjong.engine.PositionedFixture.assign(session);
        for (int seat = 0; seat < rules.players(); seat++) ready(session, seat);
        return session.game();
    }

    private static void act(RiichiGame game, int seat, RiichiAction.Type type) {
        var view = game.view(id(seat));
        int index = IntStream.range(0, view.actions().size()).filter(i -> view.actions().get(i).type() == type).findFirst().orElseThrow();
        assertTrue(game.act(id(seat), view.decision(), index));
        if (type == RiichiAction.Type.BUILD_WALL && game.view(null).handling().builtWalls() == (1 << game.rules().players()) - 1) {
            act(game, game.view(null).dealer(), RiichiAction.Type.PICK_UP_DICE);
            act(game, game.view(null).dealer(), RiichiAction.Type.ROLL_DICE);
        }
    }

    private static void ready(RiichiSession game, int seat) {
        var room = game.roomView(id(seat));
        int index = room.actions().indexOf(new RoomAction(RoomAction.Type.READY));
        assertTrue(game.actRoom(id(seat), room.tableId(), room.incarnation(), room.decision(), index));
    }

    private static void conserved(RiichiView view) {
        assertEquals(view.rules().sanma() ? 108 : 136, RiichiTableScene.build(view).size(), "Visible physical tiles must be conserved");
    }

    @Test void everyPresetUsesRealSourcesAndTheCorrectSeatDestination() {
        for (var rules : RiichiPreset.values()) {
            var game = manual(rules);
            int dealer = game.view(null).dealer();
            var view = game.view(id(dealer));
            var loose = RiichiTableScene.build(view);
            conserved(view);
            assertTrue(loose.stream().allMatch(piece -> piece.area() == RiichiTableScene.Area.LOOSE && piece.tile() == Tile.HIDDEN && piece.back()));
            assertEquals(-1, RiichiHandling.action(game.view(null)), "Spectators cannot handle tiles");
            var source = RiichiHandling.source(view, loose);
            assertNotNull(source);
            var sweep = new Vec3(source.position().x < 0 ? .6 : -.6, TableGeometry.FELT_Y, 0);
            assertTrue(RiichiHandling.completes(view, source.position(), sweep));
            assertFalse(RiichiHandling.completes(view, source.position(), source.position()));
            assertFalse(RiichiHandling.completes(view, source.position(), new Vec3(4, TableGeometry.FELT_Y, 4)));
            assertFalse(RiichiHandling.completes(view, null, sweep));
            assertFalse(RiichiHandling.completes(view, source.position(), new Vec3(Double.NaN, 0, 0)));
            act(game, dealer, RiichiAction.Type.SHUFFLE);
            for (int seat = 0; seat < rules.players(); seat++) {
                view = game.view(id(seat));
                conserved(view);
                source = RiichiHandling.source(view, RiichiTableScene.build(view));
                assertNotNull(source);
                assertEquals(RiichiTableScene.Area.LOOSE, source.area());
                assertEquals(seat, source.seat());
                assertTrue(RiichiHandling.completes(view, source.position(), RiichiHandling.destination(view)));
                var otherWall = TableGeometry.orient(0, TableGeometry.FELT_Y, RiichiTableScene.WALL_Z, (seat + 1) % rules.players());
                assertFalse(RiichiHandling.completes(view, source.position(), otherWall));
                assertFalse(RiichiHandling.completes(view, source.position(), source.position()));
                act(game, seat, RiichiAction.Type.BUILD_WALL);
                var after = RiichiTableScene.build(game.view(id(seat)));
                int built = seat;
                assertTrue(after.stream().noneMatch(piece -> piece.area() == RiichiTableScene.Area.LOOSE && piece.seat() == built));
                assertEquals((seat + 1) * (rules.sanma() ? 36 : 34), after.stream().filter(piece -> piece.area() == RiichiTableScene.Area.WALL).count());
            }
            for (int packet = 0; packet < 4 * rules.players(); packet++) {
                int turn = game.view(null).turn();
                view = game.view(id(turn));
                conserved(view);
                source = RiichiHandling.source(view, RiichiTableScene.build(view));
                assertNotNull(source);
                assertEquals(RiichiTableScene.Area.WALL, source.area());
                assertEquals(view.handling().sourceSlot(), source.index());
                assertPickable(view);
                assertTrue(RiichiHandling.completes(view, source.position(), RiichiHandling.destination(view)));
                assertFalse(RiichiHandling.completes(view, source.position(), source.position()));
                assertFalse(RiichiHandling.completes(view, source.position(), new Vec3(0, TableGeometry.FELT_Y, 0)));
                assertEquals(-1, RiichiHandling.action(game.view(id((turn + 1) % rules.players()))));
                act(game, turn, RiichiAction.Type.TAKE_PACKET);
            }
            view = game.view(id(dealer));
            conserved(view);
            assertEquals(RiichiView.Phase.DRAW, view.phase());
            source = RiichiHandling.source(view, RiichiTableScene.build(view));
            assertNotNull(source);
            assertEquals(view.handling().sourceSlot(), source.index());
            assertPickable(view);
            act(game, dealer, RiichiAction.Type.DRAW);
            conserved(game.view(id(dealer)));
            assertEquals(-1, RiichiHandling.action(game.view(id(dealer))));
        }
    }

    @Test void automaticTablesKeepTheirOwnControlsAndHaveNoLoosePile() {
        var session = new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 4432);
        session.configureEquipment(false, Tile.set(false));
        for (int seat = 0; seat < 4; seat++) {
            session.join(id(seat), "Player " + seat, seat);
        }
        top.skyeyefast.mchjong.engine.PositionedFixture.assign(session);
        for (int seat = 0; seat < 4; seat++) ready(session, seat);
        var view = session.view(id(0));
        assertNull(view.handling());
        assertEquals(-1, RiichiHandling.action(view));
        assertFalse(RiichiHandling.physical(view, new RiichiAction(RiichiAction.Type.NEXT)));
        assertTrue(RiichiTableScene.build(view).stream().noneMatch(piece -> piece.area() == RiichiTableScene.Area.LOOSE));
    }

    @Test void buildingMovesTheExistingLooseTilesRatherThanSpawningAnotherWall() {
        var game = manual(RiichiPreset.TENHOU_4);
        int dealer = game.view(null).dealer();
        act(game, dealer, RiichiAction.Type.SHUFFLE);
        var before = game.view(id(0));
        var animation = new RiichiAnimation();
        animation.accept(before, 100);
        act(game, 0, RiichiAction.Type.BUILD_WALL);
        animation.accept(game.view(id(0)), 1000);
        var moved = animation.sample(1000).stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.WALL).findFirst().orElseThrow();
        var prior = RiichiTableScene.build(before).stream().filter(piece -> piece.index() == moved.piece().index()).findFirst().orElseThrow();
        assertEquals(prior.position(), moved.piece().position());
        assertEquals(Tile.HIDDEN, moved.piece().tile());
        assertEquals(136, animation.sample(1100).size());
        assertNotEquals(prior.position(), animation.sample(1400).stream()
            .filter(frame -> frame.piece().index() == moved.piece().index()).findFirst().orElseThrow().piece().position());
    }

    @Test void replacementAnimationStartsAtTheVacatedDeadWallSlot() {
        var game = manual(RiichiPreset.MAHJONG_SOUL_3);
        int dealer = game.view(null).dealer();
        act(game, dealer, RiichiAction.Type.SHUFFLE);
        for (int seat = 0; seat < 3; seat++) act(game, seat, RiichiAction.Type.BUILD_WALL);
        for (int packet = 0; packet < 12; packet++) act(game, game.view(null).turn(), RiichiAction.Type.TAKE_PACKET);
        var base = game.view(id(dealer));
        int deadSlot = base.wall().size() - 1;
        var before = snapshot(base, base.revision() + 1, RiichiView.Phase.DRAW, base.wall(), base.seats(),
            new RiichiView.Handling(7, deadSlot, 1, 1, 1, false), List.of(new RiichiAction(RiichiAction.Type.DRAW)));
        var wall = new ArrayList<>(before.wall());
        wall.set(deadSlot, Tile.ABSENT);
        var seats = new ArrayList<>(before.seats());
        var player = seats.get(dealer);
        var hand = new ArrayList<>(player.hand());
        int drawn = IntStream.range(0, 136).filter(tile -> !hand.contains(tile)).findFirst().orElseThrow();
        hand.add(drawn);
        seats.set(dealer, new RiichiView.Seat(false, player.name(), true, false, false, player.points(), hand, drawn,
            player.melds(), player.river(), player.norths(), false, false, false));
        var after = snapshot(before, before.revision() + 1, RiichiView.Phase.TURN, wall, seats, new RiichiView.Handling(7, -1, 0, 1, 1, false), List.of());
        var animation = new RiichiAnimation();
        animation.accept(before, 0);
        animation.accept(after, 1000);
        var actual = animation.sample(1000).stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.HAND
            && frame.piece().seat() == dealer && frame.piece().index() == 13).findFirst().orElseThrow();
        assertEquals(RiichiTableScene.wallPiece(before, deadSlot, false).position(), actual.piece().position());
    }

    @Test void settlementCollectionMovesOnlyTheReadyPlayersTilesAndKeepsEveryTile() {
        var game = manual(RiichiPreset.TENHOU_4);
        int dealer = game.view(null).dealer();
        act(game, dealer, RiichiAction.Type.SHUFFLE);
        for (int seat = 0; seat < 4; seat++) act(game, seat, RiichiAction.Type.BUILD_WALL);
        for (int packet = 0; packet < 16; packet++) act(game, game.view(null).turn(), RiichiAction.Type.TAKE_PACKET);
        var base = game.view(id(dealer));
        var receipt = snapshot(base, base.revision() + 1, RiichiView.Phase.HAND_END, base.wall(), base.seats(),
            new RiichiView.Handling(15, -1, 0, 1, 1, false), List.of(new RiichiAction(RiichiAction.Type.NEXT)));
        var before = RiichiTableScene.build(receipt);
        var source = RiichiHandling.source(receipt, before);
        assertNotNull(source);
        assertEquals(dealer, source.seat());
        assertTrue(RiichiHandling.completes(receipt, source.position(), RiichiHandling.destination(receipt)));
        assertFalse(RiichiHandling.completes(receipt, source.position(), source.position()));
        var seats = new ArrayList<>(base.seats());
        var player = seats.get(dealer);
        seats.set(dealer, new RiichiView.Seat(false, player.name(), true, false, true, player.points(), player.hand(),
            player.drawn(), player.melds(), player.river(), player.norths(), player.riichi(), player.exposed(), player.doubleRiichi()));
        var collected = snapshot(receipt, receipt.revision() + 1, RiichiView.Phase.HAND_END, receipt.wall(), seats,
            receipt.handling(), List.of());
        var after = RiichiTableScene.build(collected);
        conserved(collected);
        assertEquals(-1, RiichiHandling.action(collected));
        assertTrue(after.stream().filter(piece -> piece.area() == RiichiTableScene.Area.HAND && piece.seat() == dealer)
            .allMatch(piece -> piece.flat() && piece.back() && Math.abs(piece.position().x) < .6 && Math.abs(piece.position().z) < .6));
        assertEquals(before.stream().filter(piece -> piece.seat() != dealer || piece.area() == RiichiTableScene.Area.WALL).toList(),
            after.stream().filter(piece -> piece.seat() != dealer || piece.area() == RiichiTableScene.Area.WALL).toList());
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

    private static RiichiView snapshot(RiichiView base, long revision, RiichiView.Phase phase, List<Integer> wall,
            List<RiichiView.Seat> seats, RiichiView.Handling handling, List<RiichiAction> actions) {
        return new RiichiView(base.tableId(), revision, revision, base.handNumber(), base.rules(), phase, base.viewerSeat(),
            base.dealer(), base.round(), base.honba(), base.riichiSticks(), base.turn(), base.remaining(), base.wallBreak(), wall,
            base.focus(), seats, actions, base.wins(), base.result(), base.deltas(), base.finalScores(), base.finalUma(), base.timeControl(),
            base.clocks(), base.finalRanks(), base.playerHandVisibility(), base.openHands(), base.exitVote(), handling, null, base.ronBlocked(), base.riichiHan(), base.riichiSafeTiles(), base.externalBots(), base.settlementTicks(), base.settlementSkippedSeats());
    }

    private static void assertPickable(RiichiView view) {
        var settings = new TableSettings();
        var origin = TableGeometry.orient(0, settings.cameraHeight, settings.cameraDistance, view.viewerSeat());
        var frames = RiichiTableScene.build(view).stream().map(piece -> new RiichiAnimation.Frame(piece,
            piece.flat() ? piece.back() ? 90 : -90 : 0)).toList();
        boolean visible = frames.stream().filter(frame -> RiichiHandling.source(view, frame.piece())).anyMatch(frame -> {
            var ray = RiichiHandling.grip(frame.piece(), origin).subtract(origin);
            var nearest = frames.stream().min(java.util.Comparator.comparingDouble(other ->
                TilePicking.distanceSquared(other, origin, ray, false))).orElseThrow();
            return RiichiHandling.source(view, nearest.piece());
        });
        assertTrue(visible, "Physical wall source is occluded: " + view.rules() + " / " + view.viewerSeat() + " / " + view.handling());
    }
}

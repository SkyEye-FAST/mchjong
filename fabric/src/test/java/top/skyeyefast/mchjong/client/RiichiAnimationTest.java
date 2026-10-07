package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.RiichiAction;
import top.skyeyefast.mchjong.engine.RiichiDiscard;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.Meld;
import top.skyeyefast.mchjong.engine.RiichiRules;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.RiichiView;
import top.skyeyefast.mchjong.engine.Tile;
import top.skyeyefast.mchjong.world.TableGeometry;
import static org.junit.jupiter.api.Assertions.*;

class RiichiAnimationTest {
    private static final UUID TABLE = new UUID(10, 20);

    private static RiichiView.Seat seat(List<Integer> hand, int drawn, List<Meld> melds, List<RiichiDiscard> river, boolean riichi) {
        return new RiichiView.Seat(false, "Player", true, false, false, 25000, hand, drawn, melds, river, List.of(), riichi, false, false);
    }

    private static RiichiView playing(RiichiPreset preset) {
        var rules = preset.config();
        var wall = new ArrayList<>(Collections.nCopies(rules.sanma() ? 108 : 136, Tile.HIDDEN));
        for (int i = 0; i < rules.players() * 13 + 1; i++) wall.set(i, Tile.ABSENT);
        var seats = new ArrayList<RiichiView.Seat>();
        seats.add(seat(IntStream.range(0, 14).boxed().toList(), 13, List.of(), List.of(), false));
        for (int i = 1; i < rules.players(); i++) seats.add(seat(Collections.nCopies(13, Tile.HIDDEN), Tile.ABSENT, List.of(), List.of(), false));
        return new RiichiView(TABLE, 2, 2, 1, rules, RiichiView.Phase.TURN, 0, 0, 0, 0, 0, 0,
            wall.size() - rules.players() * 13 - 15, 12, wall, null, seats, List.of(new RiichiAction(RiichiAction.Type.DISCARD, 13)),
            List.of(), "playing", Collections.nCopies(rules.players(), 0), List.of(), List.of(),
            top.skyeyefast.mchjong.engine.TimeControl.DEFAULT, List.of(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null, null, null, false, 1, java.util.Map.of(), List.of(), 0, 0);
    }

    private static RiichiView preparation(RiichiRules rules) {
        return new RiichiView(TABLE, 1, 1, 1, rules, RiichiView.Phase.SHUFFLE, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), null,
            Collections.nCopies(rules.players(), seat(List.of(), Tile.ABSENT, List.of(), List.of(), false)), List.of(),
            List.of(), "playing", Collections.nCopies(rules.players(), 0), List.of(), List.of(),
            top.skyeyefast.mchjong.engine.TimeControl.DEFAULT, List.of(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null, null, null, false, 1, java.util.Map.of(), List.of(), 0, 0);
    }

    private static RiichiView update(RiichiView old, List<RiichiView.Seat> seats, int viewer) {
        return new RiichiView(old.tableId(), old.revision() + 1, old.decision() + 1, old.handNumber(), old.rules(), old.phase(),
            viewer, old.dealer(), old.round(), old.honba(), old.riichiSticks(), old.turn(), old.remaining(), old.wallBreak(),
            old.wall(), old.focus(), seats, old.actions(), old.wins(), old.result(), old.deltas(), old.finalScores(), old.finalUma(),
            old.timeControl(), old.clocks(), old.finalRanks(), old.playerHandVisibility(), old.openHands(), old.exitVote(), old.handling(), old.autoPlay(), old.ronBlocked(), old.riichiHan(), old.riichiSafeTiles(), old.externalBots(), old.settlementTicks(), old.settlementSkippedSeats());
    }

    private static RiichiAnimation.Frame tile(List<RiichiAnimation.Frame> frames, int tile) {
        return frames.stream().filter(frame -> frame.piece().tile() == tile).findFirst().orElseThrow();
    }

    @Test void hiddenTsumogiriComesFromTheDrawSlotWhileTedashiComesFromTheHand() {
        for (boolean tsumogiri : List.of(false, true)) {
            var base = playing(RiichiPreset.MAHJONG_SOUL_4);
            var seats = new ArrayList<>(base.seats());
            seats.set(1, seat(Collections.nCopies(14, Tile.HIDDEN), Tile.HIDDEN, List.of(), List.of(), false));
            base = update(base, seats, 0);
            var animation = new RiichiAnimation();
            animation.accept(base, 0);
            int slot = tsumogiri ? 13 : 6;
            var source = animation.sample(0).stream().filter(frame -> frame.piece().seat() == 1
                && frame.piece().area() == RiichiTableScene.Area.HAND && frame.piece().index() == slot).findFirst().orElseThrow();
            seats.set(1, seat(Collections.nCopies(13, Tile.HIDDEN), Tile.ABSENT, List.of(),
                List.of(new RiichiDiscard(40, false, false, tsumogiri)), false));
            animation.accept(update(base, seats, 0), 100);
            var start = animation.sample(100).stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.RIVER).findFirst().orElseThrow();
            assertEquals(source.piece().position(), start.piece().position());
            assertEquals(Tile.HIDDEN, start.piece().tile());
            assertTrue(animation.cues(100).isEmpty());
            assertNotEquals(animation.settled(), animation.sample(350));
            assertEquals(tsumogiri, animation.sample(450).equals(animation.settled()));
            assertTrue(animation.sample(300).stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.HAND
                && frame.piece().seat() == 1).allMatch(frame -> frame.piece().tile() == Tile.HIDDEN));
        }
    }

    @Test void lateJoinDoesNotReplayAnOpening() {
        var animation = new RiichiAnimation();
        animation.accept(playing(RiichiPreset.MAHJONG_SOUL_4), 1000);
        assertFalse(animation.dealing(1000));
        assertEquals(1, animation.dealProgress(0, 0, 1000));
        assertEquals(animation.settled(), animation.sample(1000));
    }

    @Test void fullWallsRiseAndDealWithoutRevealingOpponentsForBothPlayerCounts() {
        for (var rules : List.of(RiichiPreset.MAHJONG_SOUL_4, RiichiPreset.MAHJONG_SOUL_3)) {
            var animation = new RiichiAnimation();
            animation.acceptLobby(TABLE, 0, 0);
            animation.accept(playing(rules), 100);
            assertTrue(animation.dealing(100));
            assertEquals(rules.sanma() ? 108 : 136, animation.sample(100).size());
            assertTrue(animation.sample(100).stream().allMatch(frame -> frame.piece().position().y < TableGeometry.FELT_Y));
            for (int time = 100; time < 3000; time += 100) {
                assertTrue(animation.sample(time).stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.HAND && frame.piece().seat() != 0)
                    .allMatch(frame -> frame.piece().tile() == Tile.HIDDEN));
            }
            assertFalse(animation.dealing(3000));
            assertEquals(animation.settled(), animation.sample(3000));
        }
    }

    @Test void cosmeticSnapshotsDoNotRestartTheDeal() {
        var animation = new RiichiAnimation();
        var playing = playing(RiichiPreset.MAHJONG_SOUL_4);
        animation.acceptLobby(TABLE, 0, 0);
        animation.accept(playing, 100);
        var midway = animation.sample(1000);
        double progress = animation.dealProgress(0, 4, 1100);
        animation.accept(update(playing, playing.seats(), 0), 1000);
        assertEquals(midway, animation.sample(1000));
        assertEquals(progress, animation.dealProgress(0, 4, 1100));
        assertEquals(animation.settled(), animation.sample(3000));
    }

    @Test void discardedTileMovesFromTheHandAndTurnsSidewaysForRiichi() {
        var playing = playing(RiichiPreset.MAHJONG_SOUL_4);
        var animation = new RiichiAnimation();
        animation.accept(playing, 0);
        var origin = tile(animation.sample(0), 13).piece().position();
        var seats = new ArrayList<>(playing.seats());
        seats.set(0, seat(IntStream.range(0, 13).boxed().toList(), Tile.ABSENT, List.of(), List.of(new RiichiDiscard(13, true, false, true)), true));
        animation.accept(update(playing, seats, 0), 100);
        var start = tile(animation.sample(100), 13);
        var middle = tile(animation.sample(250), 13);
        var end = tile(animation.sample(500), 13);
        assertEquals(origin, start.piece().position());
        assertNotEquals(origin, middle.piece().position());
        assertNotEquals(end.piece().position(), middle.piece().position());
        assertEquals(RiichiTableScene.Area.RIVER, end.piece().area());
        assertEquals(90, end.piece().yaw());
        assertEquals(-90, end.pitch());
        assertEquals(0, animation.riichiProgress(0, 100));
        assertTrue(animation.riichiProgress(0, 300) > 0 && animation.riichiProgress(0, 300) < 1);
        assertEquals(1, animation.riichiProgress(0, 600));
    }

    @Test void calledTileTravelsFromTheRiverIntoTheCallersMeld() {
        var base = playing(RiichiPreset.MAHJONG_SOUL_4);
        var seats = new ArrayList<>(base.seats());
        seats.set(0, seat(IntStream.range(0, 13).boxed().toList(), Tile.ABSENT, List.of(), List.of(new RiichiDiscard(13, false, false, true)), false));
        var discarded = update(base, seats, 0);
        var animation = new RiichiAnimation();
        animation.accept(discarded, 0);
        var origin = tile(animation.sample(0), 13).piece().position();
        seats.set(0, seat(seats.getFirst().hand(), Tile.ABSENT, List.of(), List.of(new RiichiDiscard(13, false, true, true)), false));
        seats.set(1, seat(Collections.nCopies(11, Tile.HIDDEN), Tile.ABSENT, List.of(new Meld(Meld.Type.TRIPLET, List.of(13, 14, 15), 0, 13)), List.of(), false));
        animation.accept(update(discarded, seats, 0), 100);
        assertEquals(origin, tile(animation.sample(100), 13).piece().position());
        assertEquals(RiichiTableScene.Area.MELD, tile(animation.sample(600), 13).piece().area());
        assertEquals(1, tile(animation.sample(600), 13).piece().seat());
    }

    @Test void changingToSpectatorDropsAllPreviouslyPrivateAnimationFrames() {
        var base = playing(RiichiPreset.MAHJONG_SOUL_4);
        var animation = new RiichiAnimation();
        animation.acceptLobby(TABLE, 0, 0);
        animation.accept(base, 100);
        var seats = new ArrayList<>(base.seats());
        seats.set(0, seat(Collections.nCopies(14, Tile.HIDDEN), Tile.HIDDEN, List.of(), List.of(), false));
        animation.accept(update(base, seats, -1), 1200);
        assertFalse(animation.dealing(1200));
        assertTrue(animation.sample(1200).stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.HAND)
            .allMatch(frame -> frame.piece().tile() == Tile.HIDDEN));
    }

    @Test void meldPieceIndicesStayUniqueAcrossMultipleMeldsAndAddedKans() {
        var base = playing(RiichiPreset.MAHJONG_SOUL_4);
        var seats = new ArrayList<>(base.seats());
        seats.set(0, seat(List.of(0, 1, 2, 3, 4, 5, 6), Tile.ABSENT,
            List.of(new Meld(Meld.Type.TRIPLET, List.of(40, 41, 42), 1, 40), new Meld(Meld.Type.ADDED_QUAD, List.of(80, 81, 82, 83), 2, 80)), List.of(), false));
        var pieces = RiichiTableScene.build(update(base, seats, 0)).stream().filter(piece -> piece.area() == RiichiTableScene.Area.MELD).toList();
        assertEquals(7, pieces.stream().map(RiichiTableScene.Piece::index).distinct().count());
        assertTrue(pieces.stream().allMatch(piece -> Math.abs(piece.position().y
            - TableGeometry.FELT_Y - TileDimensions.SMALL.depth() / 2) < 1e-7));
    }

    @Test void repeatedSnapshotsCannotRewindAnOpeningOrItsViewingPermissions() {
        var animation = new RiichiAnimation();
        var base = playing(RiichiPreset.MAHJONG_SOUL_4);
        animation.acceptLobby(TABLE, 0, 0);
        animation.accept(base, 100);
        var middle = animation.sample(900);
        animation.accept(base, 900);
        assertEquals(middle, animation.sample(900));
        assertTrue(animation.dealing(900));
    }

    @Test void interruptedDiscardStartsItsMeldFlightAtTheCurrentAnimatedPosition() {
        var base = playing(RiichiPreset.MAHJONG_SOUL_4);
        var animation = new RiichiAnimation();
        animation.accept(base, 0);
        var seats = new ArrayList<>(base.seats());
        seats.set(0, seat(IntStream.range(0, 13).boxed().toList(), Tile.ABSENT, List.of(),
            List.of(new RiichiDiscard(13, false, false, true)), false));
        var discarded = update(base, seats, 0);
        animation.accept(discarded, 100);
        var moving = tile(animation.sample(220), 13);
        seats.set(0, seat(seats.getFirst().hand(), Tile.ABSENT, List.of(),
            List.of(new RiichiDiscard(13, false, true, true)), false));
        seats.set(1, seat(Collections.nCopies(11, Tile.HIDDEN), Tile.ABSENT,
            List.of(new Meld(Meld.Type.TRIPLET, List.of(13, 14, 15), 0, 13)), List.of(), false));
        animation.accept(update(discarded, seats, 0), 220);
        var called = tile(animation.sample(220), 13);
        assertEquals(moving.piece().position(), called.piece().position());
        assertEquals(moving.pitch(), called.pitch());
        assertEquals(moving.piece().yaw(), called.piece().yaw());
        assertEquals(RiichiTableScene.Area.MELD, called.piece().area());
        assertEquals(animation.settled(), animation.sample(1000));
    }

    @Test void openingHasOneHiddenTilePerWallSlotAndDealsEarlierPacketsFirst() {
        for (var rules : List.of(RiichiPreset.MAHJONG_SOUL_4, RiichiPreset.MAHJONG_SOUL_3)) {
            var animation = new RiichiAnimation();
            animation.acceptLobby(TABLE, 0, 0);
            animation.accept(playing(rules), 100);
            var wall = animation.sample(100);
            assertEquals(wall.size(), wall.stream().map(frame -> frame.piece().position()).distinct().count());
            assertTrue(wall.stream().allMatch(frame -> frame.piece().tile() == Tile.HIDDEN && frame.pitch() == 90));
            var halfway = animation.sample(900);
            assertEquals(1, animation.dealProgress(0, 0, 900));
            assertTrue(animation.dealProgress(1, 0, 900) > 0 && animation.dealProgress(1, 0, 900) < 1);
            assertEquals(0, animation.dealProgress(0, 12, 900));
            assertEquals(0, tile(halfway, 0).pitch());
            var last = halfway.stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.HAND
                && frame.piece().seat() == 0 && frame.piece().index() == 12).findFirst().orElseThrow();
            assertEquals(Tile.HIDDEN, last.piece().tile());
            assertEquals(90, last.pitch(), "Undealt tiles must keep their physical back facing up");
        }
    }

    @Test void rollingDiceReindexesTheBuiltWallWithoutMovingIt() {
        for (var preset : List.of(RiichiPreset.MAHJONG_SOUL_4, RiichiPreset.MAHJONG_SOUL_3)) {
            var base = preparation(preset.config());
            int players = preset.players(), size = preset.sanma() ? 108 : 136;
            var wall = Collections.nCopies(size, Tile.HIDDEN);
            var handling = new RiichiView.Handling((1 << players) - 1, -1, 0, 0, 0, false);
            var built = new RiichiView(base.tableId(), 2, 2, 1, base.rules(), RiichiView.Phase.BUILD_WALL, 0,
                0, 0, 0, 0, 0, size - 14, 0, wall, null, base.seats(), List.of(), List.of(), "playing",
                Collections.nCopies(players, 0), List.of(), List.of(), base.timeControl(), List.of(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null,
                handling, null, false, 1, java.util.Map.of(), List.of(), 0, 0);
            int wallBreak = 2 * (size / (2 * players) + 3);
            var opened = new RiichiView(base.tableId(), 3, 3, 1, base.rules(), RiichiView.Phase.DEAL, 0,
                0, 0, 0, 0, 0, size - 14, wallBreak, wall, null, base.seats(), List.of(), List.of(), "playing",
                Collections.nCopies(players, 0), List.of(), List.of(), base.timeControl(), List.of(), List.of(), top.skyeyefast.mchjong.engine.PlayerHandVisibility.SELF, false, null,
                new RiichiView.Handling((1 << players) - 1, 0, 4, 3, 4, false), null, false, 1, java.util.Map.of(), List.of(), 0, 0);
            var animation = new RiichiAnimation();
            animation.accept(built, 0);
            var positions = animation.sample(0).stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.WALL)
                .map(frame -> frame.piece().position()).collect(java.util.stream.Collectors.toSet());
            animation.accept(opened, 100);
            assertEquals(positions, animation.sample(100).stream().filter(frame -> frame.piece().area() == RiichiTableScene.Area.WALL)
                .map(frame -> frame.piece().position()).collect(java.util.stream.Collectors.toSet()));
            assertFalse(animation.moving(101));
        }
    }
}

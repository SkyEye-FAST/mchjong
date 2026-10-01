package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConvenienceHintsTest {
    @Test void everyRoomSharesHostPolicyPersistenceAndVariantSelection() {
        UUID host = UUID.randomUUID(), guest = UUID.randomUUID();
        for (TableSession session : List.of(new RiichiSession(UUID.randomUUID(), RiichiPreset.TENHOU_4, 1),
                new McrSession(UUID.randomUUID(), 1), new SichuanSession(UUID.randomUUID(), 1))) {
            session.join(host, "Host", 0); session.join(guest, "Guest", 1);
            assertFalse(session.roomView(host).convenienceHints());
            assertFalse(session.configureConvenienceHints(guest, session.decision(), true));
            assertFalse(session.configureConvenienceHints(host, session.decision() - 1, true));
            assertTrue(session.configureConvenienceHints(host, session.decision(), true));
            assertTrue(session.roomView(guest).convenienceHints());
            var restored = TableSessionCodec.restore(TableSessionCodec.save(session));
            assertTrue(restored.convenienceHints());
            var next = session.selectVariant(host, session.decision(),
                session.variant() == MahjongVariant.MCR ? MahjongVariant.SICHUAN : MahjongVariant.MCR);
            assertNotNull(next); assertTrue(next.convenienceHints());
            restored.configureWorld(new WorldPolicy(false, false, true, 5000, true, true, true, true, null));
            assertFalse(restored.convenienceHints());
            assertFalse(restored.roomView(host).allowConvenienceHints());
            assertFalse(restored.configureConvenienceHints(host, restored.decision(), true));
            // Room changes remain preparation-only for every concrete runtime.
            session.lifecycle = TableSession.Lifecycle.PLAYING;
            assertFalse(session.configureConvenienceHints(host, session.decision(), false));
        }
    }

    @Test void mcrDistinguishesStructuralLowFanAndQualifyingWaitsWithoutFlowers() {
        var hints = new McrHints();
        var low = hints.preview(mcr("445566m2277779s", List.of(), 8, Tile.ABSENT), Tile.ABSENT);
        assertEquals(0, low.shanten());
        var wait = low.tiles().stream().filter(tile -> tile.kind() == Tile.parseKind("8s")).findFirst().orElseThrow();
        assertEquals(7, wait.discardFan());
        assertTrue(wait.drawFan() >= 8);
        var high = hints.preview(mcr("19m19p19s1234567z", List.of(), 0, Tile.ABSENT), Tile.ABSENT);
        assertEquals(13, high.tiles().size());
        assertTrue(high.tiles().stream().allMatch(tile -> tile.discardFan() >= 8));
        var after = hints.preview(mcr("19m19p19s1234567z2m", List.of(), 0, 4), 4);
        assertTrue(after.discard()); assertEquals(0, after.shanten());
        assertEquals(high.tiles(), after.tiles());
    }

    @Test void publicCopiesIncludeDiscardAliasesAndExhaustedStructuralWaits() {
        var river = List.of(new McrDiscard(105, false, false), new McrDiscard(106, false, false),
            new McrDiscard(107, false, false));
        var preview = new McrHints().preview(mcr("19m19p19s1234567z", river, 0, Tile.ABSENT), Tile.ABSENT);
        assertEquals(0, preview.tiles().stream().filter(tile -> tile.kind() == 26).findFirst().orElseThrow().remaining());
        assertNull(new McrHints().preview(new McrGame(711).view(-1), Tile.ABSENT));
    }

    @Test void mcrHintsIgnoreDifferentOpponentHandsAndFutureWallOrder() {
        var first = McrGameTest.fixed(1, new McrGameTest.Fixture().hand(0, "19m19p19s1234567z2m").build());
        var second = McrGameTest.fixed(1, new McrGameTest.Fixture().hand(0, "19m19p19s1234567z2m")
            .hand(1, "222333444m1122p").tail(0, Tile.RED).build());
        int discard1 = first.hand(0).stream().filter(tile -> Tile.kind(tile) == 1).findFirst().orElseThrow();
        int discard2 = second.hand(0).stream().filter(tile -> Tile.kind(tile) == 1).findFirst().orElseThrow();
        assertNotEquals(first.hand(1), second.hand(1));
        assertNotEquals(first.save().wall(), second.save().wall());
        assertEquals(new McrHints().preview(first.view(0), discard1), new McrHints().preview(second.view(0), discard2));
        assertNotNull(new McrHints().preview(first.view(0), discard1));
    }

    @Test void sichuanVoidTilesReadyValuesCapsAndPassedFanUseCurrentScorer() {
        var hints = new SichuanHints();
        var missing = hints.preview(sichuan("123456789m1239s", 2, -1, Tile.ABSENT), Tile.ABSENT);
        assertEquals(4, missing.voidTiles().size()); assertTrue(missing.tiles().isEmpty());
        var ready = hints.preview(sichuan("111222333m4445p", 2, 1, Tile.ABSENT), Tile.ABSENT);
        assertEquals(0, ready.shanten()); assertEquals(2, ready.readyValue()); assertEquals(1, ready.passedFan());
        assertTrue(ready.tiles().stream().anyMatch(tile -> tile.fan() == 1 && tile.value() == 2));
        var flush = hints.preview(sichuan("1111222233334m", 2, -1, Tile.ABSENT), Tile.ABSENT);
        assertEquals(8, flush.readyValue());
        assertTrue(flush.tiles().stream().anyMatch(tile -> tile.fan() > 3 && tile.value() == 8));
        var view = sichuan("111222333m4445p9s", 2, -1, 104);
        var after = hints.preview(view, 104);
        assertTrue(after.voidTiles().isEmpty()); assertTrue(after.discard()); assertEquals(ready.tiles(), after.tiles());
        assertEquals(SichuanHandAnalyzer.readyValue(view.seats().get(0).hand().stream().filter(tile -> tile != 104).toList(),
            List.of(), 2, view.rules()), after.readyValue());
    }

    private static McrView mcr(String notation, List<McrDiscard> river, int flowers, int discard) {
        var base = new McrGame(711).view(0);
        var seats = new ArrayList<>(base.seats());
        seats.set(0, new McrView.Seat(Tile.EAST, 0, TestHands.tiles(notation), Tile.ABSENT, List.of(), List.of(),
            java.util.stream.IntStream.range(136, 136 + flowers).boxed().toList(), false));
        var other = seats.get(1);
        seats.set(1, new McrView.Seat(other.wind(), 0, other.hand(), other.drawn(), List.of(), river, List.of(), false));
        return new McrView(1, 1, 1, McrGame.Phase.TURN, 0, 0, Tile.EAST, discard >= 0 ? 0 : 1,
            base.remaining(), base.opening(), base.wall(), null, seats,
            discard >= 0 ? List.of(new McrAction(McrAction.Type.DISCARD, discard)) : List.of(), false, false, null, List.of());
    }

    private static SichuanView sichuan(String notation, int voidSuit, int passedFan, int discard) {
        var base = new SichuanGame(711).view(0);
        var seats = new ArrayList<>(base.seats());
        seats.set(0, new SichuanView.Seat(TestHands.tiles(notation), List.of(), List.of(), voidSuit, false, Tile.ABSENT, Tile.ABSENT));
        return new SichuanView(1, 1, base.rules(), SichuanGame.Phase.TURN, 1, 0, base.scores(), 0, discard >= 0 ? 0 : 1,
            base.wall(), seats, Tile.ABSENT, -1, false, false,
            discard >= 0 ? List.of(new SichuanAction(SichuanAction.Type.DISCARD, List.of(discard))) : List.of(),
            List.of(), List.of(), null, passedFan);
    }
}

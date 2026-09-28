package top.skyeyefast.mchjong.engine;

import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.McrAction.Type.*;
import static top.skyeyefast.mchjong.engine.McrGameTest.*;

class McrViewTest {
    @Test void handsDrawsAndConcealedKongsAreRedactedBeforeSerialization() {
        var game = new McrGame(14, new Fixture().hand(0, "1111m123456p78s22z")
            .at(143, Tile.parseKind("9s")).build());
        play(game, 0, CONCEALED_KONG);
        var before = game.view(-1);
        for (int viewer = -1; viewer < 4; viewer++) {
            var view = game.view(viewer);
            roundTrip(view);
            assertTrue(view.wall().stream().allMatch(tile -> tile == Tile.HIDDEN || tile == Tile.ABSENT));
            assertEquals(game.remaining(), view.wall().stream().filter(tile -> tile == Tile.HIDDEN).count());
            assertEquals(viewer == -1 ? List.of() : game.actions(viewer), view.actions());
            for (int seat = 0; seat < 4; seat++) {
                if (seat == viewer) assertEquals(game.hand(seat), view.seats().get(seat).hand());
                else assertHidden(view.seats().get(seat));
            }
            var kong = view.seats().get(0).melds().get(0);
            if (viewer == 0) assertEquals(game.melds(0).get(0), kong);
            else assertEquals(List.of(Tile.HIDDEN, Tile.HIDDEN, Tile.HIDDEN, Tile.HIDDEN), kong.tiles());
        }
        assertThrows(UnsupportedOperationException.class, () -> before.seats().get(0).hand().clear());
        play(game, 0, WIN);
        var finished = game.view(-1);
        roundTrip(finished);
        assertInstanceOf(McrSettlement.Win.class, finished.result());
        assertEquals(game.hand(0), finished.seats().get(0).hand());
        assertEquals(game.melds(0), finished.seats().get(0).melds());
        for (int seat = 1; seat < 4; seat++) assertHidden(finished.seats().get(seat));
        assertHidden(before.seats().get(0)); // A later reveal cannot mutate an earlier recipient view.
    }

    @Test void responseProgressIsPrivateAndAddedKongFocusIsPublic() {
        var game = new McrGame(4, new Fixture().hand(0, "279m147p258s2345z5m")
            .hand(1, "123456789p11s46m").hand(2, "123456789s22p46m")
            .hand(3, "555m123789m123p1z").build());
        discardKind(game, 0, "5m");
        var before = game.view(1);
        play(game, 2, WIN);
        var after = game.view(1);
        assertEquals(before.revision() + 1, after.revision());
        assertEquals(before.decision(), after.decision());
        assertEquals(before.actions(), after.actions());
        assertEquals(before.seats(), after.seats());
        assertEquals(before.focus(), after.focus());
        assertFalse(after.responded());
        assertTrue(game.view(2).responded());
        assertTrue(game.view(2).actions().isEmpty());
        assertFalse(game.view(-1).responded());
        roundTrip(after);
        roundTrip(game.view(2));
        String json = McrCodec.encodeView(game.view(-1));
        assertFalse(json.contains("replies"));
        assertFalse(json.contains("seed"));

        var added = addedKongPosition();
        int tile = added.drawn(1), remaining = added.remaining();
        play(added, 1, MELDED_KONG);
        var observer = added.view(-1);
        assertEquals(new McrView.Focus(1, tile, true), observer.focus());
        assertEquals(remaining, observer.remaining());
        assertHidden(observer.seats().get(1));
        assertEquals(added.melds(1), observer.seats().get(1).melds());
        roundTrip(observer);
    }

    @Test void lowFanDeclarationRemainsVisibleAndPenaltyDoesNotBecomeAWinResult() {
        var game = new McrGame(5, new Fixture().hand(0, "12345m567p789s11z6m").build());
        assertFalse(game.score(0).meetsMinimum());
        assertTrue(game.view(0).actions().stream().anyMatch(action -> action.type() == WIN));
        play(game, 0, WIN);
        var self = game.view(0);
        assertTrue(self.seats().get(0).winForbidden());
        assertTrue(self.actions().stream().anyMatch(action -> action.type() == DISCARD));
        assertFalse(self.actions().stream().anyMatch(action -> action.type() == WIN));
        var observer = game.view(-1);
        assertNull(observer.result());
        assertEquals(List.of(-30, 10, 10, 10), observer.seats().stream().map(McrView.Seat::points).toList());
        assertEquals(game.penalties(), observer.penalties());
        assertHidden(observer.seats().get(0));
        roundTrip(self);
        roundTrip(observer);
        var restored = McrCodec.restore(McrCodec.save(game)).view(0);
        assertEquals(self.seats(), restored.seats());
        assertEquals(self.actions(), restored.actions());
        assertEquals(self.penalties(), restored.penalties());
    }

    @Test void flowersArePublicButPrivateSavesCannotBeUsedAsViews() {
        var game = new McrGame(2, new Fixture().hand(1, "19m19p19s1234567z")
            .at(53, FlowerTile.SPRING.id()).at(143, FlowerTile.SUMMER.id()).at(142, 0).build());
        discard(game, 0, game.drawn(0));
        passAll(game);
        play(game, 1, DRAW);
        var observer = game.view(-1);
        assertEquals(game.flowers(1), observer.seats().get(1).flowers());
        assertEquals(Tile.HIDDEN, observer.seats().get(1).drawn());
        roundTrip(observer);
        String encoded = McrCodec.encodeView(observer);
        assertThrows(IllegalArgumentException.class, () -> McrCodec.restore(encoded));
        assertThrows(IllegalArgumentException.class, () -> McrCodec.decodeView(McrCodec.save(game)));
        assertThrows(IllegalArgumentException.class, () -> game.view(4));
        assertThrows(IllegalArgumentException.class, () -> game.view(-2));
        var leaked = JsonParser.parseString(encoded).getAsJsonObject();
        leaked.getAsJsonArray("seats").get(0).getAsJsonObject().getAsJsonArray("hand")
            .set(0, JsonParser.parseString("0"));
        assertThrows(IllegalArgumentException.class, () -> McrCodec.decodeView(leaked.toString()));
        exhaust(game);
        var draw = game.view(-1);
        assertInstanceOf(McrSettlement.Draw.class, draw.result());
        assertTrue(draw.actions().isEmpty());
        draw.seats().forEach(McrViewTest::assertHidden);
        roundTrip(draw);
        var earlyEnd = JsonParser.parseString(McrCodec.encodeView(draw)).getAsJsonObject();
        earlyEnd.addProperty("phase", "MATCH_END");
        assertThrows(IllegalArgumentException.class, () -> McrCodec.decodeView(earlyEnd.toString()));
    }

    private static void roundTrip(McrView view) { assertEquals(view, McrCodec.decodeView(McrCodec.encodeView(view))); }

    private static void assertHidden(McrView.Seat seat) {
        assertTrue(seat.hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
        assertTrue(seat.drawn() == Tile.HIDDEN || seat.drawn() == Tile.ABSENT);
    }
}

package top.skyeyefast.mchjong.client;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.*;
import static org.junit.jupiter.api.Assertions.*;

class TableLayoutTest {
    private static TableView start(RuleSet rules) {
        UUID player = new UUID(10, 15);
        var game = new Game(UUID.randomUUID(), rules, 15);
        assertTrue(game.join(player, "Test", 0));
        var view = game.view(player);
        int action = java.util.stream.IntStream.range(0, view.actions().size())
            .filter(i -> view.actions().get(i).type() == Action.Type.PRACTICE).findFirst().orElseThrow();
        assertTrue(game.act(player, view.decision(), action));
        return game.view(player);
    }

    private static TableView replace(TableView v, List<Integer> hand, List<Meld> melds, List<Discard> river) {
        var seats = new ArrayList<>(v.seats());
        seats.set(0, new TableView.Seat("Test", true, false, false, 25000, hand, Tile.ABSENT,
            melds, river, List.of(), false, false));
        return new TableView(v.tableId(), v.revision() + 1, v.decision(), v.handNumber(), v.rules(), v.phase(), v.viewerSeat(),
            v.dealer(), v.round(), v.honba(), v.riichiSticks(), v.turn(), v.remaining(), v.wallBreak(), v.wall(), v.focus(),
            seats, v.actions(), v.wins(), v.result(), v.deltas(), v.finalScores(), v.timeControl(), v.clocks(), v.finalRanks(), v.openHands(), v.exitVote());
    }

    @Test void theFirstMeldDoesNotMoveTheConcealedHandOrigin() {
        for (RuleSet rules : RuleSet.values()) {
            var view = start(rules);
            var hand = List.of(0, 1, 2, 4, 5, 6, 8, 9, 10, 12, 13, 14, 16);
            var before = TableScene.build(replace(view, hand, List.of(), List.of())).stream()
                .filter(piece -> piece.area() == TableScene.Area.HAND && piece.seat() == 0 && piece.index() == 0).findFirst().orElseThrow();
            var after = TableScene.build(replace(view, hand.subList(3, hand.size()),
                List.of(new Meld(Meld.Type.PON, List.of(0, 1, 2), 1, 0)), List.of())).stream()
                .filter(piece -> piece.area() == TableScene.Area.HAND && piece.seat() == 0 && piece.index() == 0).findFirst().orElseThrow();
            assertEquals(before.position(), after.position());
            assertEquals(TableScene.HAND_LEFT, before.position().x, 1e-6);
        }
    }

    @Test void riverSlotsCloseCalledGapsWithoutLosingDiscardIdentityOrOverlappingRiichi() {
        var discards = new ArrayList<Discard>();
        for (int i = 0; i < 26; i++) discards.add(new Discard(i, i == 2, i == 1 || i == 9, false));
        var view = replace(start(RuleSet.TENHOU_4), List.of(80, 81), List.of(), discards);
        var river = TableScene.build(view).stream().filter(piece -> piece.area() == TableScene.Area.RIVER && piece.seat() == 0).toList();
        assertEquals(24, river.size());
        assertEquals(2, river.get(1).index());
        assertEquals(90, river.get(1).yaw());
        for (int i = 1; i < river.size(); i++) {
            if (i % 6 == 0) assertEquals(TableScene.RIVER_ROW, river.get(i).position().z - river.get(i - 1).position().z, 1e-6);
            else {
                double widths = (river.get(i).yaw() == 90 ? .160 : .104) + (river.get(i - 1).yaw() == 90 ? .160 : .104);
                assertTrue(river.get(i).position().x - river.get(i - 1).position().x > widths * TableScene.TILE_SCALE / 2);
                assertEquals(river.get(i).position().z, river.get(i - 1).position().z);
            }
        }
    }

    @Test void hidingRiversAlwaysShowsRemainingTilesAndResetRestoresDefaults() {
        var settings = new TableSettings();
        settings.toggle(TableSettings.Information.REMAINING);
        assertFalse(settings.show(TableSettings.Information.REMAINING));
        settings.showRiver = false;
        assertTrue(settings.show(TableSettings.Information.REMAINING));
        settings.toggle(TableSettings.Information.REMAINING);
        settings.toggle(TableSettings.Information.REMAINING);
        assertTrue(settings.show(TableSettings.Information.REMAINING));
        settings.showRiver = true;
        assertFalse(settings.show(TableSettings.Information.REMAINING));
        settings.reset();
        assertTrue(settings.showRiver);
        assertTrue(settings.show(TableSettings.Information.REMAINING));
    }

    @Test void riverVisibilityAndRemainingPreferencesSurviveSaving(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var settings = new TableSettings();
        settings.toggle(TableSettings.Information.REMAINING);
        settings.showRiver = false;
        var path = directory.resolve("table.json");
        settings.save(path);
        var restored = TableSettings.load(path);
        assertFalse(restored.showRiver);
        assertTrue(restored.show(TableSettings.Information.REMAINING));
        restored.showRiver = true;
        assertFalse(restored.show(TableSettings.Information.REMAINING));
    }
}

package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ManualHandlingTest {
    private static final Gson JSON = new Gson();
    private static UUID id(int seat) { return new UUID(812, seat); }

    private static Game game(RuleSet rules, boolean manual) {
        Game game = new Game(UUID.randomUUID(), rules, 8192);
        assertTrue(game.configureEquipment(manual, Tile.set(false)));
        for (int seat = 0; seat < rules.players(); seat++) {
            assertTrue(game.join(id(seat), "Player " + seat, seat));
            act(game, seat, Action.Type.READY);
        }
        return game;
    }

    private static void act(Game game, int seat, Action.Type action) {
        TableView view = game.view(id(seat));
        int index = Game.indexOf(view.actions(), action);
        assertTrue(index >= 0, () -> action + " is not offered in " + game.phase());
        assertTrue(game.act(id(seat), view.decision(), index));
        assertFalse(game.act(id(seat), view.decision(), index), "Replayed packet changed physical state");
        game.validate();
    }

    private static Game reload(Game game) {
        Game restored = JSON.fromJson(JSON.toJson(game), Game.class);
        restored.validate();
        assertEquals(JSON.toJson(game.view(null)), JSON.toJson(restored.view(null)));
        return restored;
    }

    private static void concealed(Game game) {
        TableView view = game.view(null);
        assertTrue(view.actions().isEmpty());
        assertTrue(view.seats().stream().filter(seat -> !seat.exposed()).flatMap(seat -> seat.hand().stream()).allMatch(tile -> tile == Tile.HIDDEN));
        assertFalse(JSON.toJson(view).contains("suppliedTiles"));
        assertFalse(JSON.toJson(view).contains("seed"));
    }

    @Test void everyPresetRequiresExplicitHandlingAndSurvivesReloadAtEveryStep() {
        for (RuleSet rules : RuleSet.values()) {
            Game game = game(rules, true);
            assertEquals(Game.Phase.SHUFFLE, game.phase());
            assertNull(game.wall);
            for (int tick = 0; tick < 2400; tick++) game.tick();
            assertEquals(Game.Phase.SHUFFLE, game.phase());
            act(game, game.dealer, Action.Type.SHUFFLE);
            assertTrue(game.view(null).wall().stream().allMatch(tile -> tile == Tile.ABSENT));
            for (int seat = 0; seat < rules.players(); seat++) {
                act(game, seat, Action.Type.BUILD_WALL);
                assertEquals((seat + 1L) * Tile.set(rules.sanma()).size() / rules.players(),
                    game.view(null).wall().stream().filter(tile -> tile == Tile.HIDDEN).count());
                game = reload(game);
            }
            assertEquals(Game.Phase.DEAL, game.phase());
            for (int packet = 0; packet < 4 * rules.players(); packet++) {
                assertTrue(game.view(id(game.next(game.turn))).actions().isEmpty());
                act(game, game.turn, Action.Type.TAKE_PACKET);
                game = reload(game);
                concealed(game);
            }
            assertEquals(Game.Phase.DRAW, game.phase());
            for (int seat = 0; seat < rules.players(); seat++) assertEquals(13, game.players[seat].hand.size());
            int remaining = game.wall.remaining();
            for (int tick = 0; tick < 2400; tick++) game.tick();
            assertEquals(remaining, game.wall.remaining(), "Human draw must not happen on a timeout");
            act(game, game.dealer, Action.Type.DRAW);
            assertEquals(14, game.players[game.dealer].hand.size());
            assertEquals(remaining - 1, game.wall.remaining());
            assertEquals(Game.Phase.TURN, game.phase());
            concealed(game);
        }
    }

    @Test void manualAndAutomaticDealUseTheSameSuppliedTiles() {
        for (RuleSet rules : RuleSet.values()) {
            Game manual = game(rules, true), automatic = game(rules, false);
            act(manual, manual.dealer, Action.Type.SHUFFLE);
            for (int seat = 0; seat < rules.players(); seat++) act(manual, seat, Action.Type.BUILD_WALL);
            for (int packet = 0; packet < 4 * rules.players(); packet++) act(manual, manual.turn, Action.Type.TAKE_PACKET);
            act(manual, manual.dealer, Action.Type.DRAW);
            for (int seat = 0; seat < rules.players(); seat++) assertEquals(automatic.players[seat].hand, manual.players[seat].hand);
            assertEquals(automatic.wall.tiles, manual.wall.tiles);
            assertFalse(manual.configureEquipment(false, List.of()), "Cannot unload a set during a match");
        }
    }

    @Test void emptyOrInvalidEquipmentCannotStartADeal() {
        Game game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 1);
        assertTrue(game.configureEquipment(true, List.of()));
        assertTrue(game.join(id(0), "Host", 0));
        assertFalse(game.equipped());
        assertTrue(game.view(id(0)).actions().stream().noneMatch(action -> action.type() == Action.Type.READY || action.type() == Action.Type.PRACTICE));
        assertThrows(IllegalArgumentException.class, () -> game.configureEquipment(true, java.util.Collections.nCopies(136, 0)));
        assertThrows(IllegalArgumentException.class, () -> game.configureEquipment(true, Tile.set(true)));
        assertTrue(game.configureEquipment(true, Tile.set(false)));
        assertTrue(game.equipped());
        game.validate();
    }

    @Test void replacementDrawWaitsForPlayerAndSurvivesReload() {
        Game game = game(RuleSet.MAHJONG_SOUL_3, true);
        act(game, game.dealer, Action.Type.SHUFFLE);
        for (int seat = 0; seat < game.rules.players(); seat++) act(game, seat, Action.Type.BUILD_WALL);
        for (int packet = 0; packet < 4 * game.rules.players(); packet++) act(game, game.turn, Action.Type.TAKE_PACKET);
        game.draw(game.dealer, true, true);
        int remaining = game.wall.remaining();
        assertEquals(0, game.wall.replacementIndex);
        game = reload(game);
        assertEquals(Game.Phase.DRAW, game.phase());
        assertTrue(game.handling.replacement);
        assertTrue(game.handling.kan);
        assertEquals(13, game.players[game.dealer].hand.size());
        act(game, game.dealer, Action.Type.DRAW);
        assertEquals(1, game.wall.replacementIndex);
        assertEquals(remaining - 1, game.wall.remaining());
        assertTrue(game.players[game.dealer].rinshan);
    }

    @Test void manualPracticeBotsAdvanceHandlingAndAWholeHandConservesTilesAndArchives() {
        for (RuleSet rules : RuleSet.values()) {
            Game game = game(rules, true);
            for (var player : game.players) player.bot = true;
            int ticks = 0;
            while (game.pendingReplays().isEmpty() && ticks++ < 50_000) {
                game.tick();
                game.validate();
                concealed(game);
            }
            assertFalse(game.pendingReplays().isEmpty(), "Manual game never completed: " + rules);
            var replay = game.pendingReplays().getFirst();
            assertEquals(1, replay.hands().size());
        }
    }
}

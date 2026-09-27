package top.skyeyefast.mchjong.engine;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.junit.jupiter.api.Assertions.*;

class MjaiTest {
    private static final Gson JSON = new Gson();

    @Test void recipientHistoryHidesOtherHandsDrawsAndSeedsEvenInOpenRooms() {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74291);
        game.handVisibility = HandVisibility.OPEN;
        var events = game.mjaiPosition(1).events();
        var start = JSON.toJsonTree(events.get(0)).getAsJsonObject();
        var hands = start.getAsJsonArray("tehais");
        for (int seat = 0; seat < 4; seat++) {
            for (var tile : hands.get(seat).getAsJsonArray()) assertEquals(seat != 1, tile.getAsString().equals("?"));
        }
        assertEquals("?", events.get(events.size() - 1).get("pai"));
        assertFalse(JSON.toJson(events).contains("seed"));
        assertFalse(JSON.toJson(events).contains("wall"));
        var restored = JSON.fromJson(JSON.toJson(game), Game.class);
        restored.validate();
        assertEquals(events, restored.mjaiPosition(1).events());
    }

    @Test void sanmaHistoryAndNorthExtractionUseThreeSeatsAndLegalActions() {
        var game = GameLifecycleTest.started(RuleSet.MAHJONG_SOUL_3, 74291);
        var history = game.mjaiPosition(1).events();
        var start = JSON.toJsonTree(history.getFirst()).getAsJsonObject();
        assertEquals(4, start.getAsJsonArray("scores").size());
        assertEquals(4, start.getAsJsonArray("tehais").size());
        assertEquals(0, start.getAsJsonArray("scores").get(3).getAsInt());
        for (var tile : start.getAsJsonArray("tehais").get(3).getAsJsonArray()) assertEquals("?", tile.getAsString());
        for (int seat = 0; seat < 3; seat++)
            assertEquals(seat != 1, start.getAsJsonArray("tehais").get(seat).getAsJsonArray().get(0).getAsString().equals("?"));
        var north = Tile.id(Tile.NORTH, 0, false);
        var event = new ReplayHand.Event(ReplayHand.Kind.NUKI, 2, north, null, false, false, true);
        var events = MjaiProtocol.hand(1, 4, 2, 0, 0, List.of(35000, 35000, 35000),
            List.of(game.players[0].hand, game.players[1].hand, game.players[2].hand), List.of(north), List.of(event));
        assertEquals("S", events.getFirst().get("bakaze"));
        assertEquals(2, events.getFirst().get("kyoku"));
        assertEquals(Map.of("type", "nukidora", "actor", 2, "pai", "N"), events.getLast());

        var turn = TrainingBotTest.hand("234567p234567s4z1z");
        turn.rules = RuleSet.MAHJONG_SOUL_3.config();
        turn.wall = new Wall(turn.rules, 24, 0);
        turn.players[0].firstTurn = false;
        turn.options.set(0, LegalActions.onTurn(turn, 0));
        var view = turn.view(turn.players[0].id);
        var response = JSON.toJsonTree(Map.of("type", "nukidora", "actor", 0, "pai", "N")).getAsJsonObject();
        assertEquals(Action.Type.NUKI, view.actions().get(MjaiProtocol.action(view, response, false)).type());
        response.addProperty("pai", "E");
        assertThrows(IllegalArgumentException.class, () -> MjaiProtocol.action(view, response, false));
        response.addProperty("pai", "N");
        assertThrows(IllegalArgumentException.class, () -> MjaiProtocol.action(view, response, true));
    }

    @Test void responseCannotChooseAnotherActorTileOrDiscardIdentity() {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74291);
        var view = game.view(game.players[game.turn].id);
        var discard = view.actions().stream().filter(a -> a.type() == Action.Type.DISCARD).findFirst().orElseThrow();
        int tile = discard.tiles().getFirst();
        var response = JSON.toJsonTree(Map.of("type", "dahai", "actor", game.turn, "pai", MjaiProtocol.tile(tile),
            "tsumogiri", tile == game.players[game.turn].drawn)).getAsJsonObject();
        assertEquals(discard, view.actions().get(MjaiProtocol.action(view, response, false)));
        response.addProperty("actor", (game.turn + 1) % 4);
        assertThrows(IllegalArgumentException.class, () -> MjaiProtocol.action(view, response, false));
        response.addProperty("actor", game.turn);
        response.addProperty("pai", "?");
        assertThrows(IllegalArgumentException.class, () -> MjaiProtocol.action(view, response, false));
    }

    @Test void hostSelectsOnlyConfiguredCompatiblePresetsAndSaveContainsOnlyIdentity() {
        var game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 4);
        var host = UUID.randomUUID();
        game.join(host, "Host", 0);
        game.configureBots(List.of(preset("normal", 5)));
        int action = game.actions(0).indexOf(new Action(Action.Type.SET_BOT, List.of(1, 2)));
        assertTrue(action >= 0);
        assertTrue(game.act(host, game.decision, action));
        assertEquals("test", game.players[1].botPreset);
        var saved = JSON.toJson(game);
        assertFalse(saved.contains("command"));
        assertFalse(JSON.toJson(game.roomView()).contains("directory"));
        var restored = JSON.fromJson(saved, Game.class);
        restored.validate();
        assertEquals("test", restored.players[1].botPreset);
        game.rules = RuleSet.TENHOU_3.config();
        assertTrue(game.actions(0).stream().noneMatch(a -> a.type() == Action.Type.SET_BOT && a.tiles().get(1) == 2));
        var sanma = new Game(UUID.randomUUID(), RuleSet.TENHOU_3, 4);
        var sanmaHost = UUID.randomUUID();
        sanma.join(sanmaHost, "Sanma host", 0);
        sanma.configureBots(List.of(preset("normal", 5, RuleSet.TENHOU_3)));
        assertTrue(sanma.actions(0).stream().anyMatch(a -> a.type() == Action.Type.SET_BOT && a.tiles().get(1) == 2));
        sanma.rules = RuleSet.MAHJONG_SOUL_3.config();
        assertTrue(sanma.actions(0).stream().noneMatch(a -> a.type() == Action.Type.SET_BOT && a.tiles().get(1) == 2));
    }

    @Test void addedKanMustIdentifyTheExistingPonIncludingItsRedTile() {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74291);
        int kind = Tile.parseKind("5p");
        var pon = List.of(Tile.id(kind, 0, true), Tile.id(kind, 1, false), Tile.id(kind, 2, false));
        game.players[game.turn].melds.add(new Meld(Meld.Type.PON, pon, (game.turn + 1) % 4, pon.getFirst()));
        game.options.set(game.turn, List.of(new Action(Action.Type.ADDED_KAN, List.of(Tile.id(kind, 3, false)))));
        var view = game.view(game.players[game.turn].id);
        var response = JSON.toJsonTree(Map.of("type", "kakan", "actor", game.turn, "pai", "5p",
            "consumed", List.of("5p", "5pr", "5p"))).getAsJsonObject();
        assertEquals(0, MjaiProtocol.action(view, response, false));
        response.add("consumed", JSON.toJsonTree(List.of("5p", "5p", "5p")));
        assertThrows(IllegalArgumentException.class, () -> MjaiProtocol.action(view, response, false));
    }

    @Test @Timeout(10) void subprocessMakesLegalDecisionAndTimeoutClosesAnUnresponsiveProcess() throws Exception {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74291);
        try (var session = new MjaiSession(preset("normal", 5))) {
            int index;
            var position = game.mjaiPosition(game.turn);
            while ((index = session.poll(position)) < 0) Thread.sleep(5);
            assertEquals(Action.Type.DISCARD, position.view().actions().get(index).type());
        }
        try (var session = new MjaiSession(preset("silent", 1))) {
            var position = game.mjaiPosition(game.turn);
            assertEquals(-1, session.poll(position));
            assertThrows(java.util.concurrent.CompletionException.class, () -> {
                while (session.poll(position) < 0) Thread.sleep(5);
            });
        }
    }

    @Test @Timeout(10) void sanmaSubprocessReceivesThreeNamesAndChoosesLegally() throws Exception {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_3, 74291);
        try (var session = new MjaiSession(preset("normal", 5, RuleSet.TENHOU_3))) {
            var position = game.mjaiPosition(game.turn);
            int index;
            while ((index = session.poll(position)) < 0) Thread.sleep(5);
            assertEquals(Action.Type.DISCARD, position.view().actions().get(index).type());
        }
    }

    @Test @Timeout(10) void failedBotPausesClocksWithoutAcceptingHumanTileActions() throws Exception {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74291);
        game.configureBots(List.of(preset("silent", 1)));
        game.players[game.turn].bot = true;
        game.players[game.turn].botPreset = "test";
        try {
            while (!game.roomView().botFailed()) {
                game.age = 11;
                game.tick();
                Thread.sleep(5);
            }
            long decision = game.decision;
            int[] clocks = game.moveTicks.clone();
            for (int i = 0; i < 40; i++) game.tick();
            assertEquals(decision, game.decision);
            assertArrayEquals(clocks, game.moveTicks);
            assertFalse(game.act(game.players[game.turn].id, decision, 0));
        } finally { game.closeBots(); }
    }

    @Test @Timeout(10) void reachHandshakeCombinesTheDiscardAndSuppressesOnlyItsOwnEcho() throws Exception {
        var game = TrainingBotTest.hand("123m456p789s23m55z1z");
        game.players[0].firstTurn = false;
        game.options.set(0, LegalActions.onTurn(game, 0));
        var view = game.view(game.players[0].id);
        Map<String, Object> draw = Map.of("type", "tsumo", "actor", 0, "pai", MjaiProtocol.tile(game.players[0].drawn));
        try (var session = new MjaiSession(preset("reach", 5))) {
            var first = new MjaiProtocol.Position(1, 0, List.of(draw), view);
            int index;
            while ((index = session.poll(first)) < 0) Thread.sleep(5);
            assertEquals(Action.Type.RIICHI, view.actions().get(index).type());
            assertEquals("reach", session.response().get("type").getAsString());
            assertEquals("dahai", session.reachDiscardResponse().get("type").getAsString());
            var next = new MjaiProtocol.Position(1, 0, List.of(draw, Map.of("type", "reach", "actor", 0), draw), view);
            while ((index = session.poll(next)) < 0) Thread.sleep(5);
            assertEquals(Action.Type.DISCARD, view.actions().get(index).type());
        }
    }

    @Test void teacherQComparesLegalActionIndicesAndRiichiDiscardStagesSeparately() {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74291);
        var view = game.view(game.players[game.turn].id);
        int kind = Tile.parseKind("5p");
        int normal = Tile.id(kind, 1, false);
        int red = Tile.id(kind, 0, true);
        var first = JSON.toJsonTree(Map.of("meta", Map.of("mask_bits", (1L << kind) | (1L << 37),
            "q_values", List.of(-2.5, 1.0)))).getAsJsonObject();
        var declaration = BotTeacherReview.compareQ(view, new Action(Action.Type.DISCARD, normal),
            new Action(Action.Type.RIICHI, red), first, null);
        assertEquals("action", declaration.stage());
        assertEquals(3.5, declaration.gap());
        var second = JSON.toJsonTree(Map.of("meta", Map.of("mask_bits", (1L << kind) | (1L << 35),
            "q_values", List.of(-1.0, 0.5)))).getAsJsonObject();
        var redChoice = BotTeacherReview.compareQ(view, new Action(Action.Type.RIICHI, normal),
            new Action(Action.Type.RIICHI, red), first, second);
        assertEquals("reach-discard", redChoice.stage());
        assertEquals(1.5, redChoice.gap());
        assertNull(BotTeacherReview.compareQ(view, new Action(Action.Type.RIICHI, normal),
            new Action(Action.Type.RIICHI, red), first, null).gap());
        var kanResponse = JSON.toJsonTree(Map.of("meta", Map.of("mask_bits", 1L << 42,
            "q_values", List.of(0.0), "kan_select", Map.of("mask_bits", (1L << 0) | (1L << 9),
                "q_values", List.of(-2.0, 0.5))))).getAsJsonObject();
        var kan = BotTeacherReview.compareQ(view, new Action(Action.Type.CLOSED_KAN, Tile.id(0, 0, false)),
            new Action(Action.Type.CLOSED_KAN, Tile.id(9, 0, false)), kanResponse, null);
        assertEquals("kan-select", kan.stage());
        assertEquals(2.5, kan.gap());
        var sanma = GameLifecycleTest.started(RuleSet.TENHOU_3, 74291);
        var sanmaView = sanma.view(sanma.players[sanma.turn].id);
        var unavailable = BotTeacherReview.compareQ(sanmaView, new Action(Action.Type.DISCARD, normal),
            new Action(Action.Type.DISCARD, red), first, null);
        assertEquals("three-player-q-unmapped", unavailable.stage());
        assertNull(unavailable.gap());
    }

    private static BotPreset preset(String mode, int timeout) {
        return preset(mode, timeout, RuleSet.TENHOU_4);
    }

    private static BotPreset preset(String mode, int timeout, RuleSet rules) {
        String java = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        return new BotPreset("test", "Test bot", rules,
            List.of(java, "-cp", System.getProperty("java.class.path"), FakeBot.class.getName(), mode, Integer.toString(rules.players())),
            Path.of("").toAbsolutePath().toString(), timeout);
    }

    public static final class FakeBot {
        public static void main(String[] args) throws Exception {
            var reader = new java.io.BufferedReader(new java.io.InputStreamReader(System.in, java.nio.charset.StandardCharsets.UTF_8));
            String drawn = null;
            boolean reached = false;
            for (String line; (line = reader.readLine()) != null;) {
                var event = JSON.fromJson(line, JsonObject.class);
                String type = event.get("type").getAsString();
                if (type.equals("start_game") && event.getAsJsonArray("names").size() != Integer.parseInt(args[1]))
                    throw new IllegalStateException("Wrong mjai player count");
                if (type.equals("reach")) {
                    if (reached) throw new IllegalStateException("Duplicate reach echo");
                    reached = true;
                    event.addProperty("pai", drawn);
                }
                if (!args[0].equals("silent") && (type.equals("tsumo") || type.equals("reach")) && event.get("can_act").getAsBoolean()) {
                    if (type.equals("tsumo")) drawn = event.get("pai").getAsString();
                    event.addProperty("type", args[0].equals("reach") && !reached ? "reach" : "dahai");
                    event.addProperty("tsumogiri", true);
                    System.out.println(JSON.toJson(event));
                    System.out.flush();
                }
            }
        }
    }
}

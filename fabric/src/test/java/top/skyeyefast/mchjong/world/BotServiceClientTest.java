package top.skyeyefast.mchjong.world;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import top.skyeyefast.mchjong.engine.BotPosition;
import top.skyeyefast.mchjong.engine.RiichiPreset;
import top.skyeyefast.mchjong.engine.ExternalBot;
import top.skyeyefast.mchjong.engine.RiichiGame;
import top.skyeyefast.mchjong.engine.WorldPolicy;
import static org.junit.jupiter.api.Assertions.*;

class BotServiceClientTest {
    @Test void selectedBotExposesUnavailableServiceWithoutChangingItsIdentity() {
        RiichiGame game = new RiichiGame(UUID.randomUUID(), RiichiPreset.TENHOU_4, 1);
        game.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        game.configureExternalBots(List.of(new ExternalBot("mortal-4p", "Mortal 4P", 4, List.of(RiichiPreset.TENHOU_4))));
        UUID host = UUID.randomUUID();
        assertTrue(game.join(host, "Host", 0));
        var view = game.roomView(host);
        int choice = -1;
        for (int i = 0; i < view.actions().size(); i++)
            if (view.actions().get(i).type() == top.skyeyefast.mchjong.engine.RoomAction.Type.SET_BOT
                && view.actions().get(i).arguments().equals(List.of(1, 2))) choice = i;
        assertTrue(choice >= 0);
        assertTrue(game.actRoom(host, view.tableId(), view.incarnation(), view.decision(), choice));
        assertEquals("mortal-4p", game.roomView(null).seats().get(1).participant().externalBotId());
        assertEquals("unavailable", new BotServiceClient().state(game).seatErrors().get(1));
    }

    @Test void discoveryValidatesProtocolAndExactPresets() {
        var bots = BotServiceClient.parseBots("""
            {"protocol_version":1,"bots":[{"id":"mortal-4p","name":"Mortal 4P",
            "player_count":4,"presets":["TENHOU_4"]}]}
            """);
        assertEquals(1, bots.size());
        assertTrue(bots.getFirst().supports(RiichiPreset.TENHOU_4.config()));
        assertFalse(bots.getFirst().supports(RiichiPreset.MAHJONG_SOUL_4.config()));
        assertThrows(IllegalStateException.class, () -> BotServiceClient.parseBots("""
            {"protocol_version":2,"bots":[]}
            """));
    }

    @Test void wireNamesAndDecisionEchoProtectServerActions() {
        UUID table = UUID.randomUUID(), session = UUID.randomUUID();
        BotPosition position = new BotPosition(1, "mortal-4p", RiichiPreset.TENHOU_4, table, session, 1, 0, 4, 12,
            new BotPosition.Opening(0, 0, 0, 0, List.of(25000, 25000, 25000, 25000),
                List.of(0, 4, 8, 12, 16, 20, 24, 28, 32, 36, 40, 44, 48), 108),
            List.of(new BotPosition.Event("DRAW", 0, 52, false, false, null, null, List.of())),
            List.of(new Action(Action.Type.DISCARD, 52)), null, 52, List.of());
        var request = JsonParser.parseString(BotServiceClient.requestBody(position)).getAsJsonObject();
        assertEquals(4, request.get("player_count").getAsInt());
        assertEquals("mortal-4p", request.get("bot_id").getAsString());
        assertEquals(1, request.get("protocol_version").getAsInt());
        assertEquals(1, request.getAsJsonArray("legal_actions").size());
        assertEquals(108, request.getAsJsonObject("opening").get("dora_marker").getAsInt());

        String valid = """
            {"protocol_version":1,"bot_id":"mortal-4p","table_id":"%s","session_id":"%s","hand_number":1,
             "seat":0,"decision":12,"action_index":0}
            """.formatted(table, session);
        assertEquals(0, BotServiceClient.validatedIndex(position, valid));
        assertThrows(IllegalStateException.class, () -> BotServiceClient.validatedIndex(position,
            valid.replace("\"decision\":12", "\"decision\":11")));
        assertThrows(IllegalStateException.class, () -> BotServiceClient.validatedIndex(position,
            valid.replace("\"action_index\":0", "\"action_index\":1")));
        assertThrows(IllegalStateException.class, () -> BotServiceClient.validatedIndex(position,
            valid.replace("\"protocol_version\":1", "\"protocol_version\":2")));
        assertThrows(IllegalStateException.class, () -> BotServiceClient.validatedIndex(position,
            valid.replace("\"action_index\":0", "\"unused\":0")));
    }
}

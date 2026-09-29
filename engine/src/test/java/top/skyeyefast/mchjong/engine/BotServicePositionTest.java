package top.skyeyefast.mchjong.engine;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotServicePositionTest {
    @Test void lobbyOffersOnlyBotsMatchingTheCurrentPreset() {
        var bot = new ExternalBot("mortal-4p", "Mortal 4P", 4, java.util.List.of(RiichiPreset.TENHOU_4));
        RiichiGame game = new RiichiGame(UUID.randomUUID(), RiichiPreset.MAHJONG_SOUL_4, 1);
        game.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        game.configureExternalBots(java.util.List.of(bot));
        UUID host = UUID.randomUUID();
        assertTrue(game.join(host, "Host", 0));
        assertTrue(game.roomView(host).actions().stream().noneMatch(action ->
            action.type() == RoomAction.Type.SET_BOT && action.arguments().get(1) == 2));
    }

    @Test void botPositionKeepsPrivateDrawsAndRejectsStaleOrIllegalResponses() {
        RiichiGame game = new RiichiGame(UUID.randomUUID(), RiichiPreset.TENHOU_4, 83);
        game.configureExternalBots(java.util.List.of(new ExternalBot("mortal-4p", "Mortal 4P", 4,
            java.util.List.of(RiichiPreset.TENHOU_4))));
        game.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        UUID host = UUID.randomUUID();
        assertTrue(game.join(host, "Host", 1));
        var lobby = game.roomView(host);
        int external = -1;
        for (int i = 0; i < lobby.actions().size(); i++)
            if (lobby.actions().get(i).type() == RoomAction.Type.SET_BOT
                && lobby.actions().get(i).arguments().equals(java.util.List.of(0, 2))) external = i;
        assertTrue(external >= 0);
        assertTrue(game.actRoom(host, lobby.tableId(), lobby.incarnation(), lobby.decision(), external));
        String saved = new com.google.gson.Gson().toJson(game);
        assertTrue(saved.contains("\"externalBotId\":\"mortal-4p\""));
        assertFalse(saved.contains("externalBots"));
        RiichiGame restored = new com.google.gson.Gson().fromJson(saved, RiichiGame.class);
        restored.validate();
        assertEquals("mortal-4p", restored.externalBotId(0));
        GameLifecycleTest.startPositioned(game);
        assertNull(game.replay);
        assertNotNull(game.recorder);

        UUID session = UUID.randomUUID();
        assertNull(game.botPosition(0, session));
        for (int tick = 0; tick <= RiichiGame.DEAL_TICKS; tick++) game.tick();
        BotPosition position = game.botPosition(0, session);
        assertNotNull(position);
        assertEquals(13, position.opening().hand().size());
        assertEquals(4, position.opening().scores().size());
        assertEquals(game.players[0].drawn, position.events().getLast().tile());
        assertNull(game.recorder.botEvents(1).getLast().tile());
        assertNull(game.botPosition(1, session));
        assertNull(game.botPosition(2, session));
        for (int tick = 0; tick < 24; tick++) game.tick();
        assertEquals(position.decision(), game.botPosition(0, session).decision());

        int discard = -1;
        for (int i = 0; i < position.legalActions().size(); i++)
            if (position.legalActions().get(i).type() == Action.Type.DISCARD) { discard = i; break; }
        assertTrue(discard >= 0);
        assertFalse(game.actBot(0, position.decision(), -1));
        assertFalse(game.actBot(0, position.decision() - 1, discard));
        assertTrue(game.actBot(0, position.decision(), discard));
        game.validate();
    }
}

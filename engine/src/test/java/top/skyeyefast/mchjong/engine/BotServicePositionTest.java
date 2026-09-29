package top.skyeyefast.mchjong.engine;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotServicePositionTest {
    @Test void lobbyOffersOnlyBotsMatchingTheCurrentPreset() {
        var bot = new ExternalBot("mortal-4p", "Mortal 4P", 4, java.util.List.of(RuleSet.TENHOU_4));
        Game game = new Game(UUID.randomUUID(), RuleSet.MAHJONG_SOUL_4, 1);
        game.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        game.configureExternalBots(java.util.List.of(bot));
        UUID host = UUID.randomUUID();
        assertTrue(game.join(host, "Host", 0));
        assertTrue(game.view(host).actions().stream().noneMatch(action ->
            action.type() == Action.Type.SET_BOT && action.tiles().get(1) == 2));
    }

    @Test void botPositionKeepsPrivateDrawsAndRejectsStaleOrIllegalResponses() {
        Game game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 83);
        game.configureExternalBots(java.util.List.of(new ExternalBot("mortal-4p", "Mortal 4P", 4,
            java.util.List.of(RuleSet.TENHOU_4))));
        game.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        UUID host = UUID.randomUUID();
        assertTrue(game.join(host, "Host", 1));
        var lobby = game.view(host);
        int external = -1;
        for (int i = 0; i < lobby.actions().size(); i++)
            if (lobby.actions().get(i).type() == Action.Type.SET_BOT
                && lobby.actions().get(i).tiles().equals(java.util.List.of(0, 2))) external = i;
        assertTrue(external >= 0);
        assertTrue(game.act(host, lobby.decision(), external));
        GameLifecycleTest.startPositioned(game);
        assertNull(game.replay);
        assertNotNull(game.recorder);

        UUID session = UUID.randomUUID();
        assertNull(game.botPosition(0, session));
        for (int tick = 0; tick <= Game.DEAL_TICKS; tick++) game.tick();
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

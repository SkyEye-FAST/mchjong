package top.skyeyefast.mchjong.engine;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotServicePositionTest {
    @Test void botPositionKeepsPrivateDrawsAndRejectsStaleOrIllegalResponses() {
        Game game = new Game(UUID.randomUUID(), RuleSet.TENHOU_4, 83);
        game.configureBotService(true);
        game.configureWorld(new WorldPolicy(true, false, true, 5_000, false, true, true, true, null));
        assertTrue(game.join(UUID.randomUUID(), "Host", 1));
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

package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenpaiHintsTest {
    private static final UUID OWNER = new UUID(1, 1);

    @Test void countsDeduplicateCalledTilesAndIgnoreOtherHandsEvenWhenRevealed() {
        var game = fixture("123456m456p22s78s");
        var hints = new TenpaiHints();
        int six = Tile.id(23, 0, false);
        game.players[1].river.add(new Discard(six, false, false, true));
        game.players[2].melds.add(TestHands.meld(Meld.Type.PON, "666s"));
        game.players[1].hand.add(Tile.id(23, 3, false));
        game.openHands = true;
        var view = game.view(OWNER);
        var waits = hints.waits(view, Tile.ABSENT);
        assertEquals(List.of(new TenpaiHints.Wait(23, 1), new TenpaiHints.Wait(26, 4)), waits);
        assertSame(waits, hints.waits(view, Tile.ABSENT), "Rendering the same snapshot reuses the complete result");
        game.players[1].river.add(new Discard(Tile.id(23, 3, false), false, false, false));
        assertEquals(0, hints.waits(game.view(OWNER), Tile.ABSENT).getFirst().remaining());
        int drawn = Tile.id(Tile.WHITE, 0, false);
        game.players[0].hand.add(drawn);
        game.options.set(0, List.of(new Action(Action.Type.DISCARD, drawn)));
        assertEquals(0, hints.waits(game.view(OWNER), drawn).getFirst().remaining());
        assertTrue(hints.waits(game.view(OWNER), game.players[0].hand.getFirst()).isEmpty());
        assertTrue(hints.waits(game.view(null), drawn).isEmpty());
        game.phase = Game.Phase.HAND_END;
        assertTrue(hints.waits(game.view(OWNER), drawn).isEmpty());
    }

    @Test void specialAndOpenHandsUseStructuralWaitsWithoutImpossibleFifthCopies() {
        var hints = new TenpaiHints();
        var game = fixture("19m19p19s1234567z");
        game.rules = RuleSet.MAHJONG_SOUL_3.config().with(RuleOption.MIN_HAN, 4);
        assertEquals(13, hints.waits(game.view(OWNER), Tile.ABSENT).size());
        game.players[0].hand.clear();
        game.players[0].hand.addAll(TestHands.tiles("1122p3344s55667z"));
        assertEquals(List.of(new TenpaiHints.Wait(Tile.RED, 3)), hints.waits(game.view(OWNER), Tile.ABSENT));
        game.players[0].hand.clear();
        game.players[0].hand.addAll(TestHands.tiles("123456p1113s"));
        game.players[0].melds.add(TestHands.meld(Meld.Type.CLOSED_KAN, "2222s"));
        assertEquals(List.of(new TenpaiHints.Wait(20, 3)), hints.waits(game.view(OWNER), Tile.ABSENT));
    }

    private static Game fixture(String hand) {
        var game = new Game(new UUID(0, 1), RuleSet.MAHJONG_SOUL_4, 1);
        game.join(OWNER, "Player", 0);
        game.phase = Game.Phase.TURN;
        game.players[0].hand.addAll(TestHands.tiles(hand));
        return game;
    }
}

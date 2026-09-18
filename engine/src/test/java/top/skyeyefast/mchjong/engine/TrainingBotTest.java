package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrainingBotTest {
    private static Game hand(String text) {
        var game = new Game(new UUID(1, 2), RuleSet.TENHOU_4, 17);
        for (int seat = 0; seat < 4; seat++) game.join(new UUID(2, seat + 1), "Player " + seat, seat);
        game.phase = Game.Phase.TURN;
        game.turn = 0;
        game.wall = new Wall(game.rules(), 24);
        game.players[0].hand = new ArrayList<>(TestHands.tiles(text));
        game.players[0].drawn = game.players[0].hand.getLast();
        game.options.set(0, game.players[0].hand.stream().map(tile -> new Action(Action.Type.DISCARD, tile)).toList());
        return game;
    }

    private static Action choice(Game game, BotDifficulty difficulty) {
        var view = game.view(game.players[0].id);
        return view.actions().get(TrainingBot.choose(view, difficulty));
    }

    @Test void tiersUseLiveEfficiencyAndHardBotNeverReadsHiddenHandsOrSeed() {
        var game = hand("123456m234p456s12z");
        var shapes = HandAnalyzer.discardEfficiency(game.players[0].hand, List.of());
        int min = shapes.values().stream().mapToInt(TileEfficiency::shanten).min().orElseThrow();
        for (var difficulty : BotDifficulty.values())
            assertEquals(min, shapes.get(Tile.kind(choice(game, difficulty).tiles().getFirst())).shanten());
        var first = choice(game, BotDifficulty.HARD);
        game.seed = Long.MAX_VALUE;
        game.players[1].hand = TestHands.tiles("111222333m45677p");
        game.players[2].hand = TestHands.tiles("789m123456p11122z");
        assertEquals(first, choice(game, BotDifficulty.HARD));
        game.configureWorld(true, true);
        assertEquals(first, choice(game, BotDifficulty.HARD), "Open-hand permission cannot improve a bot's information");
    }

    @Test void normalAndHardDefendWithGenbutsuWithoutForcingTheEasyBotToFold() {
        var game = hand("123568m2458p147s1z");
        game.players[1].riichi = true;
        // Break a completed group to use a known-safe tile when the hand is far from ready.
        game.players[1].river.add(new Discard(Tile.id(0, 3, false), true, false, false));
        assertNotEquals(0, Tile.kind(choice(game, BotDifficulty.EASY).tiles().getFirst()));
        assertEquals(0, Tile.kind(choice(game, BotDifficulty.NORMAL).tiles().getFirst()));
        assertEquals(0, Tile.kind(choice(game, BotDifficulty.HARD).tiles().getFirst()));
    }

    @Test void callsNeedAnOpenYakuAndRealProgressAndWinsAlwaysComeFirst() {
        var game = hand("123m456p23s55667z");
        game.phase = Game.Phase.REACTION;
        game.lastFrom = 1;
        game.lastTile = Tile.id(Tile.WHITE, 3, false);
        game.players[1].river.add(new Discard(game.lastTile, false, false, false));
        game.players[0].drawn = Tile.ABSENT;
        var pon = new Action(Action.Type.PON, game.players[0].hand.stream().filter(tile -> Tile.kind(tile) == Tile.WHITE).toList());
        game.options.set(0, List.of(pon, new Action(Action.Type.PASS)));
        assertEquals(Action.Type.PASS, choice(game, BotDifficulty.EASY).type());
        assertEquals(Action.Type.PON, choice(game, BotDifficulty.NORMAL).type());
        assertEquals(Action.Type.PON, choice(game, BotDifficulty.HARD).type());
        game.players[2].riichi = true;
        assertEquals(Action.Type.PASS, choice(game, BotDifficulty.HARD).type());
        game.options.set(0, List.of(pon, new Action(Action.Type.PASS), new Action(Action.Type.RON, game.lastTile)));
        for (var difficulty : BotDifficulty.values()) assertEquals(Action.Type.RON, choice(game, difficulty).type());
    }
}

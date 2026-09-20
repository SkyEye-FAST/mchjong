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
        game.wall = new Wall(game.rules(), 24, game.dealer);
        game.players[0].hand = new ArrayList<>(TestHands.tiles(text));
        game.players[0].drawn = game.players[0].hand.getLast();
        game.options.set(0, game.players[0].hand.stream().map(tile -> new Action(Action.Type.DISCARD, tile)).toList());
        return game;
    }

    private static Action choice(Game game, BotDifficulty difficulty) {
        var view = game.view(game.players[0].id);
        return view.actions().get(TrainingBot.choose(view, difficulty));
    }

    @Test void analysisKeepsVisibleDiscardsAndSeparatesRedStockAndDuplicateDora() {
        var game = hand("234m340p456678s2p");
        game.wall.revealed = 2;
        game.wall.tiles.set(game.wall.dora.get(0), Tile.id(12, 2, false));
        game.wall.tiles.set(game.wall.dora.get(1), Tile.id(12, 3, false));
        var analysis = new BotAnalysis(game.view(game.players[0].id), BotDifficulty.NORMAL);
        var state = analysis.initial();
        assertEquals(0, analysis.unseen[13 + 34], "Owned red five cannot be drawn again");
        assertEquals(3, analysis.unseen[13]);
        assertEquals(3, analysis.value.bonus(state, Tile.ABSENT), "Two indicators plus the same tile's red bonus");
        var waits = analysis.value.waits(state, HandAnalyzer.waits(state.hand(), state.melds()), analysis.unseen);
        assertTrue(waits.ronTiles() > 0);
        assertTrue(waits.tsumoTiles() > 0);
        var furiten = new BotAnalysis.State(state.hand(), state.melds(), state.norths(), 1L << 10, false);
        assertEquals(0, analysis.value.waits(furiten, HandAnalyzer.waits(state.hand(), state.melds()), analysis.unseen).ronTiles());
        int discard = state.hand().getFirst();
        int before = analysis.unseen[BotAnalysis.face(discard)];
        game.players[0].hand.remove(Integer.valueOf(discard));
        game.players[0].river.add(new Discard(discard, false, false, false));
        assertEquals(before, new BotAnalysis(game.view(game.players[0].id), BotDifficulty.NORMAL).unseen[BotAnalysis.face(discard)]);
    }

    @Test void bonusesCannotMakeYakulessOrBelowMinimumWaitsLegal() {
        var game = hand("123m456p789s23m55z");
        // A structurally complete open hand with only dora has no winning value.
        var meld = TestHands.meld(Meld.Type.CHI, "123m");
        var state = new BotAnalysis.State(TestHands.tiles("456p789s23m55z"), List.of(meld), List.of(), 0, false);
        var value = new BotValue(game.view(game.players[0].id));
        value.dora[Tile.WHITE] = 3;
        assertNull(value.score(state, Tile.id(3, 1, false), false));
        game.rules = game.rules.with(RuleOption.MIN_HAN, 4);
        value = new BotValue(game.view(game.players[0].id));
        var closed = new BotAnalysis.State(TestHands.tiles("234m345p456s678s2p"), List.of(), List.of(), 0, true);
        value.dora[13] = 4;
        assertNull(value.score(closed, Tile.id(10, 1, false), false), "Dora do not satisfy four-yaku-han minimum");
    }

    @Test void hardTradesImmediateUkeireForWeightedDevelopmentDeterministically() {
        var game = GameLifecycleTest.started(RuleSet.TENHOU_4, 74309);
        var view = game.view(game.players[game.turn].id);
        var normal = view.actions().get(TrainingBot.choose(view, BotDifficulty.NORMAL));
        var hard = view.actions().get(TrainingBot.choose(view, BotDifficulty.HARD));
        assertEquals(10, Tile.kind(normal.tiles().getFirst()));
        assertEquals(9, Tile.kind(hard.tiles().getFirst()));
        var analysis = new BotAnalysis(view, BotDifficulty.HARD);
        var start = analysis.initial();
        var shapes = analysis.discards(start);
        var n = start.discard(normal.tiles().getFirst(), false);
        var h = start.discard(hard.tiles().getFirst(), false);
        var ne = analysis.evaluate(n, shapes.get(10), analysis.unseen);
        var he = analysis.evaluate(h, shapes.get(9), analysis.unseen);
        assertEquals(ne.shanten(), he.shanten());
        assertTrue(he.live() <= ne.live());
        assertTrue(analysis.forward(h, he) > analysis.forward(n, ne));
        assertTrue(analysis.drawNodes <= 2 * 37);
        var actions = new ArrayList<>(game.options.get(game.turn));
        java.util.Collections.reverse(actions);
        game.options.set(game.turn, actions);
        var reordered = game.view(game.players[game.turn].id);
        assertEquals(BotAnalysis.face(hard.tiles().getFirst()),
            BotAnalysis.face(reordered.actions().get(TrainingBot.choose(reordered, BotDifficulty.HARD)).tiles().getFirst()));
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

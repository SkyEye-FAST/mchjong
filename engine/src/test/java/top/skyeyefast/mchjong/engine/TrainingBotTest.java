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
        var game = hand("234m340p45667s22p");
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
        assertEquals(java.util.Set.of(22, 25), HandAnalyzer.waits(state.hand(), state.melds()));
        var furiten = new BotAnalysis.State(state.hand(), state.melds(), state.norths(), 1L << 22, false, false, 1);
        assertEquals(0, analysis.value.waits(furiten, HandAnalyzer.waits(state.hand(), state.melds()), analysis.unseen).ronTiles());
        int discard = state.hand().getFirst();
        int before = analysis.unseen[BotAnalysis.face(discard)];
        game.players[0].hand.remove(Integer.valueOf(discard));
        game.players[0].river.add(new Discard(discard, false, false, false));
        assertEquals(before, new BotAnalysis(game.view(game.players[0].id), BotDifficulty.NORMAL).unseen[BotAnalysis.face(discard)]);
        var pon = TestHands.meld(Meld.Type.PON, "777z");
        game.players[2].melds.add(pon);
        game.lastTile = pon.calledTile(); game.lastFrom = 1; game.phase = Game.Phase.REACTION;
        game.players[1].river.add(new Discard(game.lastTile, false, false, true));
        game.players[2].melds.add(TestHands.meld(Meld.Type.CLOSED_KAN, "1111z"));
        game.players[0].norths.add(Tile.id(Tile.NORTH, 0, false));
        var known = VisibleTiles.counts(game.view(game.players[0].id));
        assertEquals(3, known[Tile.RED], "Called river tile and focus alias the meld tile");
        assertEquals(4, known[Tile.EAST]);
        assertEquals(1, known[Tile.NORTH]);
    }

    @Test void bonusesCannotMakeYakulessOrBelowMinimumWaitsLegal() {
        var game = hand("123m456p789s23m55z");
        // A structurally complete open hand with only dora has no winning value.
        var meld = TestHands.meld(Meld.Type.CHI, "123m");
        var state = new BotAnalysis.State(TestHands.tiles("456p789s23m55z"), List.of(meld), List.of(), 0, false, false, 1);
        var value = new BotValue(game.view(game.players[0].id));
        value.dora[Tile.WHITE] = 3;
        assertNull(value.score(state, Tile.id(3, 1, false), false, false));
        game.rules = game.rules.with(RuleOption.MIN_HAN, 4);
        value = new BotValue(game.view(game.players[0].id));
        var closed = new BotAnalysis.State(TestHands.tiles("234m345p456s678s2p"), List.of(), List.of(), 0, true, false, 1);
        value.dora[13] = 4;
        assertNull(value.score(closed, Tile.id(10, 1, false), false, false), "Dora do not satisfy four-yaku-han minimum");
        assertNull(value.score(closed, Tile.id(10, 1, false), true, false));
        var doubled = new BotAnalysis.State(closed.hand(), closed.melds(), closed.norths(), 0, true, false, 2);
        assertNotNull(value.score(doubled, Tile.id(10, 1, false), true, false), "Established double riichi counts toward the actual minimum");
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
        assertTrue(analysis.forward(h, he, false) > analysis.forward(n, ne, false));
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

    @Test void allLevelsCanFoldAWeakHandWithGenbutsu() {
        var game = hand("123568m2458p147s1z");
        game.players[1].riichi = true;
        // Break a completed group to use a known-safe tile when the hand is far from ready.
        game.players[1].river.add(new Discard(Tile.id(0, 3, false), true, false, false));
        assertEquals(0, Tile.kind(choice(game, BotDifficulty.EASY).tiles().getFirst()));
        assertEquals(0, Tile.kind(choice(game, BotDifficulty.NORMAL).tiles().getFirst()));
        assertEquals(0, Tile.kind(choice(game, BotDifficulty.HARD).tiles().getFirst()));
        game.players[2].riichi = true;
        game.players[1].river.add(new Discard(Tile.id(Tile.EAST, 2, false), false, false, false));
        game.players[2].river.add(new Discard(Tile.id(Tile.EAST, 3, false), true, false, false));
        assertEquals(Tile.EAST, Tile.kind(choice(game, BotDifficulty.HARD).tiles().getFirst()),
            "Fold with a tile safe to both opponents, not one opponent's genbutsu");
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
        assertEquals(Action.Type.PON, choice(game, BotDifficulty.EASY).type());
        assertEquals(Action.Type.PON, choice(game, BotDifficulty.NORMAL).type());
        assertEquals(Action.Type.PON, choice(game, BotDifficulty.HARD).type());
        game.players[2].riichi = true;
        assertEquals(Action.Type.PON, choice(game, BotDifficulty.HARD).type(), "A threat alone does not prohibit a useful call");
        game.options.set(0, List.of(pon, new Action(Action.Type.PASS), new Action(Action.Type.RON, game.lastTile)));
        for (var difficulty : BotDifficulty.values()) assertEquals(Action.Type.RON, choice(game, difficulty).type());
        var noYaku = hand("123m456p23s33667z");
        noYaku.phase = Game.Phase.REACTION; noYaku.lastFrom = 1;
        noYaku.lastTile = Tile.id(Tile.WEST, 3, false);
        noYaku.players[1].river.add(new Discard(noYaku.lastTile, false, false, false));
        var useless = new Action(Action.Type.PON, noYaku.players[0].hand.stream().filter(t -> Tile.kind(t) == Tile.WEST).toList());
        noYaku.options.set(0, List.of(useless, new Action(Action.Type.PASS)));
        for (var difficulty : BotDifficulty.values()) assertEquals(Action.Type.PASS, choice(noYaku, difficulty).type());
        // Already tenpai: calling can replace dead pair waits with live tsumo improvement.
        var ready = hand("234m456p2255s");
        ready.players[0].melds.add(TestHands.meld(Meld.Type.PON, "555z"));
        ready.phase = Game.Phase.REACTION; ready.lastFrom = 1;
        ready.lastTile = Tile.id(22, 3, false);
        for (int tile : List.of(Tile.id(19, 2, false), Tile.id(19, 3, false), Tile.id(22, 0, true), ready.lastTile))
            ready.players[1].river.add(new Discard(tile, false, false, false));
        var improve = new Action(Action.Type.PON, ready.players[0].hand.stream().filter(t -> Tile.kind(t) == 22).toList());
        ready.options.set(0, List.of(improve, new Action(Action.Type.PASS)));
        assertEquals(0, HandAnalyzer.handEfficiency(ready.players[0].hand, ready.players[0].melds).shanten());
        assertEquals(Action.Type.PON, choice(ready, BotDifficulty.NORMAL).type());
    }

    @Test void hardPushesValuableGoodTenpaiAndSeparatelyAccountsForTwoThreats() {
        var game = hand("234m340p45667s22p1z");
        game.wall.revealed = 2;
        game.wall.tiles.set(game.wall.dora.get(0), Tile.id(12, 2, false));
        game.wall.tiles.set(game.wall.dora.get(1), Tile.id(12, 3, false));
        game.players[1].riichi = true;
        game.players[1].river.add(new Discard(Tile.id(1, 3, false), true, false, false));
        assertEquals(Tile.EAST, Tile.kind(choice(game, BotDifficulty.HARD).tiles().getFirst()), "Keep the valuable two-sided tenpai");
        game.players[2].riichi = true;
        game.players[2].river.add(new Discard(Tile.id(2, 3, false), true, false, false));
        var analysis = new BotAnalysis(game.view(game.players[0].id), BotDifficulty.HARD);
        assertEquals(0, analysis.defence.riskAgainst(1, 1));
        assertTrue(analysis.defence.riskAgainst(2, 1) > 0);
        assertTrue(analysis.defence.danger(Tile.id(1, 0, false)) > 0, "One opponent's genbutsu is not globally safe");
        assertTrue(analysis.defence.riskAgainst(1, Tile.WHITE) > 0, "An honor without proof retains residual risk");
    }

    @Test void riichiComparesLegalValueWithDamaAndOwnTemporaryFuritenStaysPrivate() {
        var game = hand("123m456p789s23m55z1z");
        game.players[0].firstTurn = false;
        game.options.set(0, LegalActions.onTurn(game, 0));
        assertEquals(Action.Type.RIICHI, choice(game, BotDifficulty.NORMAL).type(), "Yakuless dama gains a ron route from riichi");
        game.players[0].temporaryFuriten = true;
        var own = game.view(game.players[0].id);
        assertTrue(own.ronBlocked());
        assertFalse(game.view(game.players[1].id).ronBlocked());
        assertFalse(game.view(null).ronBlocked());
        var analysis = new BotAnalysis(own, BotDifficulty.NORMAL);
        var state = analysis.initial().discard(game.players[0].hand.getLast(), true);
        var shape = analysis.shape(state);
        assertTrue(analysis.evaluate(state, shape, analysis.unseen).waits().ronTiles() > 0,
            "A real draw's discard clears temporary furiten before the next ron window");
        assertTrue(analysis.evaluate(state, shape, analysis.unseen).waits().tsumoTiles() > 0);
        var blocked = new BotAnalysis.State(state.hand(), state.melds(), state.norths(), state.river(), true, true, 1);
        assertEquals(0, analysis.evaluate(blocked, shape, analysis.unseen).waits().ronTiles());
        var dama = hand("1112345678999m2z");
        dama.players[0].firstTurn = false;
        dama.options.set(0, LegalActions.onTurn(dama, 0));
        assertEquals(Action.Type.DISCARD, choice(dama, BotDifficulty.NORMAL).type(), "Already valuable dama need not buy a declaration");
    }

    @Test void sanmaNorthExtractionPreservesValuableShapesAndUsesThePlayingSet() {
        var game = hand("19m19p19s1234567z4z");
        game.rules = RuleSet.MAHJONG_SOUL_3.config();
        game.wall = new Wall(game.rules, 24, 0);
        game.players[0].firstTurn = false;
        game.players[0].hand = new ArrayList<>(TestHands.tiles("19m19p19s1234567z2p"));
        game.players[0].drawn = game.players[0].hand.getLast();
        game.options.set(0, LegalActions.onTurn(game, 0));
        var analysis = new BotAnalysis(game.view(game.players[0].id), BotDifficulty.HARD);
        for (int kind = 1; kind < 8; kind++) assertEquals(0, analysis.unseen[kind] + analysis.unseen[kind + 34]);
        assertNotEquals(Action.Type.NUKI, choice(game, BotDifficulty.HARD).type(), "Keep north in thirteen-orphans tenpai");
        game.players[0].hand = new ArrayList<>(TestHands.tiles("234567p234567s4z1z"));
        game.players[0].drawn = game.players[0].hand.getLast();
        game.options.set(0, LegalActions.onTurn(game, 0));
        assertEquals(Action.Type.NUKI, choice(game, BotDifficulty.NORMAL).type(), "An isolated north can buy a useful replacement and bonus");
        game.players[0].riichi = game.players[0].riichiFuriten = true;
        game.players[0].drawn = game.players[0].hand.stream().filter(t -> Tile.kind(t) == Tile.NORTH).findFirst().orElseThrow();
        game.players[1].riichi = true;
        game.wall.liveEnd = game.wall.cursor + 1;
        game.options.set(0, LegalActions.onTurn(game, 0));
        assertEquals(Action.Type.DISCARD, choice(game, BotDifficulty.HARD).type(), "A single replacement need not justify its forced discard risk");
        game.wall.revealed = 2;
        game.wall.tiles.set(game.wall.dora.get(0), Tile.id(Tile.WEST, 0, false));
        game.wall.tiles.set(game.wall.dora.get(1), Tile.id(Tile.WEST, 1, false));
        assertEquals(Action.Type.NUKI, choice(game, BotDifficulty.HARD).type(), "Locked riichi still compares its legal replacement with forced tsumogiri");
        var late = new BotAnalysis(game.view(game.players[0].id), BotDifficulty.HARD);
        var after = late.initial().discard(game.players[0].drawn, false);
        var evaluation = late.evaluate(after, late.shape(after), late.unseen);
        assertEquals(evaluation.utility(), late.forward(after, evaluation, false));
        assertEquals(0, late.drawNodes, "Do not invent another own draw after the live wall ends");
    }
}

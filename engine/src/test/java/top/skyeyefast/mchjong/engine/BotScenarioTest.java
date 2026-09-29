package top.skyeyefast.mchjong.engine;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BotScenarioTest {
    private static Action choose(RiichiGame game) {
        var view = game.view(game.players[0].member.id);
        return view.actions().get(TrainingBot.choose(view, BotDifficulty.HARD));
    }

    private static RiichiGame deadTenpai() {
        var game = TrainingBotTest.hand("123456789m45p11z2z");
        game.wall.cursor = game.wall.liveEnd;
        game.players[0].river.add(new Discard(Tile.id(16, 0, false), false, false, false));
        for (int seat = 1; seat < 4; seat++) {
            game.players[seat].riichi = true;
            game.players[seat].river.add(new Discard(Tile.id(0, seat, false), false, false, false));
            game.players[seat].river.add(new Discard(Tile.id(Tile.SOUTH, seat, false), false, false, false));
            game.players[seat].river.add(new Discard(Tile.id(16, seat, false), false, false, false));
        }
        for (int kind : new int[]{11, 14}) for (int copy = 0; copy < 4; copy++)
            game.players[1].river.add(new Discard(Tile.id(kind, copy, false), false, false, false));
        return game;
    }

    @Test void lastDiscardKeepsDeadFormalTenpaiAmongEquallySafeTiles() {
        var game = deadTenpai();
        var chosen = choose(game);
        assertEquals(Tile.SOUTH, Tile.kind(chosen.tiles().getFirst()), "Dead waits still receive noten payments");
        game.players[0].hand.remove(chosen.tiles().getFirst());
        assertTrue(LegalActions.formalTenpai(game, 0));
        Settlement.exhaustive(game);
        assertEquals(3000, game.deltas.getFirst());
    }

    @Test void formalTenpaiDoesNotBuyExtraRiskOrOverrideAnEarlierTurn() {
        var risky = deadTenpai();
        for (int seat = 1; seat < 4; seat++)
            risky.players[seat].river.removeIf(d -> Tile.kind(d.tile()) == Tile.SOUTH);
        assertNotEquals(Tile.SOUTH, Tile.kind(choose(risky).tiles().getFirst()));

        var earlier = deadTenpai();
        earlier.wall.cursor--;
        assertNotEquals(Tile.SOUTH, Tile.kind(choose(earlier).tiles().getFirst()));
    }

    @Test void anotherNagashiReplacesNotenPayments() {
        var game = deadTenpai();
        game.players[3].river.removeIf(d -> !Tile.terminalOrHonor(Tile.kind(d.tile())));
        assertNotEquals(Tile.SOUTH, Tile.kind(choose(game).tiles().getFirst()));
    }

    @Test void lastDiscardPreservesNagashiInsteadOfFutureHandValue() {
        var game = TrainingBotTest.hand("123456789m45p11z2p");
        game.wall.cursor = game.wall.liveEnd;
        game.players[0].river.add(new Discard(Tile.id(Tile.SOUTH, 0, false), false, false, false));
        var chosen = choose(game);
        assertTrue(Tile.terminalOrHonor(Tile.kind(chosen.tiles().getFirst())));
        game.players[0].hand.remove(chosen.tiles().getFirst());
        game.players[0].river.add(new Discard(chosen.tiles().getFirst(), false, false, false));
        Settlement.exhaustive(game);
        assertEquals("nagashi", game.result);
        assertEquals(12000, game.deltas.getFirst());

        var disabled = TrainingBotTest.hand("123456789m45p11z2p");
        disabled.rules = disabled.rules.with(RiichiRuleOption.NAGASHI_MANGAN, 0);
        disabled.wall.cursor = disabled.wall.liveEnd;
        assertEquals(Tile.parseKind("2p"), Tile.kind(choose(disabled).tiles().getFirst()));
    }

    @Test void sanmaLastDiscardKeepsFormalTenpai() {
        var game = TrainingBotTest.hand("123456789p45s11z2z");
        game.rules = RiichiPreset.MAHJONG_SOUL_3.config();
        game.wall = new Wall(game.rules, 24, 0);
        game.wall.cursor = game.wall.liveEnd;
        game.players[0].river.add(new Discard(Tile.id(25, 0, false), false, false, false));
        for (int seat = 1; seat < 3; seat++) {
            game.players[seat].riichi = true;
            for (int kind : new int[]{9, Tile.SOUTH, 25})
                game.players[seat].river.add(new Discard(Tile.id(kind, seat, false), false, false, false));
        }
        for (int kind : new int[]{20, 23}) for (int copy = 0; copy < 4; copy++)
            game.players[1].river.add(new Discard(Tile.id(kind, copy, false), false, false, false));
        var chosen = choose(game);
        assertEquals(Tile.SOUTH, Tile.kind(chosen.tiles().getFirst()));
        game.players[0].hand.remove(chosen.tiles().getFirst());
        Settlement.exhaustive(game);
        assertEquals(2000, game.deltas.getFirst());
    }

    @Test void fourFixedMeldsOnlyPermitSingleTilePairWaits() {
        var game = TrainingBotTest.hand("123456789m1123z4z");
        game.players[1].melds.addAll(List.of(TestHands.meld(Meld.Type.SEQUENCE, "123p"),
            TestHands.meld(Meld.Type.SEQUENCE, "456p"), TestHands.meld(Meld.Type.SEQUENCE, "123s"),
            TestHands.meld(Meld.Type.SEQUENCE, "456s")));
        game.players[1].hand.add(Tile.id(15, 0, false));
        for (int copy = 1; copy < 4; copy++)
            game.players[2].river.add(new Discard(Tile.id(0, copy, false), false, false, false));
        var defence = new BotAnalysis(game.view(game.players[0].member.id), BotDifficulty.HARD).defence;
        assertEquals(0, defence.riskAgainst(1, 0), "All four visible copies exclude a tanki wait");
        assertEquals(defence.riskAgainst(1, 25), defence.riskAgainst(1, Tile.WEST), 1e-9,
            "A numbered tile has no additional sequence wait after four fixed melds");
        assertTrue(defence.riskAgainst(3, 0) > 0, "A concealed opponent can still complete kokushi");
        game.players[1].melds.removeLast();
        var threeMelds = new BotAnalysis(game.view(game.players[0].member.id), BotDifficulty.HARD).defence;
        assertTrue(threeMelds.riskAgainst(1, 0) > 0, "Three fixed melds still allow a sequence wait");
    }

    @Test void fourExposedTripletsGuaranteeToitoiValue() {
        var game = TrainingBotTest.hand("123456789m1123z4z");
        game.players[1].melds.addAll(List.of(TestHands.meld(Meld.Type.TRIPLET, "111p"),
            TestHands.meld(Meld.Type.TRIPLET, "222p"), TestHands.meld(Meld.Type.TRIPLET, "333s"),
            TestHands.meld(Meld.Type.TRIPLET, "444s")));
        game.players[1].hand.add(Tile.id(15, 0, false));
        game.wall.tiles.set(game.wall.dora.get(0), Tile.id(Tile.WEST, 3, false));
        var defence = new BotAnalysis(game.view(game.players[0].member.id), BotDifficulty.HARD).defence;
        assertTrue(defence.threats.getFirst().getValue() >= 2000, "A winning four-pon hand has at least two yaku han");
    }
}

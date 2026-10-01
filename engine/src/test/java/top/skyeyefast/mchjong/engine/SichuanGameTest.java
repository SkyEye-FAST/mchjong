package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static top.skyeyefast.mchjong.engine.SichuanAction.Type.*;

class SichuanGameTest {
    private static final String WAIT = "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 9p";

    @Test void stockOpeningAndSealedVoiding() {
        assertEquals(108, Tile.sichuanSet().size());
        assertEquals(108, Tile.sichuanSet().stream().distinct().count());
        assertFalse(Tile.validSet(Tile.sichuanSet()));
        assertFalse(Tile.validSichuanSet(Tile.set(true, RedFives.NONE)));
        var game = new SichuanGame(12);
        assertEquals(List.of(14, 13, 13, 13), game.save().players().stream().map(player -> player.hand().size()).toList());
        assertEquals(55, game.view(-1).wall().remaining());
        long decision = game.decision();
        choose(game, 0, VOID_SUIT, 0);
        assertEquals(decision, game.decision());
        assertEquals(0, game.view(0).seats().get(0).voidSuit());
        assertEquals(-1, game.view(1).seats().get(0).voidSuit());
        var restored = SichuanCodec.restore(SichuanCodec.save(game));
        assertFalse(restored.act(1, decision, 0));
        choose(restored, 1, VOID_SUIT, 1); choose(restored, 2, VOID_SUIT, 2); choose(restored, 3, VOID_SUIT, 0);
        assertEquals(SichuanGame.Phase.TURN, restored.phase());
        assertEquals(0, restored.view(-1).seats().get(0).voidSuit());
        restored.validate();
    }

    @Test void presetsUseTheirOwnWallLayoutAcrossEveryDealerAndDiceCut() {
        for (var preset : SichuanPreset.values()) {
            boolean longEastWest = preset.config().eastWestLongWall();
            assertEquals(preset == SichuanPreset.SBR_2025 ? List.of(14, 13, 14, 13) : List.of(13, 14, 13, 14),
                java.util.stream.IntStream.range(0, 4).mapToObj(seat -> SichuanWallLayout.stacks(seat, longEastWest)).toList());
            assertEquals(preset == SichuanPreset.SBR_2025 ? 30 : 28, SichuanWallLayout.traversal(0, 1, 1, longEastWest).getFirst());
            for (int dealer = 0; dealer < 4; dealer++) for (int die1 = 1; die1 <= 6; die1++) for (int die2 = 1; die2 <= 6; die2++) {
                var wall = new SichuanWall.State(Tile.sichuanSet(), dealer, die1, die2, 0, longEastWest);
                if (dealer == 0) {
                    var game = SichuanGame.replayHand(preset.config(), List.of(), wall);
                    game = SichuanCodec.restore(SichuanCodec.save(game));
                    assertEquals(55, game.view(-1).wall().remaining());
                    assertEquals(longEastWest, game.view(-1).wall().eastWestLongWall());
                }
                var restored = SichuanWall.restore(wall);
                var dealt = new java.util.HashSet<Integer>();
                for (int tile = 0; tile < 53; tile++) assertTrue(dealt.add(restored.draw()));
                assertEquals(55, SichuanWall.restore(restored.save()).remaining());
                assertEquals(108, SichuanWallLayout.traversal(dealer, die1, die2, longEastWest).stream().distinct().count());
            }
        }
    }

    @Test void milFirstDiscardIsSecretPhysicalChoiceAndSurvivesDrawAndRestore() {
        var game = new SichuanGame(12);
        var selected = game.actions(1).stream().filter(action -> !action.tiles().isEmpty()).reduce((first, last) -> last).orElseThrow();
        int tile = selected.tiles().getFirst();
        assertTrue(game.act(1, game.decision(), game.actions(1).indexOf(selected)));
        assertEquals(tile, game.view(1).seats().get(1).firstDiscard());
        assertTrue(game.view(1).submitted());
        var malformed = JsonParser.parseString(SichuanCodec.save(game)).getAsJsonObject();
        var savedPlayer = malformed.getAsJsonArray("players").get(1).getAsJsonObject();
        savedPlayer.addProperty("firstDiscard", Tile.ABSENT);
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(malformed.toString()));
        savedPlayer.remove("firstDiscard");
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(malformed.toString()));
        for (int recipient : new int[]{-1, 0, 2, 3}) {
            var view = SichuanCodec.decodeView(SichuanCodec.encodeView(game.view(recipient)));
            var hand = recipient < 0 ? List.<Integer>of() : game.save().players().get(recipient).hand();
            assertEquals(-1, view.seats().get(1).voidSuit());
            assertEquals(Tile.ABSENT, view.seats().get(1).firstDiscard());
            assertTrue(view.actions().stream().allMatch(action -> action.tiles().stream().allMatch(owned -> recipient >= 0
                && hand.contains(owned))));
        }
        game = SichuanCodec.restore(SichuanCodec.save(game));
        for (int seat : new int[]{0, 2, 3}) assertTrue(game.act(seat, game.decision(), 0));
        assertEquals(selected.suit(), game.view(-1).seats().get(1).voidSuit());
        assertEquals(Tile.ABSENT, game.view(0).seats().get(1).firstDiscard());
        while (game.turn() != 1) {
            choose(game, game.turn(), DISCARD, -1); passAll(game);
        }
        choose(game, 1, DRAW, -1);
        game = SichuanCodec.restore(SichuanCodec.save(game));
        assertEquals(List.of(new SichuanAction(DISCARD, List.of(tile))), game.actions(1).stream().filter(action -> action.type() == DISCARD).toList());
        choose(game, 1, DISCARD, Tile.kind(tile)); passAll(game);
        assertEquals(tile, game.save().players().get(1).river().getFirst().tile());
        game.validate();
    }

    @Test void heavenlyVoidHasNoBoundDiscardAndTfmjOnlySelectsTheSuit() {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var initial = take(stock, "1m 2m 3m 4m 5m 6m 7m 8m 9m 1p 2p 3p 4p 5p");
        // Make a complete opening with a dealer hand that naturally lacks souzu.
        var source = new SichuanGame(12).initialOpening();
        var slots = new ArrayList<>(source.slots());
        var order = SichuanWallLayout.traversal(source.dealer(), source.die1(), source.die2(), source.eastWestLongWall());
        var indices = List.of(0, 1, 2, 3, 16, 17, 18, 19, 32, 33, 34, 35, 48, 52);
        for (int index = 0; index < indices.size(); index++) {
            int to = order.get(indices.get(index)); int from = slots.indexOf(initial.get(index));
            Collections.swap(slots, to, from);
        }
        var opening = new SichuanWall.State(slots, source.dealer(), source.die1(), source.die2(), 0, source.eastWestLongWall());
        var game = SichuanGame.replayHand(SichuanPreset.SBR_2025.config(), List.of(), opening);
        var sky = new SichuanAction(VOID_SUIT, List.of(), 2);
        assertTrue(game.actions(0).contains(sky));
        assertTrue(game.act(0, game.decision(), game.actions(0).indexOf(sky)));
        assertEquals(Tile.ABSENT, game.save().players().get(0).firstDiscard());
        for (int seat = 1; seat < 4; seat++) assertTrue(game.act(seat, game.decision(), 0));
        assertEquals(14, game.actions(0).stream().filter(action -> action.type() == DISCARD).count());
        var tfmj = new SichuanGame(12, SichuanPreset.TFMJ_2024.config(), Tile.sichuanSet());
        assertEquals(3, tfmj.actions(0).size());
        assertTrue(tfmj.actions(0).stream().allMatch(action -> action.tiles().isEmpty()));
    }

    @Test void milShootingRefundsOnlyTheLastKongAndNeverTransfersOrRefundsTwice() {
        var game = position(new int[]{2, 2, 2, 2}, false,
            "9m 9m 9m 9m 7m 7m 7m 7m 9p", WAIT, WAIT, "");
        game = withRules(game, SichuanPreset.SBR_2025.config());
        choose(game, 0, CONCEALED_KONG, 8); choose(game, 0, DRAW, -1);
        choose(game, 0, CONCEALED_KONG, 6);
        game = SichuanCodec.restore(SichuanCodec.save(game));
        assertEquals(3, game.save().kongLedgerStart());
        choose(game, 0, DRAW, -1); choose(game, 0, DISCARD, Tile.parseKind("9p"));
        choose(game, 1, WIN, -1); choose(game, 2, WIN, -1); passAll(game);
        var refunds = game.save().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND).toList();
        assertEquals(List.of(3, 4, 5), refunds.stream().map(SichuanSettlement.Entry::relatedEntry).toList());
        assertEquals(List.of(1, 2, 3), refunds.stream().map(SichuanSettlement.Entry::recipient).toList());
        assertTrue(game.save().ledger().stream().noneMatch(SichuanSettlement.Entry::kongTransfer));
        game = SichuanCodec.restore(SichuanCodec.save(game));
        game = emptyWall(game); choose(game, game.turn(), DRAW, -1);
        assertEquals(SichuanSettlement.DrawStatus.READY, game.result().drawStatus().get(0));
        assertEquals(3, game.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND).count());
        assertEquals(3, game.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND)
            .map(SichuanSettlement.Entry::relatedEntry).distinct().count());
        game.validate();
    }

    @Test void voidSuitDiscardsAndCallsAreConstrainedAndThereIsNoChow() {
        var game = position(new int[]{0, 2, 2, 2}, false,
            "9m 9m 9m 9m 2m 3m 4m 5m 6m 7m 8m 8m 1p 1p", WAIT, WAIT, "");
        assertTrue(game.actions(0).stream().filter(action -> action.type() == DISCARD).allMatch(action -> Tile.kind(action.tiles().get(0)) < 9));
        assertTrue(game.actions(0).stream().noneMatch(action -> action.type() == WIN || action.type() == CONCEALED_KONG));
        choose(game, 0, DISCARD, Tile.parseKind("9m"));
        assertTrue(game.actions(1).stream().noneMatch(action -> action.type().name().contains("CHOW")));
        passAll(game);
        game.validate();
        var prohibited = position(new int[]{2, 0, 2, 2}, false, "1m", "1m 1m", "", "");
        choose(prohibited, 0, DISCARD, 0);
        assertTrue(prohibited.actions(1).stream().noneMatch(action -> action.type() == PUNG || action.type() == DISCARD_KONG || action.type() == WIN));
    }

    @Test void multipleWinsKeepOnePhysicalTileAndFinishAtThree() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, WAIT, WAIT);
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        long decision = game.decision();
        choose(game, 2, WIN, -1);
        assertEquals(decision, game.decision());
        var restored = SichuanCodec.restore(SichuanCodec.save(game));
        assertTrue(restored.actions(2).isEmpty());
        assertTrue(restored.view(2).submitted());
        choose(restored, 3, WIN, -1); choose(restored, 1, WIN, -1);
        assertEquals(SichuanGame.Phase.HAND_END, restored.phase());
        assertEquals(List.of(1, 2, 3), restored.result().wins().stream().map(SichuanSettlement.Win::seat).toList());
        assertEquals(List.of(13, 13, 13, 14), restored.save().players().stream().map(player -> player.hand().size()).toList());
        assertEquals(3, restored.result().ledger().size());
        assertEquals(0, restored.result().deltas().stream().mapToInt(Integer::intValue).sum());
        restored.validate();
        assertEquals(restored.result(), SichuanCodec.restore(SichuanCodec.save(restored)).result());
    }

    @Test void oneWinnerExitsAndBloodBattleContinuesAfterThatWinner() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, "", "");
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        choose(game, 1, WIN, -1); passAll(game);
        assertEquals(SichuanGame.Phase.TURN, game.phase());
        assertEquals(2, game.turn());
        assertTrue(game.actions(1).isEmpty());
        assertTrue(game.view(0).seats().get(1).hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
        var saved = game.save();
        assertEquals(1, saved.wins().size());
        choose(game, 2, DRAW, -1);
        assertEquals(saved.wins(), game.save().wins());
        game.validate();
    }

    @Test void finalTileChecksPassiveFlowerPigsAndMaximumReadyValue() {
        var game = position(new int[]{2, 2, 2, 0}, true,
            "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 7p 8p", WAIT,
            "1m 2m 4m 5m 7m 8m 1p 2p 4p 5p 7p 8p 8p", "1m 2m 3m 4m 5m 6m 7m 8m 9m 1p 2p 3p 4p");
        choose(game, 0, DISCARD, Tile.parseKind("8p")); passAll(game);
        assertEquals(SichuanGame.Phase.HAND_END, game.phase());
        assertTrue(game.result().exhaustive());
        assertEquals(SichuanSettlement.DrawStatus.READY, game.result().drawStatus().get(1));
        assertEquals(SichuanSettlement.DrawStatus.PASSIVE_FLOWER_PIG, game.result().drawStatus().get(3));
        assertTrue(game.result().ledger().stream().anyMatch(entry -> entry.type() == SichuanSettlement.Type.READY_PAYMENT && entry.payer() == 3 && entry.recipient() == 1));
        assertTrue(game.result().ledger().stream().noneMatch(entry -> entry.type() == SichuanSettlement.Type.FLOWER_PIG));
        game.validate();
    }

    @Test void scoringIncludesQuadPairsAndKongsWithoutRiichiSemantics() {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var pairs = take(stock, "1m 1m 1m 1m 2m 2m 3m 3m 4m 4m 5m 5m 6m 6m");
        var score = SichuanHandAnalyzer.score(pairs, List.of(), SichuanPreset.SBR_2025.config(), false, false, false, false);
        assertNotNull(score);
        assertEquals(5, score.fan());
        assertEquals(8, score.value());
        assertTrue(score.patterns().contains(SichuanSettlement.Fan.ROOT));
        assertTrue(score.patterns().contains(SichuanSettlement.Fan.SEVEN_PAIRS));
    }

    @Test void selfDrawPaysOnlyActiveOpponentsAndDiscardKongUsesItsSupplier() {
        var winner = position(new int[]{2, 2, 2, 2}, false,
            "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 9p 9p", "", "", "");
        choose(winner, 0, WIN, -1);
        assertEquals(1, winner.turn());
        assertEquals(3, winner.save().ledger().size());
        assertTrue(winner.save().ledger().stream().allMatch(entry -> entry.type() == SichuanSettlement.Type.SELF_DRAW_WIN
            && entry.amount() == 2 && entry.recipient() == 0));
        winner.validate();
        var kong = position(new int[]{2, 2, 2, 2}, false, "9m", "9m 9m 9m", "", "");
        choose(kong, 0, DISCARD, 8); choose(kong, 1, DISCARD_KONG, -1); passAll(kong);
        assertEquals(1, kong.turn());
        assertEquals(List.of(new SichuanSettlement.Entry(0, SichuanSettlement.Type.DISCARD_KONG, 0, 1, 2, -1)), kong.save().ledger());
        choose(kong, 1, DRAW, -1);
        kong.validate();
    }

    @Test void kongTakesFrontReplacementAndTransfersOnShoot() {
        var game = position(new int[]{2, 2, 2, 2}, false,
            "9m 9m 9m 9m 9p", WAIT, "", "");
        choose(game, 0, CONCEALED_KONG, 8);
        assertEquals(3, game.save().ledger().size());
        int remaining = game.view(-1).wall().remaining();
        var before = game.save().wall();
        int next = before.slots().get(SichuanWallLayout.traversal(before.dealer(), before.die1(), before.die2(), before.eastWestLongWall()).get(before.cursor()));
        choose(game, 0, DRAW, -1);
        assertEquals(next, game.save().players().get(0).drawn());
        assertEquals(remaining - 1, game.view(-1).wall().remaining());
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        choose(game, 1, WIN, -1); passAll(game);
        assertTrue(game.save().wins().get(0).score().patterns().contains(SichuanSettlement.Fan.SHOOT_AFTER_KONG));
        assertEquals(3, game.save().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_TRANSFER).count());
        assertTrue(game.save().ledger().stream().noneMatch(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND));
        game.validate();
    }

    @Test void safeViewsAndStrictRestorationRejectMalformedDocuments() {
        var game = new SichuanGame(8);
        var view = game.view(0);
        assertEquals(view, SichuanCodec.decodeView(SichuanCodec.encodeView(view)));
        assertTrue(view.seats().get(1).hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
        assertTrue(view.wall().slots().stream().allMatch(tile -> tile == Tile.HIDDEN || tile == Tile.ABSENT));
        String json = SichuanCodec.save(game);
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.decodeView(json));
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(json.replace("\"revision\":1", "\"revision\":1,\"revision\":2")));
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(json.replace("\"turn\":0,", "")));
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(json.replace("\"replacementDraw\":false", "\"replacementDraw\":\"false\"")));
        var state = game.save();
        var players = new ArrayList<>(state.players());
        var first = players.get(0);
        var hand = new ArrayList<>(first.hand()); hand.set(0, hand.get(1));
        players.set(0, new SichuanPlayerState(hand, first.melds(), first.river(), first.voidSuit(), first.won(), first.drawn(), first.passedFan(), first.firstDiscard()));
        assertThrows(IllegalArgumentException.class, () -> SichuanGame.restore(copy(state, players, state.wall())));
    }

    @Test void addedKongRobberyRestoresThePungAndRetiresAllWinners() {
        String wait = "1m 2m 3m 4m 5m 6m 7m 8m 1p 2p 3p 9p 9p";
        var game = pungPosition(true, wait, wait);
        choose(game, 0, ADDED_KONG, 8);
        var restored = SichuanCodec.restore(SichuanCodec.save(game));
        choose(restored, 1, WIN, -1); choose(restored, 2, WIN, -1); passAll(restored);
        assertEquals(3, restored.turn());
        assertEquals(2, restored.save().wins().size());
        assertEquals(Meld.Type.TRIPLET, restored.save().players().get(0).melds().getFirst().type());
        assertEquals(Tile.ABSENT, restored.save().players().get(0).drawn());
        assertTrue(restored.save().wins().stream().allMatch(win -> win.robbingKong()
            && win.score().patterns().contains(SichuanSettlement.Fan.ROBBING_KONG)));
        assertTrue(restored.save().ledger().stream().noneMatch(SichuanSettlement.Entry::kong));
        restored.validate();
    }

    @Test void milAddedKongAfterReplacementIsShootingAndTfmjKeepsRobbery() {
        String wait = "1m 2m 3m 4m 5m 6m 7m 8m 1p 2p 3p 9p 9p";
        for (var preset : SichuanPreset.values()) {
            var game = position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m 5p 5p 5p 5p", wait, wait, "");
            game = withRules(pungPosition(game, false), preset.config());
            choose(game, 0, CONCEALED_KONG, Tile.parseKind("5p")); choose(game, 0, DRAW, -1);
            choose(game, 0, ADDED_KONG, 8);
            game = SichuanCodec.restore(SichuanCodec.save(game));
            choose(game, 1, WIN, -1); choose(game, 2, WIN, -1); passAll(game);
            boolean mil = preset == SichuanPreset.SBR_2025;
            assertEquals(mil ? Meld.Type.ADDED_QUAD : Meld.Type.TRIPLET, game.save().players().get(0).melds().getFirst().type());
            assertTrue(game.save().wins().stream().allMatch(win -> win.robbingKong() != mil
                && win.score().patterns().equals(List.of(mil ? SichuanSettlement.Fan.SHOOT_AFTER_KONG : SichuanSettlement.Fan.ROBBING_KONG))));
            assertEquals(mil ? 3 : 0, game.save().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND).count());
            assertEquals(3, game.save().ledger().stream().filter(SichuanSettlement.Entry::kong).count());
            assertEquals(game.save().players(), SichuanCodec.restore(SichuanCodec.save(game)).save().players());
            game.validate();
        }
    }

    @Test void timelyAddedKongChargesEachActivePlayerButDelayedAddedKongIsFree() {
        for (boolean timely : new boolean[]{true, false}) {
            var game = pungPosition(timely, "", "");
            choose(game, 0, ADDED_KONG, 8); passAll(game);
            assertEquals(Meld.Type.ADDED_QUAD, game.save().players().get(0).melds().getFirst().type());
            assertEquals(timely ? 3 : 0, game.save().ledger().size());
            assertTrue(game.actions(0).stream().allMatch(action -> action.type() == DRAW));
            choose(game, 0, DRAW, -1);
            game.validate();
        }
    }

    @Test void exhaustiveDrawChargesActiveFlowerPigsAndRefundsNotReadyKongs() {
        var game = position(new int[]{2, 2, 2, 0}, true,
            "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 7p 8p", WAIT,
            "1m 2m 4m 5m 7m 8m 1p 2p 4p 5p 7p 8p 8p", "1m 2m 3m 4m 5m 6m 7m 8m 9m 1p 2p 3p 4p");
        var state = game.save();
        var players = new ArrayList<>(state.players());
        var river = new ArrayList<>(players.get(0).river());
        var discarded = river.stream().filter(tile -> Tile.kind(tile.tile()) / 9 == 1).findFirst().orElseThrow();
        river.remove(discarded);
        var owner = players.get(0);
        players.set(0, new SichuanPlayerState(owner.hand(), owner.melds(), river, owner.voidSuit(), false, owner.drawn(), -1, Tile.ABSENT));
        var pig = players.get(3);
        players.set(3, new SichuanPlayerState(pig.hand(), pig.melds(), List.of(discarded), pig.voidSuit(), false, pig.drawn(), -1, Tile.ABSENT));
        game = SichuanGame.restore(copy(state, players, state.wall()));
        choose(game, 0, DISCARD, Tile.parseKind("8p")); passAll(game);
        assertEquals(SichuanSettlement.DrawStatus.ACTIVE_FLOWER_PIG, game.result().drawStatus().get(3));
        assertEquals(1, game.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.FLOWER_PIG
            && entry.amount() == 24 && entry.recipient() == -1).count());
        assertEquals(-24, game.result().deltas().stream().mapToInt(Integer::intValue).sum());

        var kong = position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m", "", "", "");
        choose(kong, 0, CONCEALED_KONG, 8); choose(kong, 0, DRAW, -1);
        state = kong.save(); players = new ArrayList<>(state.players());
        var slots = new ArrayList<>(Collections.nCopies(108, Tile.ABSENT));
        owner = players.get(0); river = new ArrayList<>(owner.river());
        for (int tile : state.wall().slots()) if (tile != Tile.ABSENT) river.add(new SichuanPlayerState.Discard(tile, false));
        players.set(0, new SichuanPlayerState(owner.hand(), owner.melds(), river, owner.voidSuit(), false, owner.drawn(), -1, Tile.ABSENT));
        kong = SichuanGame.restore(copy(state, players, new SichuanWall.State(slots, 0, 1, 1, 108, state.wall().eastWestLongWall())));
        choose(kong, 0, DISCARD, Tile.kind(owner.hand().getFirst())); passAll(kong);
        assertEquals(3, kong.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND).count());
        kong.validate();
    }

    private static SichuanGame pungPosition(boolean timely, String second, String third) {
        return pungPosition(position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m", second, third, ""), timely);
    }
    private static SichuanGame pungPosition(SichuanGame game, boolean timely) {
        var state = game.save();
        var players = new ArrayList<>(state.players());
        var player = players.get(0);
        var hand = new ArrayList<>(player.hand());
        var pung = hand.stream().filter(tile -> Tile.kind(tile) == 8).limit(3).toList();
        hand.removeAll(pung);
        int drawn = hand.stream().filter(tile -> (Tile.kind(tile) == 8) == timely).findFirst().orElseThrow();
        players.set(0, new SichuanPlayerState(hand, List.of(new Meld(Meld.Type.TRIPLET, pung, 3, pung.getFirst())),
            player.river(), player.voidSuit(), false, drawn, -1, Tile.ABSENT));
        return SichuanGame.restore(copy(state, players, state.wall()));
    }

    @Test void threeVariantSessionsRoundTripWithFreshAuthority() {
        var id = UUID.randomUUID();
        TableSession session = new RiichiSession(UUID.randomUUID(), RiichiPreset.MAHJONG_SOUL_4, 5);
        assertTrue(session.join(id, "host", 0));
        session = session.selectVariant(id, session.decision(), MahjongVariant.SICHUAN);
        assertInstanceOf(SichuanSession.class, session);
        assertEquals(4, session.capacity());
        assertTrue(session.configureEquipment(false, Tile.sichuanSet()));
        var restored = TableSessionCodec.restore(TableSessionCodec.save(session));
        assertInstanceOf(SichuanSession.class, restored);
        assertEquals(MahjongVariant.SICHUAN, restored.variant());
        assertNotEquals(session.incarnation(), restored.incarnation());
        assertTrue(restored.equipped());
        assertInstanceOf(McrSession.class, restored.selectVariant(id, restored.decision(), MahjongVariant.MCR));
    }

    @Test void consecutiveKongsTransferTheWholeChainToMultipleWinners() {
        var game = position(new int[]{2, 2, 2, 2}, false,
            "9m 9m 9m 9m 7m 7m 7m 7m 9p", WAIT, WAIT, "");
        choose(game, 0, CONCEALED_KONG, 8); choose(game, 0, DRAW, -1);
        choose(game, 0, CONCEALED_KONG, 6);
        game = SichuanCodec.restore(SichuanCodec.save(game));
        assertEquals(0, game.save().kongLedgerStart());
        choose(game, 0, DRAW, -1); choose(game, 0, DISCARD, Tile.parseKind("9p"));
        choose(game, 2, WIN, -1); choose(game, 1, WIN, -1); passAll(game);
        var transfers = game.save().ledger().stream().filter(SichuanSettlement.Entry::kongTransfer).toList();
        assertEquals(12, transfers.stream().mapToInt(SichuanSettlement.Entry::amount).sum());
        for (int winner : new int[]{1, 2})
            assertEquals(6, transfers.stream().filter(entry -> entry.recipient() == winner).mapToInt(SichuanSettlement.Entry::amount).sum());
        assertEquals(List.of(0, 1, 2, 3, 4, 5), transfers.stream().map(SichuanSettlement.Entry::relatedEntry).toList());
        assertEquals(List.of(-4, 4, 4, -4), game.scores());
        var malformed = JsonParser.parseString(SichuanCodec.save(game)).getAsJsonObject();
        malformed.getAsJsonArray("ledger").get(8).getAsJsonObject().addProperty("relatedEntry", 6);
        assertThrows(IllegalArgumentException.class, () -> SichuanCodec.restore(malformed.toString()));
        game.validate();
    }

    @Test void multipleWinnersShareKongIncomeAndShooterSuppliesIntegerRounding() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m 9p", WAIT, WAIT, "");
        var state = game.save();
        var rules = new SichuanRules(3, 1, 1, 2, 1, 24, true, true, 8, false, false, false, false);
        game = SichuanGame.restore(new SichuanGame.State(state.format(), rules, state.nextWallSeed(), 1, List.of(), state.phase(),
            state.revision(), state.decision(), state.turn(), state.wall(), state.players(), state.focus(), state.supplier(),
            state.pendingKong(), state.replacementDraw(), state.afterKong(), state.kongLedgerStart(), state.responses(), state.wins(), state.ledger(), state.result()));
        choose(game, 0, CONCEALED_KONG, 8); choose(game, 0, DRAW, -1);
        choose(game, 0, DISCARD, Tile.parseKind("9p")); choose(game, 1, WIN, -1); choose(game, 2, WIN, -1); passAll(game);
        var ledger = game.save().ledger();
        assertEquals(List.of(new SichuanSettlement.Entry(8, SichuanSettlement.Type.KONG_TRANSFER_TOP_UP, 0, 2, 1, 0)),
            ledger.stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_TRANSFER_TOP_UP).toList());
        assertEquals(3, ledger.stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_TRANSFER).mapToInt(SichuanSettlement.Entry::amount).sum());
        assertEquals(List.of(-5, 3, 3, -1), game.scores());
        assertEquals(game.save().ledger(), SichuanCodec.restore(SichuanCodec.save(game)).save().ledger());
    }

    @Test void addedKongChainsIncludeOnlySettledIncomeAndRobberyIsNotAShootingDiscard() {
        for (boolean timely : new boolean[]{true, false}) {
            var game = position(new int[]{2, 2, 2, 2}, false,
                "9m 9m 9m 9m 7m 7m 7m 7m 9p", WAIT, WAIT, "");
            game = pungPosition(game, timely);
            choose(game, 0, ADDED_KONG, 8); passAll(game); choose(game, 0, DRAW, -1);
            choose(game, 0, CONCEALED_KONG, 6); choose(game, 0, DRAW, -1);
            choose(game, 0, DISCARD, Tile.parseKind("9p")); choose(game, 1, WIN, -1); choose(game, 2, WIN, -1); passAll(game);
            assertEquals(timely ? 9 : 6, game.save().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_TRANSFER).mapToInt(SichuanSettlement.Entry::amount).sum());
            assertEquals(timely ? 1 : 0, game.save().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_TRANSFER_TOP_UP).mapToInt(SichuanSettlement.Entry::amount).sum());
        }

        String wait = "1m 2m 3m 4m 5m 6m 7m 8m 1p 2p 3p 9p 9p";
        var game = position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m 5p 5p 5p 5p", wait, wait, "");
        game = pungPosition(game, false);
        choose(game, 0, CONCEALED_KONG, Tile.parseKind("5p")); choose(game, 0, DRAW, -1);
        choose(game, 0, ADDED_KONG, 8); choose(game, 1, WIN, -1); choose(game, 2, WIN, -1); passAll(game);
        assertTrue(game.save().wins().stream().allMatch(win -> win.robbingKong() && win.score().fan() == 1
            && !win.score().patterns().contains(SichuanSettlement.Fan.SHOOT_AFTER_KONG)));
        assertTrue(game.save().ledger().stream().noneMatch(SichuanSettlement.Entry::kongTransfer));
        assertEquals(Meld.Type.TRIPLET, game.save().players().get(0).melds().get(0).type());
    }

    @Test void thirdWinnerEndsWithoutDrawChecksOrKongRefundsEvenForTheLastNonReadyPlayer() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m 9p", WAIT, WAIT, WAIT);
        choose(game, 0, CONCEALED_KONG, 8); choose(game, 0, DRAW, -1);
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        choose(game, 3, WIN, -1); choose(game, 2, WIN, -1); choose(game, 1, WIN, -1);
        assertFalse(game.result().exhaustive());
        assertTrue(game.result().drawStatus().isEmpty());
        assertEquals(List.of(-6, 2, 2, 2), game.result().deltas());
        assertEquals(3, game.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_TRANSFER).count());
        assertTrue(game.result().ledger().stream().noneMatch(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND));
        assertTrue(game.nextHand());
        assertEquals(0, game.dealer());
    }

    @Test void exhaustiveChecksRetainReadyKongsAndDoNotRefundAlreadyTransferredIncome() {
        var ready = position(new int[]{2, 2, 2, 2}, false,
            "9m 9m 9m 9m 1m 2m 3m 4m 5m 6m 1p 2p 3p 9p", WAIT, "", "");
        choose(ready, 0, CONCEALED_KONG, 8);
        ready = nextDraw(ready, Tile.parseKind("9s"));
        choose(ready, 0, DRAW, -1);
        ready = emptyWall(ready);
        choose(ready, 0, DISCARD, Tile.parseKind("9s")); passAll(ready);
        assertEquals(SichuanSettlement.DrawStatus.READY, ready.result().drawStatus().get(0));
        assertTrue(ready.result().ledger().stream().noneMatch(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND));
        assertEquals(2, SichuanHandAnalyzer.readyValue(ready.save().players().get(0).hand(), ready.save().players().get(0).melds(), 2, ready.rules()));

        var transferred = position(new int[]{2, 2, 2, 2}, false, "9m 9m 9m 9m 9p 1m 2m 4m 5m 7m 8m 1p 3p 5p", WAIT, "", "");
        choose(transferred, 0, CONCEALED_KONG, 8); transferred = nextDraw(transferred, Tile.parseKind("8p")); choose(transferred, 0, DRAW, -1);
        choose(transferred, 0, DISCARD, Tile.parseKind("9p")); choose(transferred, 1, WIN, -1); passAll(transferred);
        transferred = emptyWall(transferred); choose(transferred, transferred.turn(), DRAW, -1);
        assertEquals(SichuanSettlement.DrawStatus.NOT_READY, transferred.result().drawStatus().get(0));
        assertEquals(3, transferred.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.KONG_TRANSFER).count());
        assertTrue(transferred.result().ledger().stream().noneMatch(entry -> entry.type() == SichuanSettlement.Type.KONG_REFUND));
        transferred.validate();
    }

    @Test void passedWinsBlockSameFanUntilDrawingAndSurviveRestore() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, "9p", "");
        choose(game, 0, DISCARD, Tile.parseKind("9p")); choose(game, 1, PASS, -1); passAll(game);
        game = SichuanCodec.restore(SichuanCodec.save(game));
        game = nextDraw(game, Tile.parseKind("9p"));
        choose(game, 1, DRAW, -1);
        assertEquals(-1, game.save().players().get(1).passedFan());
        choose(game, 1, DISCARD, Tile.kind(game.save().players().get(1).drawn())); passAll(game);
        choose(game, 2, DRAW, -1); choose(game, 2, DISCARD, Tile.parseKind("9p"));
        assertTrue(game.actions(1).stream().noneMatch(action -> action.type() == WIN));
        assertEquals(0, game.save().players().get(1).passedFan());
    }

    @Test void milSeparatesRootsAndKongsWhileTfmjCountsBothAsRoots() {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var quad = take(stock, "9m 9m 9m 9m");
        var hand = take(stock, "1m 1m 1m 2m 2m 2m 1p 1p 1p 2p 2p");
        var score = SichuanHandAnalyzer.score(hand, List.of(new Meld(Meld.Type.CONCEALED_QUAD, quad, 0, Tile.ABSENT)),
            SichuanPreset.SBR_2025.config(), false, false, false, false);
        assertEquals(List.of(SichuanSettlement.Fan.ALL_PUNGS, SichuanSettlement.Fan.KONG), score.patterns());
        var tfmj = SichuanHandAnalyzer.score(hand, List.of(new Meld(Meld.Type.CONCEALED_QUAD, quad, 0, Tile.ABSENT)),
            SichuanPreset.TFMJ_2024.config(), false, false, false, false);
        assertEquals(score.fan(), tfmj.fan());
        assertEquals(List.of(SichuanSettlement.Fan.ALL_PUNGS, SichuanSettlement.Fan.ROOT), tfmj.patterns());
        var melds = new ArrayList<Meld>();
        stock = new ArrayList<>(Tile.sichuanSet());
        for (String kind : List.of("1m", "2m", "1p", "2p")) {
            var tiles = take(stock, kind + " " + kind + " " + kind);
            melds.add(new Meld(Meld.Type.TRIPLET, tiles, 1, tiles.get(0)));
        }
        score = SichuanHandAnalyzer.score(take(stock, "9p 9p"), melds, SichuanPreset.SBR_2025.config(), false, false, false, false);
        assertEquals(List.of(SichuanSettlement.Fan.ALL_PUNGS, SichuanSettlement.Fan.GOLDEN_SINGLE_WAIT), score.patterns());
        assertEquals(4, score.value());
    }

    @Test void maximumReadyValueExcludesWinCircumstancesAndSanctionedReadyPigCannotCollect() {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var hand = take(stock, "1m 1m 2m 2m 3m 3m 4m 4m 5p 5p 6p 6p 7p");
        assertEquals(4, SichuanHandAnalyzer.readyValue(hand, List.of(), 2, SichuanPreset.SBR_2025.config()));
        var game = position(new int[]{2, 2, 2, 2}, true,
            "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 9p 9s", WAIT, "", "");
        assertFalse(game.adjudicateActiveFlowerPig(1));
        assertTrue(game.adjudicateActiveFlowerPig(0));
        assertFalse(game.adjudicateActiveFlowerPig(0));
        game = SichuanCodec.restore(SichuanCodec.save(game));
        choose(game, 0, DISCARD, Tile.parseKind("9s")); passAll(game);
        assertEquals(SichuanSettlement.DrawStatus.ACTIVE_FLOWER_PIG, game.result().drawStatus().get(0));
        assertEquals(1, game.result().ledger().stream().filter(entry -> entry.type() == SichuanSettlement.Type.FLOWER_PIG).count());
        assertTrue(game.result().ledger().stream().noneMatch(entry -> entry.type() == SichuanSettlement.Type.READY_PAYMENT
            && (entry.payer() == 0 || entry.recipient() == 0)));
        assertEquals(-24, game.result().deltas().stream().mapToInt(Integer::intValue).sum());
    }

    @Test void drawPaymentsUseTheRecipientsLargestStructuralWinRatherThanLastTileBonus() {
        var game = position(new int[]{2, 2, 2, 2}, true,
            "1m 2m 4m 5m 7m 8m 1p 2p 4p 5p 7p 8p 9p 9p",
            "1m 1m 2m 2m 3m 3m 4p 4p 5p 5p 6p 6p 7p", WAIT, "");
        choose(game, 0, DISCARD, Tile.parseKind("9p")); passAll(game);
        assertEquals(SichuanSettlement.DrawStatus.NOT_READY, game.result().drawStatus().get(0));
        assertTrue(game.result().ledger().stream().anyMatch(entry -> entry.type() == SichuanSettlement.Type.READY_PAYMENT
            && entry.payer() == 0 && entry.recipient() == 1 && entry.amount() == 4));
    }

    @Test void basicFlushAndSituationalFanHaveIndependentValues() {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var hand = take(stock, "1m 2m 3m 4m 5m 6m 1p 2p 3p 4p 5p 6p 9p 9p");
        var rules = SichuanPreset.SBR_2025.config();
        assertEquals(0, SichuanHandAnalyzer.score(hand, List.of(), rules, false, false, false, false).fan());
        for (int circumstance = 0; circumstance < 4; circumstance++) {
            var score = SichuanHandAnalyzer.score(hand, List.of(), rules, circumstance == 0, circumstance == 1, circumstance == 2, circumstance == 3);
            assertEquals(1, score.fan()); assertEquals(2, score.value());
        }
        stock = new ArrayList<>(Tile.sichuanSet());
        assertEquals(List.of(SichuanSettlement.Fan.FULL_FLUSH), SichuanHandAnalyzer.score(take(stock,
            "1m 2m 3m 1m 2m 3m 4m 5m 6m 7m 8m 9m 5m 5m"), List.of(), rules, false, false, false, false).patterns());
    }

    @Test void firstWinnerDealsNextAndNoWinRetainsDealerWithFreshVoiding() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, "", "");
        choose(game, 0, DISCARD, Tile.parseKind("9p")); choose(game, 1, WIN, -1); passAll(game);
        game = emptyWall(game); choose(game, game.turn(), DRAW, -1);
        var points = game.scores();
        var completed = game.result();
        var restored = SichuanCodec.restore(SichuanCodec.save(game));
        long oldDecision = restored.decision();
        assertTrue(restored.nextHand()); assertTrue(game.nextHand());
        assertEquals(game.save().wall(), restored.save().wall());
        assertEquals(2, restored.handNumber()); assertEquals(1, restored.dealer());
        assertEquals(List.of(13, 14, 13, 13), restored.save().players().stream().map(player -> player.hand().size()).toList());
        assertEquals(points, restored.scores());
        assertEquals(completed, restored.completedHands().get(0).result());
        assertNull(restored.result());
        assertTrue(restored.save().players().stream().allMatch(player -> player.voidSuit() == -1 && !player.won()));
        assertFalse(restored.act(0, oldDecision, 0));
        for (int seat = 0; seat < 4; seat++) choose(restored, seat, VOID_SUIT, 2);
        restored = emptyWall(restored);
        choose(restored, 1, DISCARD, Tile.kind(restored.actions(1).stream().filter(action -> action.type() == DISCARD).findFirst().orElseThrow().tiles().get(0)));
        passAll(restored);
        assertTrue(restored.result().wins().isEmpty());
        assertTrue(restored.nextHand()); assertEquals(1, restored.dealer());
    }

    @Test void eightHandsRetainLedgerDerivedTotalsAndOnlyFinalHandEndsMatch() {
        for (var preset : SichuanPreset.values()) {
            var game = new SichuanGame(71, preset.config(), Tile.sichuanSet());
            var expected = new int[4];
            for (int number = 1; number <= 8; number++) {
                int steps = 0;
                while (!game.ended()) {
                    assertTrue(++steps < 500);
                    for (int seat = 0; seat < 4; seat++) {
                        var actions = game.actions(seat);
                        if (actions.isEmpty()) continue;
                        var preferred = actions.stream().filter(action -> action.type() == WIN || action.type() == PASS
                            || action.type() == DRAW || action.type() == DISCARD || action.type() == VOID_SUIT).findFirst().orElseThrow();
                        assertTrue(game.act(seat, game.decision(), actions.indexOf(preferred)));
                    }
                }
                for (int seat = 0; seat < 4; seat++) expected[seat] += game.result().deltas().get(seat);
                assertEquals(java.util.Arrays.stream(expected).boxed().toList(), game.scores());
                assertEquals(number, game.completedHands().size());
                assertEquals(number == 8 ? SichuanGame.Phase.MATCH_END : SichuanGame.Phase.HAND_END, game.phase());
                game = SichuanCodec.restore(SichuanCodec.save(game));
                assertEquals(number < 8, game.nextHand());
            }
            assertTrue(game.view(0).actions().isEmpty());
            assertEquals(game.scores(), game.view(-1).scores());
        }
    }

    @Test void sessionWaitsForLifecycleConfirmationsAndRestoresSavedDeadlineWithoutClockCharges() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, WAIT, WAIT);
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        var session = session(game);
        for (int seat = 1; seat < 4; seat++) {
            var view = session.view(id(seat));
            int index = view.game().actions().indexOf(new SichuanAction(WIN));
            assertTrue(session.act(id(seat), session.tableId(), view.incarnation(), view.game().decision(), index));
        }
        assertEquals(TableSession.Lifecycle.PLAYING, session.lifecycle());
        var end = session.view(id(0));
        assertTrue(end.canConfirmNextHand());
        assertEquals(0, end.confirmedCount());
        var clocks = session.save().clocks();
        assertFalse(session.confirmNextHand(UUID.randomUUID(), session.tableId(), end.incarnation(), end.game().decision()));
        assertTrue(session.confirmNextHand(id(0), session.tableId(), end.incarnation(), end.game().decision()));
        assertFalse(session.confirmNextHand(id(0), session.tableId(), end.incarnation(), end.game().decision()));
        assertFalse(session.view(id(0)).canConfirmNextHand());
        assertEquals(1, session.view(id(1)).confirmedCount());
        assertTrue(session.requestExit(id(1)));
        assertFalse(session.view(id(2)).canConfirmNextHand());
        assertFalse(session.confirmNextHand(id(2), session.tableId(), end.incarnation(), end.game().decision()));
        int reading = session.view(id(0)).settlementTicks();
        session.tick();
        assertEquals(reading, session.view(id(0)).settlementTicks());
        long vote = session.roomView(id(0)).exitVote().id();
        assertTrue(session.answerExit(id(2), vote, false));
        assertTrue(session.view(id(2)).canConfirmNextHand());
        for (int tick = 0; tick < 199; tick++) session.tick();
        assertEquals(clocks, session.save().clocks());
        var restored = SichuanCodec.restoreSession(SichuanCodec.saveSession(session));
        assertTrue(restored.paused());
        assertEquals(1, restored.view(id(0)).settlementTicks());
        assertEquals(1, restored.view(id(0)).confirmed());
        restored.tick(); assertEquals(1, restored.game().handNumber());
        restored.synchronizeSeats(Map.of(id(0), 0), java.util.Set.of(id(0), id(1), id(2), id(3)));
        restored.tick(); assertEquals(2, restored.game().handNumber());
        assertEquals(0, restored.view(id(0)).confirmed());
        assertEquals(end.game().scores(), restored.game().scores());
        assertFalse(restored.confirmNextHand(id(0), restored.tableId(), end.incarnation(), end.game().decision()));
        assertTrue(restored.view(id(0)).game().seats().get(1).hand().stream().allMatch(tile -> tile == Tile.HIDDEN));
        assertEquals(restored.view(id(0)), SichuanCodec.decodeSessionView(SichuanCodec.encodeSessionView(restored.view(id(0)))));
    }

    @Test void allHumanConfirmationsAdvanceOnceAndMatchEndIsTerminalForTheSession() {
        var game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, WAIT, WAIT);
        choose(game, 0, DISCARD, Tile.parseKind("9p"));
        choose(game, 1, WIN, -1); choose(game, 2, WIN, -1); choose(game, 3, WIN, -1);
        var session = session(game);
        var view = session.view(id(0));
        for (int seat = 0; seat < 4; seat++)
            assertTrue(session.confirmNextHand(id(seat), session.tableId(), view.incarnation(), view.game().decision()));
        assertEquals(2, session.game().handNumber());
        assertFalse(session.confirmNextHand(id(3), session.tableId(), view.incarnation(), view.game().decision()));

        game = position(new int[]{2, 2, 2, 2}, false, "9p", WAIT, WAIT, WAIT);
        var state = game.save(); var base = game.rules();
        var rules = new SichuanRules(base.fanCap(), base.selfDrawBonus(), base.concealedKongPayment(), base.discardKongPayment(),
            base.addedKongPayment(), base.activeFlowerPigPenalty(), base.transferKongOnShoot(), base.refundKongWhenNotReady(), 1, base.separateKongFan(), base.selectFirstDiscard(), base.addedKongAfterKongIsShoot(), base.eastWestLongWall());
        game = SichuanGame.restore(new SichuanGame.State(state.format(), rules, state.nextWallSeed(), 1, List.of(), state.phase(),
            state.revision(), state.decision(), state.turn(), state.wall(), state.players(), state.focus(), state.supplier(), state.pendingKong(),
            state.replacementDraw(), state.afterKong(), state.kongLedgerStart(), state.responses(), state.wins(), state.ledger(), state.result()));
        choose(game, 0, DISCARD, Tile.parseKind("9p")); session = session(game);
        for (int seat = 1; seat < 4; seat++) {
            view = session.view(id(seat));
            assertTrue(session.act(id(seat), session.tableId(), view.incarnation(), view.game().decision(), view.game().actions().indexOf(new SichuanAction(WIN))));
        }
        assertEquals(TableSession.Lifecycle.FINISHED, session.lifecycle());
        assertEquals(SichuanGame.Phase.MATCH_END, session.game().phase());
        assertFalse(session.view(id(0)).canConfirmNextHand());
        assertFalse(session.confirmNextHand(id(0), session.tableId(), session.incarnation(), session.game().decision()));
        var completed = session.save().game(); session.tick(); assertEquals(completed, session.save().game());
        assertEquals(session.game().scores(), SichuanCodec.restoreSession(SichuanCodec.saveSession(session)).game().scores());
    }

    private static UUID id(int seat) { return new UUID(42, seat + 1); }
    private static SichuanSession session(SichuanGame game) {
        var roster = new ArrayList<TableParticipant>();
        for (int seat = 0; seat < 4; seat++) roster.add(new TableParticipant(id(seat), "player" + seat));
        var seating = new RoomSeating(); seating.positioned(4);
        var room = new TableSession.State(new UUID(41, 1), MahjongVariant.SICHUAN, 4, id(0), roster, seating.save(),
            game.phase() == SichuanGame.Phase.MATCH_END ? TableSession.Lifecycle.FINISHED : TableSession.Lifecycle.PLAYING,
            1, 1, 71, false, null, null, 0, 0);
        var control = new TimeControl(1, 1);
        var clocks = Collections.nCopies(4, new TimeControl.Clock(20, 20, false));
        var session = SichuanSession.restore(new SichuanSession.State(SichuanSession.State.FORMAT, room, game.rules(), Tile.sichuanSet(),
            control, clocks, 0, 0, game.save(), null, null, List.of()));
        session.synchronizeSeats(Map.of(id(0), 0, id(1), 1, id(2), 2, id(3), 3), java.util.Set.of(id(0), id(1), id(2), id(3)));
        return session;
    }

    private static SichuanGame emptyWall(SichuanGame game) {
        var state = game.save();
        var players = new ArrayList<>(state.players());
        var player = players.get(2);
        var river = new ArrayList<>(player.river());
        for (int tile : state.wall().slots()) if (tile != Tile.ABSENT) river.add(new SichuanPlayerState.Discard(tile, false));
        players.set(2, new SichuanPlayerState(player.hand(), player.melds(), river, player.voidSuit(), player.won(), player.drawn(), player.passedFan(), player.firstDiscard()));
        return SichuanGame.restore(copy(state, players, new SichuanWall.State(Collections.nCopies(108, Tile.ABSENT), state.wall().dealer(), state.wall().die1(), state.wall().die2(), 108, state.wall().eastWestLongWall())));
    }

    private static SichuanGame nextDraw(SichuanGame game, int kind) {
        var state = game.save();
        var wall = state.wall();
        var slots = new ArrayList<>(wall.slots());
        int from = -1;
        for (int index = 0; index < slots.size(); index++) if (slots.get(index) != Tile.ABSENT && Tile.kind(slots.get(index)) == kind) { from = index; break; }
        assertTrue(from >= 0);
        int next = SichuanWallLayout.traversal(wall.dealer(), wall.die1(), wall.die2(), wall.eastWestLongWall()).get(wall.cursor());
        Collections.swap(slots, from, next);
        return SichuanGame.restore(copy(state, state.players(), new SichuanWall.State(slots, wall.dealer(), wall.die1(), wall.die2(), wall.cursor(), wall.eastWestLongWall())));
    }

    private static void choose(SichuanGame game, int seat, SichuanAction.Type type, int argument) {
        var actions = game.actions(seat);
        for (int index = 0; index < actions.size(); index++) {
            var action = actions.get(index);
            if (action.type() == type && (argument < 0 || type == VOID_SUIT && action.suit() == argument
                || !action.tiles().isEmpty() && Tile.kind(action.tiles().get(0)) == argument)) {
                assertTrue(game.act(seat, game.decision(), index)); return;
            }
        }
        fail("Missing " + type + " for " + seat + ": " + actions);
    }
    private static void passAll(SichuanGame game) {
        for (int seat = 0; seat < 4 && game.phase() == SichuanGame.Phase.REACTION; seat++)
            if (!game.actions(seat).isEmpty()) choose(game, seat, PASS, -1);
    }
    private static SichuanGame position(int[] suits, boolean exhausted, String... hands) {
        var stock = new ArrayList<>(Tile.sichuanSet());
        var selected = new ArrayList<ArrayList<Integer>>();
        for (String hand : hands) selected.add(take(stock, hand));
        for (int seat = 0; seat < 4; seat++) while (selected.get(seat).size() < (seat == 0 ? 14 : 13)) selected.get(seat).add(stock.remove(0));
        var players = new ArrayList<SichuanPlayerState>();
        for (int seat = 0; seat < 4; seat++) players.add(new SichuanPlayerState(selected.get(seat), List.of(),
            exhausted && seat == 0 ? stock.stream().map(tile -> new SichuanPlayerState.Discard(tile, false)).toList() : List.of(),
            suits[seat], false, seat == 0 ? selected.get(seat).get(0) : Tile.ABSENT, -1, Tile.ABSENT));
        var slots = new ArrayList<>(Collections.nCopies(108, Tile.ABSENT));
        var order = SichuanWallLayout.traversal(0, 1, 1, false);
        if (!exhausted) for (int index = 0; index < stock.size(); index++) slots.set(order.get(53 + index), stock.get(index));
        var state = new SichuanGame.State(SichuanGame.State.FORMAT, SichuanPreset.TFMJ_2024.config(), 17, 1, List.of(), SichuanGame.Phase.TURN, 1, 1, 0,
            new SichuanWall.State(slots, 0, 1, 1, exhausted ? 108 : 53, false), players, Tile.ABSENT, -1, -1,
            false, false, -1, List.of(), List.of(), List.of(), null);
        return SichuanGame.restore(state);
    }
    private static ArrayList<Integer> take(ArrayList<Integer> stock, String notation) {
        var hand = new ArrayList<Integer>();
        if (!notation.isBlank()) for (String tile : notation.split(" ")) {
            int kind = Tile.parseKind(tile);
            int physical = stock.stream().filter(candidate -> Tile.kind(candidate) == kind).findFirst().orElseThrow();
            hand.add(physical); stock.remove(Integer.valueOf(physical));
        }
        return hand;
    }
    private static SichuanGame.State copy(SichuanGame.State state, List<SichuanPlayerState> players, SichuanWall.State wall) {
        return new SichuanGame.State(state.format(), state.rules(), state.nextWallSeed(), state.handNumber(), state.completedHands(), state.phase(), state.revision(), state.decision(), state.turn(),
            wall, players, state.focus(), state.supplier(), state.pendingKong(), state.replacementDraw(), state.afterKong(),
            state.kongLedgerStart(), state.responses(), state.wins(), state.ledger(), state.result());
    }
    private static SichuanGame withRules(SichuanGame game, SichuanRules rules) {
        var state = game.save();
        var wall = state.wall();
        var before = SichuanWallLayout.traversal(wall.dealer(), wall.die1(), wall.die2(), wall.eastWestLongWall());
        var after = SichuanWallLayout.traversal(wall.dealer(), wall.die1(), wall.die2(), rules.eastWestLongWall());
        var slots = new ArrayList<>(wall.slots());
        for (int index = 0; index < 108; index++) slots.set(after.get(index), wall.slots().get(before.get(index)));
        wall = new SichuanWall.State(slots, wall.dealer(), wall.die1(), wall.die2(), wall.cursor(), rules.eastWestLongWall());
        return SichuanGame.restore(new SichuanGame.State(state.format(), rules, state.nextWallSeed(), state.handNumber(), state.completedHands(),
            state.phase(), state.revision(), state.decision(), state.turn(), wall, state.players(), state.focus(), state.supplier(),
            state.pendingKong(), state.replacementDraw(), state.afterKong(), state.kongLedgerStart(), state.responses(), state.wins(), state.ledger(), state.result()));
    }
}

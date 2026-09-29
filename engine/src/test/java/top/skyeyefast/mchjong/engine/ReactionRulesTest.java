package top.skyeyefast.mchjong.engine;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ReactionRulesTest {
    /** Build legal physical ownership, then use only the public action protocol. */
    private static final class Fixture {
        final RiichiGame game;
        final Set<Integer> owned = new HashSet<>();
        Fixture(RiichiPreset rules) {
            var session = new RiichiSession(UUID.randomUUID(), rules, 5566);
            for (int seat = 0; seat < rules.players(); seat++)
                session.join(new UUID(40, seat), "Player " + seat, seat);
            session.startMatch();
            game = session.game();
            game.wall = new Wall(rules.config(), 7788, game.dealer);
            for (int s = 0; s < rules.players(); s++) {
                RiichiPlayerState p = game.players[s];
                p.resetHand();
                p.member.id = new UUID(40, s); p.member.name = "Player " + s;
                p.member.presence = PlayerPresence.SEATED;
                p.points = rules.startingPoints(); p.firstTurn = false;
            }
            game.uninterrupted = false;
        }
        List<Integer> take(String text) {
            var result = new ArrayList<Integer>();
            for (int parsed : TestHands.tiles(text)) {
                int kind = Tile.kind(parsed);
                int tile = Tile.set(game.rules.sanma(), game.rules.redFives()).stream()
                    .filter(candidate -> Tile.kind(candidate) == kind && !owned.contains(candidate)).findFirst().orElseThrow();
                owned.add(tile); result.add(tile);
            }
            return result;
        }
        void hand(int seat, String text) { game.players[seat].hand.addAll(take(text)); }
        void start(int from, int drawn) {
            var rest = new ArrayDeque<>(Tile.set(game.rules.sanma(), game.rules.redFives()).stream().filter(t -> !owned.contains(t)).toList());
            for (int s = 0; s < game.rules.players(); s++) {
                int count = s == from ? 14 : 13;
                while (game.players[s].hand.size() < count) {
                    int tile = rest.removeFirst(); owned.add(tile); game.players[s].hand.add(tile);
                }
                assertEquals(count, game.players[s].hand.size());
            }
            var unowned = Tile.set(game.rules.sanma(), game.rules.redFives()).stream().filter(t -> !owned.contains(t)).toList();
            game.wall.tiles = new ArrayList<>(Collections.nCopies(owned.size(), Tile.ABSENT));
            game.wall.tiles.addAll(unowned);
            game.wall.cursor = owned.size();
            // Unused green dragons indicate red dragons, which these fixtures do not hold.
            for (int index : new int[]{game.wall.dora.getFirst(), game.wall.ura.getFirst()}) {
                int available = -1;
                for (int i = game.wall.cursor; i < game.wall.tiles.size(); i++)
                    if (Tile.kind(game.wall.tiles.get(i)) == Tile.GREEN && i != game.wall.dora.getFirst()) { available = i; break; }
                if (available >= 0) Collections.swap(game.wall.tiles, index, available);
            }
            game.turn = from;
            game.players[from].drawn = drawn;
            game.players[from].canDeclare = true;
            game.newDecision(RiichiGame.Phase.TURN);
            game.options.set(from, LegalActions.onTurn(game, from));
            game.validate();
        }
        void riichi(int seat) {
            game.players[seat].riichi = true;
            game.players[seat].points -= 1000;
            game.riichiSticks++;
        }
        void act(int seat, RiichiAction.Type type) { act(seat, type, null); }
        void act(int seat, RiichiAction.Type type, Integer tile) {
            RiichiView view = game.view(game.players[seat].member.id);
            for (int i = 0; i < view.actions().size(); i++) {
                RiichiAction action = view.actions().get(i);
                if (action.type() == type && (tile == null || action.tiles().contains(tile))) {
                    assertTrue(game.act(game.players[seat].member.id, view.decision(), i));
                    game.validate();
                    return;
                }
            }
            fail("Missing " + type + " for " + seat + ": " + view.actions());
        }
        void passOthers() {
            int guard = 0;
            while (game.phase() == RiichiGame.Phase.REACTION && guard++ < 4) {
                boolean passed = false;
                for (int s = 0; s < game.rules.players(); s++) {
                    if (game.view(game.players[s].member.id).actions().stream().anyMatch(a -> a.type() == RiichiAction.Type.PASS)) {
                        act(s, RiichiAction.Type.PASS); passed = true; break;
                    }
                }
                if (!passed) break;
            }
        }
    }

    @Test void declarationPublishesDoubleRiichiOnlyOnAnUninterruptedFirstTurn() {
        for (int state = 0; state < 3; state++) {
            Fixture f = new Fixture(RiichiPreset.TENHOU_4);
            f.hand(0, "123456789m111p56z");
            f.game.players[0].firstTurn = state != 1;
            f.game.uninterrupted = state != 2;
            int discard = f.game.players[0].hand.getLast();
            f.start(0, discard);
            assertFalse(f.game.view(null).seats().getFirst().doubleRiichi());
            f.act(0, RiichiAction.Type.RIICHI, discard);
            assertEquals(state == 0, f.game.view(null).seats().getFirst().doubleRiichi());
            assertEquals(state == 0, f.game.view(f.game.players[1].member.id).seats().getFirst().doubleRiichi());
        }
    }

    @Test void automaticWinClaimsConcealedKanRobberyWithoutTakingAReplacementTile() {
        Fixture f = new Fixture(RiichiPreset.MAHJONG_SOUL_4);
        f.hand(1, "11m19p19s1234567z");
        f.hand(0, "9999m");
        int fourth = f.game.players[0].hand.getLast();
        f.start(0, fourth);
        for (int seat = 1; seat < 4; seat++) {
            assertTrue(f.game.configureAutoPlay(f.game.players[seat].member.id, f.game.decision(), AutoPlay.Option.WIN, true));
            assertTrue(f.game.configureAutoPlay(f.game.players[seat].member.id, f.game.decision(), AutoPlay.Option.NO_CALLS, true));
        }
        f.act(0, RiichiAction.Type.CLOSED_KAN, fourth);
        for (int tick = 0; tick < RiichiGame.AUTO_ACTION_TICKS + 3; tick++) f.game.tick();
        f.game.validate();
        assertEquals(List.of(1), f.game.view(null).wins().stream().map(RiichiView.Win::seat).toList());
        assertTrue(f.game.players[0].melds.isEmpty());
        assertEquals(0, f.game.wall.replacementIndex);
    }

    @Test void automaticWinsUseLegalScoringAndPreserveSimultaneousRonPriority() {
        Fixture f = new Fixture(RiichiPreset.MAHJONG_SOUL_4);
        f.hand(1, "123456789m111p5z");
        f.hand(2, "123456789p111s5z");
        f.hand(3, "123456789s111m5z");
        int discarded = f.take("5z").getFirst();
        f.game.players[0].hand.add(discarded);
        for (int seat = 1; seat <= 3; seat++) f.riichi(seat);
        f.start(0, discarded);
        for (int seat = 1; seat <= 3; seat++) {
            UUID player = f.game.players[seat].member.id;
            assertTrue(f.game.configureAutoPlay(player, f.game.decision(), AutoPlay.Option.WIN, true));
            assertTrue(f.game.configureAutoPlay(player, f.game.decision(), AutoPlay.Option.NO_CALLS, true));
        }
        f.act(0, RiichiAction.Type.DISCARD, discarded);
        for (int i = 0; i < RiichiGame.AUTO_ACTION_TICKS + 3; i++) { f.game.tick(); f.game.validate(); }
        assertEquals(List.of(1, 2, 3), f.game.view(null).wins().stream().map(RiichiView.Win::seat).toList());
        assertTrue(f.game.view(null).wins().stream().allMatch(win -> win.score().ron() > 0));
    }

    @Test void automaticTsumoSettlesInsteadOfDiscardingTheWinningTile() {
        Fixture f = new Fixture(RiichiPreset.TENHOU_4);
        f.hand(0, "123456789m111p55z");
        f.riichi(0);
        int drawn = f.game.players[0].hand.getLast();
        f.start(0, drawn);
        assertTrue(f.game.configureAutoPlay(f.game.players[0].member.id, f.game.decision(), AutoPlay.Option.WIN, true));
        assertTrue(f.game.configureAutoPlay(f.game.players[0].member.id, f.game.decision(), AutoPlay.Option.DISCARD, true));
        for (int i = 0; i < RiichiGame.AUTO_ACTION_TICKS; i++) f.game.tick();
        f.game.validate();
        assertEquals(1, f.game.view(null).wins().size());
        assertEquals(0, f.game.view(null).wins().getFirst().seat());
        assertTrue(f.game.players[0].river.isEmpty());
        assertTrue(f.game.players[0].hand.contains(drawn));
    }

    @ParameterizedTest @EnumSource(value = RiichiPreset.class, names = {"MAHJONG_SOUL_4", "TENHOU_4", "M_LEAGUE", "JPML_A", "WRC"})
    void simultaneousRonUsesRulesetPriorityRatherThanPacketArrival(RiichiPreset rules) {
        Fixture f = new Fixture(rules);
        f.hand(1, "123456789m111p5z");
        f.hand(2, "123456789p111s5z");
        f.hand(3, "123456789s111m5z");
        int discarded = f.take("5z").getFirst();
        f.game.players[0].hand.add(discarded);
        for (int s = 1; s <= 3; s++) f.riichi(s);
        f.game.honba = 2;
        f.start(0, discarded);
        int[] before = Arrays.stream(f.game.players).mapToInt(p -> p.points).toArray();
        f.act(0, RiichiAction.Type.DISCARD, discarded);
        long token = f.game.view(f.game.players[1].member.id).decision();
        f.act(3, RiichiAction.Type.RON);
        assertEquals(token, f.game.view(f.game.players[1].member.id).decision(), "Another response must not stale a simultaneous choice");
        f.act(2, RiichiAction.Type.RON);
        f.act(1, RiichiAction.Type.RON);
        if (rules == RiichiPreset.TENHOU_4) {
            assertEquals("triple_ron", f.game.view(null).result());
            assertTrue(f.game.view(null).wins().isEmpty());
            assertArrayEquals(before, Arrays.stream(f.game.players).mapToInt(p -> p.points).toArray());
        } else {
            var winners = f.game.view(null).wins();
            assertEquals(rules.headBump() ? List.of(1) : List.of(1,2,3), winners.stream().map(RiichiView.Win::seat).toList());
            for (var win : winners) {
                int extra = win.seat() == 1 ? 3600 : 0;
                assertEquals(win.score().ron() + extra, f.game.players[win.seat()].points - before[win.seat()]);
            }
        }
    }

    @Test void ronBeatsEarlierPonAndChiRequests() {
        Fixture f = new Fixture(RiichiPreset.MAHJONG_SOUL_4);
        f.hand(1, "45p1236789s55z19m");
        f.hand(2, "123456789m55s78p");
        f.hand(3, "66p123456s11z789m");
        f.riichi(2);
        int discarded = f.take("6p").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);
        f.act(0, RiichiAction.Type.DISCARD, discarded);
        f.act(3, RiichiAction.Type.PON);
        f.act(1, RiichiAction.Type.CHI);
        f.act(2, RiichiAction.Type.RON);
        assertEquals(List.of(2), f.game.view(null).wins().stream().map(RiichiView.Win::seat).toList());
        assertTrue(f.game.players[1].melds.isEmpty()); assertTrue(f.game.players[3].melds.isEmpty());
    }

    @Test void botRonSettlesBeforeHumanCallOnlyChoices() {
        Fixture f = new Fixture(RiichiPreset.MAHJONG_SOUL_4);
        f.hand(1, "45p1236789s55z19m");
        f.hand(2, "123456789m55s78p");
        f.hand(3, "66p123456s11z789m");
        f.riichi(2);
        f.game.players[2].member.bot = true;
        int discarded = f.take("6p").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);

        f.act(0, RiichiAction.Type.DISCARD, discarded);

        assertEquals(List.of(2), f.game.view(null).wins().stream().map(RiichiView.Win::seat).toList());
        assertTrue(f.game.players[1].melds.isEmpty());
        assertTrue(f.game.players[3].melds.isEmpty());
        assertEquals(List.of(new RiichiAction(RiichiAction.Type.SKIP_SETTLEMENT), new RiichiAction(RiichiAction.Type.SETTLEMENT_DONE)),
            f.game.view(f.game.players[1].member.id).actions());
    }

    @Test void botRonStillWaitsForAnotherPlayersRon() {
        Fixture f = new Fixture(RiichiPreset.MAHJONG_SOUL_4);
        f.hand(1, "123456789m111p5z");
        f.hand(2, "123456789p111s5z");
        int discarded = f.take("5z").getFirst();
        f.game.players[0].hand.add(discarded);
        f.riichi(1);
        f.riichi(2);
        f.game.players[2].member.bot = true;
        f.start(0, discarded);

        f.act(0, RiichiAction.Type.DISCARD, discarded);
        assertEquals(RiichiGame.Phase.REACTION, f.game.phase());
        assertTrue(f.game.view(f.game.players[1].member.id).actions().stream().anyMatch(a -> a.type() == RiichiAction.Type.RON));
        f.act(1, RiichiAction.Type.RON);

        assertEquals(List.of(1, 2), f.game.view(null).wins().stream().map(RiichiView.Win::seat).toList());
    }

    @Test void equivalentPhysicalCopiesYieldOnePonChoice() {
        Fixture f = new Fixture(RiichiPreset.MAHJONG_SOUL_4);
        f.hand(3, "666p");
        int discarded = f.take("6p").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);
        f.act(0, RiichiAction.Type.DISCARD, discarded);

        assertEquals(1, f.game.view(f.game.players[3].member.id).actions().stream()
            .filter(a -> a.type() == RiichiAction.Type.PON).count());
    }

    @Test void redFiveConsumptionRemainsASeparatePonAndChiChoice() {
        Fixture pon = new Fixture(RiichiPreset.MAHJONG_SOUL_4);
        pon.hand(3, "555p");
        int ponDiscard = pon.take("5p").getFirst();
        pon.game.players[0].hand.add(ponDiscard);
        pon.start(0, ponDiscard);
        pon.act(0, RiichiAction.Type.DISCARD, ponDiscard);
        var ponChoices = pon.game.view(pon.game.players[3].member.id).actions().stream()
            .filter(a -> a.type() == RiichiAction.Type.PON).toList();
        assertEquals(2, ponChoices.size());
        assertEquals(Set.of(0L, 1L), ponChoices.stream()
            .map(a -> a.tiles().stream().filter(Tile::red).count()).collect(java.util.stream.Collectors.toSet()));

        Fixture chi = new Fixture(RiichiPreset.MAHJONG_SOUL_4);
        chi.hand(1, "4455p");
        int chiDiscard = chi.take("6p").getFirst();
        chi.game.players[0].hand.add(chiDiscard);
        chi.start(0, chiDiscard);
        chi.act(0, RiichiAction.Type.DISCARD, chiDiscard);
        var chiChoices = chi.game.view(chi.game.players[1].member.id).actions().stream()
            .filter(a -> a.type() == RiichiAction.Type.CHI).toList();
        assertEquals(2, chiChoices.size());
        assertEquals(Set.of(0L, 1L), chiChoices.stream()
            .map(a -> a.tiles().stream().filter(Tile::red).count()).collect(java.util.stream.Collectors.toSet()));
    }

    @ParameterizedTest @EnumSource(value = RiichiPreset.class, names = {"TENHOU_4", "WRC"})
    void aCallClearsTemporaryFuritenOnlyUnderWrcRules(RiichiPreset rules) {
        Fixture f = new Fixture(rules);
        f.hand(3, "123456m789p55z11s");
        int discarded = f.take("5z").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);
        f.act(0, RiichiAction.Type.DISCARD, discarded);
        assertTrue(f.game.view(f.game.players[3].member.id).actions().stream().anyMatch(a -> a.type() == RiichiAction.Type.RON));
        f.act(3, RiichiAction.Type.PON);
        f.passOthers();
        assertEquals(RiichiGame.Phase.TURN, f.game.phase());
        assertEquals(3, f.game.turn);
        assertEquals(rules != RiichiPreset.WRC, f.game.players[3].temporaryFuriten);
        f.act(3, RiichiAction.Type.DISCARD);
        assertEquals(rules != RiichiPreset.WRC, f.game.players[3].temporaryFuriten);
    }

    @ParameterizedTest @EnumSource(value = RiichiPreset.class, names = {"MAHJONG_SOUL_4", "TENHOU_4", "M_LEAGUE", "JPML_A", "WRC"})
    void concealedKanRobberyIsKokushiAndMahjongSoulOnly(RiichiPreset rules) {
        Fixture f = new Fixture(rules);
        f.hand(1, "11m19p19s1234567z");
        f.hand(0, "9999m");
        int fourth = f.game.players[0].hand.getLast();
        f.start(0, fourth);
        f.act(0, RiichiAction.Type.CLOSED_KAN, fourth);
        if (rules.mahjongSoul()) {
            assertEquals(RiichiGame.Phase.REACTION, f.game.phase());
            assertTrue(f.game.view(null).focus().declaration());
            assertEquals(fourth, f.game.view(null).focus().tile());
            f.act(1, RiichiAction.Type.RON); f.passOthers();
            assertEquals(1, f.game.view(null).wins().size());
            assertTrue(f.game.players[0].melds.isEmpty());
            assertEquals(0, f.game.wall.replacementIndex);
            assertEquals(1, f.game.wall.revealed);
        } else {
            assertEquals(RiichiGame.Phase.TURN, f.game.phase());
            assertEquals(1, f.game.players[0].melds.size());
            assertEquals(1, f.game.wall.replacementIndex);
            assertEquals(rules.kanDora() ? 2 : 1, f.game.wall.revealed);
        }
    }

    @Test void wrcYakulessPassStillCausesTemporaryFuriten() {
        Fixture f = new Fixture(RiichiPreset.WRC);
        f.hand(3, "123m456p789s44z45m");
        int discarded = f.take("6m").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);
        f.act(0, RiichiAction.Type.DISCARD, discarded);
        assertTrue(f.game.players[3].temporaryFuriten);
        assertTrue(f.game.view(f.game.players[3].member.id).actions().stream().noneMatch(a -> a.type() == RiichiAction.Type.RON));
    }

    @ParameterizedTest @EnumSource(value = RiichiPreset.class, names = {"MAHJONG_SOUL_3", "TENHOU_3"})
    void automaticNorthUsesTheLegalDeclarationAndReplacementDraw(RiichiPreset rules) {
        Fixture f = new Fixture(rules);
        f.hand(0, "147p258s19m11235z");
        int north = f.take("4z").getFirst();
        f.game.players[0].hand.add(north);
        f.start(0, north);
        assertTrue(f.game.configureAutoPlay(f.game.players[0].member.id, f.game.decision(), AutoPlay.Option.KITA, true));
        for (int tick = 0; tick < RiichiGame.AUTO_ACTION_TICKS; tick++) f.game.tick();
        f.passOthers();
        assertEquals(List.of(north), f.game.players[0].norths);
        assertEquals(1, f.game.wall.replacementIndex);
        assertEquals(RiichiGame.Phase.TURN, f.game.phase());
        assertNotEquals(north, f.game.players[0].drawn);
        assertTrue(f.game.players[0].river.isEmpty());
        f.game.validate();
    }

    @ParameterizedTest @EnumSource(value = RiichiPreset.class, names = {"MAHJONG_SOUL_3", "TENHOU_3"})
    void automaticNorthCanBeRobbedByOrdinaryYakuButDoesNotGiveChankan(RiichiPreset rules) {
        Fixture f = new Fixture(rules);
        f.hand(1, "123456789p111s4z");
        int north = f.take("4z").getFirst();
        f.game.players[0].hand.add(north);
        f.start(0, north);
        assertTrue(f.game.configureAutoPlay(f.game.players[0].member.id, f.game.decision(), AutoPlay.Option.KITA, true));
        for (int tick = 0; tick < RiichiGame.AUTO_ACTION_TICKS; tick++) f.game.tick();
        f.game.validate();
        f.act(1, RiichiAction.Type.RON); f.passOthers();
        assertTrue(f.game.players[0].norths.isEmpty());
        assertEquals(0, f.game.wall.replacementIndex);
        assertFalse(f.game.view(null).wins().getFirst().score().yaku().contains("Chankan"));
        assertEquals(0, f.game.view(null).wins().getFirst().score().yakuman());
    }
}

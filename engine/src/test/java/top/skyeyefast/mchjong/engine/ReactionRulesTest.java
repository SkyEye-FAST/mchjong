package top.skyeyefast.mchjong.engine;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.*;

class ReactionRulesTest {
    /** Build legal physical ownership, then use only the public action protocol. */
    private static final class Fixture {
        final Game game;
        final Set<Integer> owned = new HashSet<>();
        Fixture(RuleSet rules) {
            game = new Game(UUID.randomUUID(), rules, 5566);
            game.wall = new Wall(rules.config(), 7788, game.dealer);
            for (int s = 0; s < rules.players(); s++) {
                PlayerState p = game.players[s];
                p.id = new UUID(40, s); p.name = "Player " + s;
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
            game.newDecision(Game.Phase.TURN);
            game.options.set(from, LegalActions.onTurn(game, from));
            game.validate();
        }
        void riichi(int seat) {
            game.players[seat].riichi = true;
            game.players[seat].points -= 1000;
            game.riichiSticks++;
        }
        void act(int seat, Action.Type type) { act(seat, type, null); }
        void act(int seat, Action.Type type, Integer tile) {
            TableView view = game.view(game.players[seat].id);
            for (int i = 0; i < view.actions().size(); i++) {
                Action action = view.actions().get(i);
                if (action.type() == type && (tile == null || action.tiles().contains(tile))) {
                    assertTrue(game.act(game.players[seat].id, view.decision(), i));
                    game.validate();
                    return;
                }
            }
            fail("Missing " + type + " for " + seat + ": " + view.actions());
        }
        void passOthers() {
            int guard = 0;
            while (game.phase() == Game.Phase.REACTION && guard++ < 4) {
                boolean passed = false;
                for (int s = 0; s < game.rules.players(); s++) {
                    if (game.view(game.players[s].id).actions().stream().anyMatch(a -> a.type() == Action.Type.PASS)) {
                        act(s, Action.Type.PASS); passed = true; break;
                    }
                }
                if (!passed) break;
            }
        }
    }

    @Test void automaticWinClaimsConcealedKanRobberyWithoutTakingAReplacementTile() {
        Fixture f = new Fixture(RuleSet.MAHJONG_SOUL_4);
        f.hand(1, "11m19p19s1234567z");
        f.hand(0, "9999m");
        int fourth = f.game.players[0].hand.getLast();
        f.start(0, fourth);
        for (int seat = 1; seat < 4; seat++) {
            assertTrue(f.game.configureAutoPlay(f.game.players[seat].id, f.game.decision, AutoPlay.Option.WIN, true));
            assertTrue(f.game.configureAutoPlay(f.game.players[seat].id, f.game.decision, AutoPlay.Option.NO_CALLS, true));
        }
        f.act(0, Action.Type.CLOSED_KAN, fourth);
        for (int tick = 0; tick < Game.AUTO_ACTION_TICKS + 3; tick++) f.game.tick();
        f.game.validate();
        assertEquals(List.of(1), f.game.view(null).wins().stream().map(TableView.Win::seat).toList());
        assertTrue(f.game.players[0].melds.isEmpty());
        assertEquals(0, f.game.wall.replacementIndex);
    }

    @Test void automaticWinsUseLegalScoringAndPreserveSimultaneousRonPriority() {
        Fixture f = new Fixture(RuleSet.MAHJONG_SOUL_4);
        f.hand(1, "123456789m111p5z");
        f.hand(2, "123456789p111s5z");
        f.hand(3, "123456789s111m5z");
        int discarded = f.take("5z").getFirst();
        f.game.players[0].hand.add(discarded);
        for (int seat = 1; seat <= 3; seat++) f.riichi(seat);
        f.start(0, discarded);
        for (int seat = 1; seat <= 3; seat++) {
            UUID player = f.game.players[seat].id;
            assertTrue(f.game.configureAutoPlay(player, f.game.decision, AutoPlay.Option.WIN, true));
            assertTrue(f.game.configureAutoPlay(player, f.game.decision, AutoPlay.Option.NO_CALLS, true));
        }
        f.act(0, Action.Type.DISCARD, discarded);
        for (int i = 0; i < Game.AUTO_ACTION_TICKS + 3; i++) { f.game.tick(); f.game.validate(); }
        assertEquals(List.of(1, 2, 3), f.game.view(null).wins().stream().map(TableView.Win::seat).toList());
        assertTrue(f.game.view(null).wins().stream().allMatch(win -> win.score().ron() > 0));
    }

    @Test void automaticTsumoSettlesInsteadOfDiscardingTheWinningTile() {
        Fixture f = new Fixture(RuleSet.TENHOU_4);
        f.hand(0, "123456789m111p55z");
        f.riichi(0);
        int drawn = f.game.players[0].hand.getLast();
        f.start(0, drawn);
        assertTrue(f.game.configureAutoPlay(f.game.players[0].id, f.game.decision, AutoPlay.Option.WIN, true));
        assertTrue(f.game.configureAutoPlay(f.game.players[0].id, f.game.decision, AutoPlay.Option.DISCARD, true));
        for (int i = 0; i < Game.AUTO_ACTION_TICKS; i++) f.game.tick();
        f.game.validate();
        assertEquals(1, f.game.view(null).wins().size());
        assertEquals(0, f.game.view(null).wins().getFirst().seat());
        assertTrue(f.game.players[0].river.isEmpty());
        assertTrue(f.game.players[0].hand.contains(drawn));
    }

    @ParameterizedTest @EnumSource(value = RuleSet.class, names = {"MAHJONG_SOUL_4", "TENHOU_4", "M_LEAGUE", "JPML_A", "WRC"})
    void simultaneousRonUsesRulesetPriorityRatherThanPacketArrival(RuleSet rules) {
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
        f.act(0, Action.Type.DISCARD, discarded);
        long token = f.game.view(f.game.players[1].id).decision();
        f.act(3, Action.Type.RON);
        assertEquals(token, f.game.view(f.game.players[1].id).decision(), "Another response must not stale a simultaneous choice");
        f.act(2, Action.Type.RON);
        f.act(1, Action.Type.RON);
        if (rules == RuleSet.TENHOU_4) {
            assertEquals("triple_ron", f.game.view(null).result());
            assertTrue(f.game.view(null).wins().isEmpty());
            assertArrayEquals(before, Arrays.stream(f.game.players).mapToInt(p -> p.points).toArray());
        } else {
            var winners = f.game.view(null).wins();
            assertEquals(rules.headBump() ? List.of(1) : List.of(1,2,3), winners.stream().map(TableView.Win::seat).toList());
            for (var win : winners) {
                int extra = win.seat() == 1 ? 3600 : 0;
                assertEquals(win.score().ron() + extra, f.game.players[win.seat()].points - before[win.seat()]);
            }
        }
    }

    @Test void ronBeatsEarlierPonAndChiRequests() {
        Fixture f = new Fixture(RuleSet.MAHJONG_SOUL_4);
        f.hand(1, "45p1236789s55z19m");
        f.hand(2, "123456789m55s78p");
        f.hand(3, "66p123456s11z789m");
        f.riichi(2);
        int discarded = f.take("6p").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);
        f.act(0, Action.Type.DISCARD, discarded);
        f.act(3, Action.Type.PON);
        f.act(1, Action.Type.CHI);
        f.act(2, Action.Type.RON);
        assertEquals(List.of(2), f.game.view(null).wins().stream().map(TableView.Win::seat).toList());
        assertTrue(f.game.players[1].melds.isEmpty()); assertTrue(f.game.players[3].melds.isEmpty());
    }

    @Test void botRonSettlesBeforeHumanCallOnlyChoices() {
        Fixture f = new Fixture(RuleSet.MAHJONG_SOUL_4);
        f.hand(1, "45p1236789s55z19m");
        f.hand(2, "123456789m55s78p");
        f.hand(3, "66p123456s11z789m");
        f.riichi(2);
        f.game.players[2].bot = true;
        int discarded = f.take("6p").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);

        f.act(0, Action.Type.DISCARD, discarded);

        assertEquals(List.of(2), f.game.view(null).wins().stream().map(TableView.Win::seat).toList());
        assertTrue(f.game.players[1].melds.isEmpty());
        assertTrue(f.game.players[3].melds.isEmpty());
        assertTrue(f.game.view(f.game.players[1].id).actions().isEmpty());
    }

    @Test void botRonStillWaitsForAnotherPlayersRon() {
        Fixture f = new Fixture(RuleSet.MAHJONG_SOUL_4);
        f.hand(1, "123456789m111p5z");
        f.hand(2, "123456789p111s5z");
        int discarded = f.take("5z").getFirst();
        f.game.players[0].hand.add(discarded);
        f.riichi(1);
        f.riichi(2);
        f.game.players[2].bot = true;
        f.start(0, discarded);

        f.act(0, Action.Type.DISCARD, discarded);
        assertEquals(Game.Phase.REACTION, f.game.phase());
        assertTrue(f.game.view(f.game.players[1].id).actions().stream().anyMatch(a -> a.type() == Action.Type.RON));
        f.act(1, Action.Type.RON);

        assertEquals(List.of(1, 2), f.game.view(null).wins().stream().map(TableView.Win::seat).toList());
    }

    @ParameterizedTest @EnumSource(value = RuleSet.class, names = {"TENHOU_4", "WRC"})
    void aCallClearsTemporaryFuritenOnlyUnderWrcRules(RuleSet rules) {
        Fixture f = new Fixture(rules);
        f.hand(3, "123456m789p55z11s");
        int discarded = f.take("5z").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);
        f.act(0, Action.Type.DISCARD, discarded);
        assertTrue(f.game.view(f.game.players[3].id).actions().stream().anyMatch(a -> a.type() == Action.Type.RON));
        f.act(3, Action.Type.PON);
        f.passOthers();
        assertEquals(Game.Phase.TURN, f.game.phase());
        assertEquals(3, f.game.turn);
        assertEquals(rules != RuleSet.WRC, f.game.players[3].temporaryFuriten);
        f.act(3, Action.Type.DISCARD);
        assertEquals(rules != RuleSet.WRC, f.game.players[3].temporaryFuriten);
    }

    @ParameterizedTest @EnumSource(value = RuleSet.class, names = {"MAHJONG_SOUL_4", "TENHOU_4", "M_LEAGUE", "JPML_A", "WRC"})
    void concealedKanRobberyIsKokushiAndMahjongSoulOnly(RuleSet rules) {
        Fixture f = new Fixture(rules);
        f.hand(1, "11m19p19s1234567z");
        f.hand(0, "9999m");
        int fourth = f.game.players[0].hand.getLast();
        f.start(0, fourth);
        f.act(0, Action.Type.CLOSED_KAN, fourth);
        if (rules.mahjongSoul()) {
            assertEquals(Game.Phase.REACTION, f.game.phase());
            assertTrue(f.game.view(null).focus().declaration());
            assertEquals(fourth, f.game.view(null).focus().tile());
            f.act(1, Action.Type.RON); f.passOthers();
            assertEquals(1, f.game.view(null).wins().size());
            assertTrue(f.game.players[0].melds.isEmpty());
            assertEquals(0, f.game.wall.replacementIndex);
            assertEquals(1, f.game.wall.revealed);
        } else {
            assertEquals(Game.Phase.TURN, f.game.phase());
            assertEquals(1, f.game.players[0].melds.size());
            assertEquals(1, f.game.wall.replacementIndex);
            assertEquals(rules.kanDora() ? 2 : 1, f.game.wall.revealed);
        }
    }

    @Test void wrcYakulessPassStillCausesTemporaryFuriten() {
        Fixture f = new Fixture(RuleSet.WRC);
        f.hand(3, "123m456p789s44z45m");
        int discarded = f.take("6m").getFirst();
        f.game.players[0].hand.add(discarded);
        f.start(0, discarded);
        f.act(0, Action.Type.DISCARD, discarded);
        assertTrue(f.game.players[3].temporaryFuriten);
        assertTrue(f.game.view(f.game.players[3].id).actions().stream().noneMatch(a -> a.type() == Action.Type.RON));
    }

    @ParameterizedTest @EnumSource(value = RuleSet.class, names = {"MAHJONG_SOUL_3", "TENHOU_3"})
    void automaticNorthUsesTheLegalDeclarationAndReplacementDraw(RuleSet rules) {
        Fixture f = new Fixture(rules);
        f.hand(0, "147p258s19m11235z");
        int north = f.take("4z").getFirst();
        f.game.players[0].hand.add(north);
        f.start(0, north);
        assertTrue(f.game.configureAutoPlay(f.game.players[0].id, f.game.decision, AutoPlay.Option.KITA, true));
        for (int tick = 0; tick < Game.AUTO_ACTION_TICKS; tick++) f.game.tick();
        f.passOthers();
        assertEquals(List.of(north), f.game.players[0].norths);
        assertEquals(1, f.game.wall.replacementIndex);
        assertEquals(Game.Phase.TURN, f.game.phase());
        assertNotEquals(north, f.game.players[0].drawn);
        assertTrue(f.game.players[0].river.isEmpty());
        f.game.validate();
    }

    @ParameterizedTest @EnumSource(value = RuleSet.class, names = {"MAHJONG_SOUL_3", "TENHOU_3"})
    void automaticNorthCanBeRobbedByOrdinaryYakuButDoesNotGiveChankan(RuleSet rules) {
        Fixture f = new Fixture(rules);
        f.hand(1, "123456789p111s4z");
        int north = f.take("4z").getFirst();
        f.game.players[0].hand.add(north);
        f.start(0, north);
        assertTrue(f.game.configureAutoPlay(f.game.players[0].id, f.game.decision, AutoPlay.Option.KITA, true));
        for (int tick = 0; tick < Game.AUTO_ACTION_TICKS; tick++) f.game.tick();
        f.game.validate();
        f.act(1, Action.Type.RON); f.passOthers();
        assertTrue(f.game.players[0].norths.isEmpty());
        assertEquals(0, f.game.wall.replacementIndex);
        assertFalse(f.game.view(null).wins().getFirst().score().yaku().contains("Chankan"));
        assertEquals(0, f.game.view(null).wins().getFirst().score().yakuman());
    }
}

package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TenhouFormatTest {
    @Test void allFacesAndPhysicalRedFivesUseTenhouTileCodes() {
        for (int kind = 0; kind < 34; kind++) {
            int ordinary = (kind / 9 + 1) * 10 + kind % 9 + 1;
            for (int copy = 0; copy < 4; copy++) {
                int id = Tile.id(kind, copy, copy == 0 && (kind == 4 || kind == 13 || kind == 22));
                assertEquals(Tile.red(id) ? 51 + kind / 9 : ordinary, TenhouReplay.tile(id));
            }
        }
        assertEquals(47, TenhouReplay.tile(135));
        assertThrows(IllegalArgumentException.class, () -> TenhouReplay.tile(-1));
        assertThrows(IllegalArgumentException.class, () -> TenhouReplay.tile(136));
        var match = fixture(RuleSet.TENHOU_4, 0, 0, List.of(), "exhaustive");
        for (var reds : RedFives.values()) for (int length : List.of(1, 2)) {
            var configured = new ReplayMatch(match.id(), match.tableId(), match.startedAt(), match.updatedAt(),
                match.rules().with(RuleOption.RED_FIVES, reds.ordinal()).with(RuleOption.KUITAN, 0)
                    .with(RuleOption.MATCH_LENGTH, length).with(RuleOption.MIN_HAN, 4),
                match.initialDealer(), match.participants(), match.hands(), false, reds);
            var rule = (java.util.Map<?, ?>) TenhouReplay.export(configured).get("rule");
            for (int suit = 0; suit < 3; suit++) assertEquals(reds.count(suit), rule.get("aka5" + (suit + 1)));
            assertFalse(rule.get("disp").toString().contains("喰"));
            assertTrue(rule.get("disp").toString().startsWith(length == 1 ? "東" : "南"));
            assertTrue(rule.get("disp").toString().contains("4飜縛り"));
        }
    }

    @Test void calledTileAndMarkerPositionsEncodeEachDirectionIncludingRedFives() {
        int red = Tile.id(4, 0, true);
        var pon = List.of(red, 17, 18);
        assertEquals("p511515", TenhouReplay.call(Meld.Type.PON, 0, 3, pon, red));
        assertEquals("15p5115", TenhouReplay.call(Meld.Type.PON, 0, 2, pon, red));
        assertEquals("1515p51", TenhouReplay.call(Meld.Type.PON, 0, 1, pon, red));
        assertEquals("m51151515", TenhouReplay.call(Meld.Type.OPEN_KAN, 0, 3, List.of(red,17,18,19), red));
        assertEquals("15m511515", TenhouReplay.call(Meld.Type.OPEN_KAN, 0, 2, List.of(red,17,18,19), red));
        assertEquals("151515m51", TenhouReplay.call(Meld.Type.OPEN_KAN, 0, 1, List.of(red,17,18,19), red));
        assertEquals("c151314", TenhouReplay.call(Meld.Type.CHI, 0, 3, List.of(8,12,17), 17));
        assertEquals("c145116", TenhouReplay.call(Meld.Type.CHI, 0, 3, List.of(12,red,20), 12));
        assertThrows(IllegalArgumentException.class, () -> TenhouReplay.call(Meld.Type.PON, 0, 0, pon, red));
    }

    @Test void sanmaPadsTheFourthSeatRotatesStartingDealerAndSkipsTheFourthRoundId() {
        ReplayMatch match = fixture(RuleSet.TENHOU_3, 2, 3, List.of(
            new ReplayHand.Event(ReplayHand.Kind.DRAW, 2, Tile.id(13, 0, true), null, false, false, true),
            new ReplayHand.Event(ReplayHand.Kind.DISCARD, 2, Tile.id(13, 0, true), null, true, true, true)), "exhaustive");
        var root = TenhouReplay.export(match);
        assertEquals("2.3", root.get("ver"));
        assertEquals("PF3", root.get("ratingc"));
        assertEquals(List.of("Player 2", "Player 0", "Player 1", ""), root.get("name"));
        assertFalse(root.containsKey("sc"), "An ongoing match has no fabricated final ranking");
        List<?> row = (List<?>) ((List<?>) root.get("log")).getFirst();
        assertEquals(17, row.size());
        assertEquals(List.of(4, 0, 0), row.getFirst());
        assertEquals(List.of(35002, 35000, 35001, 0), row.get(1));
        assertEquals(List.of(52), row.get(5));
        assertEquals(List.of("r60"), row.get(6));
        assertEquals(List.of(), row.get(13));
        assertEquals(List.of(), row.get(14));
        assertEquals(List.of(), row.get(15));
        assertEquals(List.of("流局", List.of(0,0,0,0)), row.getLast());
    }

    @Test void declarationsUseDrawAndDiscardStreamsWithoutLosingRobbedDeclarations() {
        List<ReplayHand.Event> events = List.of(
            new ReplayHand.Event(ReplayHand.Kind.MELD, 0, Tile.id(4, 0, true), new Meld(Meld.Type.OPEN_KAN,
                List.of(Tile.id(4, 0, true),17,18,19), 3, Tile.id(4, 0, true)), false,false,true),
            new ReplayHand.Event(ReplayHand.Kind.MELD, 0, -2, new Meld(Meld.Type.CLOSED_KAN,
                List.of(Tile.id(13, 0, true),53,54,55), 0, -2), false,false,true),
            new ReplayHand.Event(ReplayHand.Kind.MELD, 0, Tile.id(22, 0, true), new Meld(Meld.Type.ADDED_KAN,
                List.of(89,90,91,Tile.id(22, 0, true)), 3, 89), false,false,false),
            new ReplayHand.Event(ReplayHand.Kind.NUKI, 0, 120, null, false,false,false));
        var root = TenhouReplay.export(fixture(RuleSet.TENHOU_4, 0, 0, events, "four_kans"));
        List<?> row = (List<?>) ((List<?>) root.get("log")).getFirst();
        assertEquals(List.of("m51151515"), row.get(5));
        assertEquals(List.of(0, "252525a52", "k53353535", "f44"), row.get(6));
        assertEquals("四開槓", ((List<?>) row.getLast()).getFirst());
    }

    static ReplayMatch fixture(RuleSet rules, int dealer, int round, List<ReplayHand.Event> events, String result) {
        int count = rules.players();
        var participants = new ArrayList<ReplayMatch.Participant>();
        var hands = new ArrayList<List<Integer>>();
        var seats = new ArrayList<TableView.Seat>();
        var points = new ArrayList<Integer>();
        for (int seat = 0; seat < count; seat++) {
            var tiles = Tile.set(rules.sanma(), rules.defaultRedFives()).subList(seat * 13, seat * 13 + 13);
            participants.add(new ReplayMatch.Participant(new UUID(1, seat + 1), "Player " + seat, false));
            hands.add(tiles); points.add(rules.startingPoints() + seat);
            seats.add(new TableView.Seat(false, "Player " + seat, true,false,false,points.get(seat),tiles,-2,List.of(),List.of(),List.of(),false,false,false));
        }
        ReplayHand hand = new ReplayHand(1,round,dealer,0,0,points,hands,List.of(132),wall(rules),events,List.of(),seats,
            List.of(),result,Collections.nCopies(count,0),List.of(132),List.of(),List.of(),List.of());
        return new ReplayMatch(UUID.randomUUID(), UUID.randomUUID(), 1,2,rules.config(),dealer,participants,List.of(hand),false,rules.defaultRedFives());
    }

    private static ReplayWall wall(RuleSet rules) {
        var tiles = Tile.set(rules.sanma(), rules.defaultRedFives());
        int end = tiles.size();
        var replacements = new ArrayList<Integer>();
        for (int i = 0; i < rules.replacementCapacity(); i++) replacements.add(end - 1 - i % 4);
        return new ReplayWall(tiles, 0, replacements,
            List.of(end - 5, end - 7, end - 9, end - 11, end - 13),
            List.of(end - 6, end - 8, end - 10, end - 12, end - 14));
    }
}

package top.skyeyefast.mchjong.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static top.skyeyefast.mchjong.engine.ReplayHand.Kind.*;

/** Private recorder, persisted alongside the live game and sealed only after settlement. */
final class ReplayRecorder {
    int number, round, dealer, honba, sticks;
    List<Integer> initialPoints;
    List<List<Integer>> initialHands;
    List<Integer> initialDora;
    List<ReplayHand.Event> events = new ArrayList<>();
    List<ReplayHand.Win> wins = new ArrayList<>();
    int pendingDeclaration = -1;
    int indicators = 1;

    ReplayRecorder(Game game) {
        number = game.handNumber; round = game.round; dealer = game.dealer; honba = game.honba; sticks = game.riichiSticks;
        initialPoints = points(game);
        initialHands = Arrays.stream(game.players).limit(game.rules.players()).map(player -> List.copyOf(player.hand)).toList();
        initialDora = List.copyOf(game.wall.indicators(false));
    }

    static List<Integer> points(Game game) {
        return Arrays.stream(game.players).limit(game.rules.players()).map(player -> player.points).toList();
    }
    void draw(int seat, int tile) { events.add(new ReplayHand.Event(DRAW, seat, tile, null, false, false, true)); }
    void discard(int seat, int tile, boolean drawn, boolean riichi) {
        events.add(new ReplayHand.Event(DISCARD, seat, tile, null, drawn, riichi, true));
    }
    void riichi(int seat) { events.add(new ReplayHand.Event(RIICHI, seat, Tile.ABSENT, null, false, false, true)); }
    void call(int seat, Meld meld) { events.add(new ReplayHand.Event(MELD, seat, meld.calledTile(), meld, false, false, true)); }

    void declare(Game game, int seat, Action action) {
        int tile = game.lastTile;
        Meld meld = null;
        if (action.type() == Action.Type.CLOSED_KAN)
            meld = new Meld(Meld.Type.CLOSED_KAN, action.tiles(), seat, Tile.ABSENT);
        else if (action.type() == Action.Type.ADDED_KAN) {
            Meld pon = game.players[seat].melds.stream().filter(old -> old.type() == Meld.Type.PON && old.kind() == Tile.kind(tile))
                .findFirst().orElseThrow();
            var tiles = new ArrayList<>(pon.tiles());
            tiles.add(tile);
            meld = new Meld(Meld.Type.ADDED_KAN, tiles, pon.fromSeat(), pon.calledTile());
        }
        pendingDeclaration = events.size();
        events.add(new ReplayHand.Event(action.type() == Action.Type.NUKI ? NUKI : MELD, seat, tile, meld, false, false, false));
    }
    void confirmDeclaration() {
        if (pendingDeclaration >= 0) events.set(pendingDeclaration, events.get(pendingDeclaration).commit());
        pendingDeclaration = -1;
    }
    void dora(Game game) {
        var revealed = game.wall.indicators(false);
        while (indicators < revealed.size()) events.add(new ReplayHand.Event(DORA, -1, revealed.get(indicators++), null, false, false, true));
    }

    void win(Game game, int seat, int from, int tile, HandScore score, List<Integer> before, int pao, int bonus) {
        var changes = new ArrayList<Integer>();
        for (int i = 0; i < game.rules.players(); i++) changes.add(game.players[i].points - before.get(i));
        var player = game.players[seat];
        var all = new ArrayList<>(player.hand);
        if (tile >= 0 && all.size() + player.melds.size() * 3 == 13) all.add(tile);
        player.melds.forEach(meld -> all.addAll(meld.tiles()));
        all.addAll(player.norths);
        boolean bonuses = score.yakuman() == 0 && !score.yaku().contains("Renhou");
        int red = bonuses ? (int) all.stream().filter(Tile::red).count() : 0;
        int north = bonuses ? player.norths.size() : 0;
        int ura = 0;
        if (bonuses && game.rules.uraDora() && player.riichi) for (int i = 0; i < game.wall.revealed; i++) {
            int indicator = game.wall.tiles.get(game.wall.ura.get(i));
            int kind = Tile.doraAfter(Tile.kind(indicator), game.rules.sanma());
            ura += (int) all.stream().filter(id -> Tile.kind(id) == kind).count();
        }
        wins.add(new ReplayHand.Win(seat, from, tile, score, changes, pao, bonus, 0,
            score.yaku().contains("Nagashi") ? List.of() : HandAnalyzer.yakuValues(score.yaku(), player.closed(), game.rules),
            Math.max(0, score.dora() - red - ura - north), ura, red, north));
    }

    void awardDeposit(int amount) {
        if (!wins.isEmpty()) wins.set(0, wins.getFirst().withDeposit(amount));
    }

    ReplayHand finish(Game game) {
        var publicSeats = game.view(null).seats();
        var allSeats = new ArrayList<TableView.Seat>();
        for (int i = 0; i < game.rules.players(); i++) {
            TableView.Seat visible = publicSeats.get(i);
            var player = game.players[i];
            var hand = new ArrayList<>(player.hand);
            hand.sort(Tile.ORDER);
            if (player.drawn >= 0 && hand.remove(Integer.valueOf(player.drawn))) hand.add(player.drawn);
            allSeats.add(new TableView.Seat(visible.name(), visible.occupied(), visible.bot(), visible.ready(), visible.points(),
                hand, player.drawn, visible.melds(), visible.river(), visible.norths(), visible.riichi(), visible.exposed()));
        }
        var ura = new ArrayList<Integer>();
        if (game.rules.uraDora() && game.wins.stream().anyMatch(win -> game.players[win.seat()].riichi))
            for (int i = 0; i < game.wall.revealed; i++) ura.add(game.wall.tiles.get(game.wall.ura.get(i)));
        return new ReplayHand(number, round, dealer, honba, sticks, initialPoints, initialHands, initialDora, events,
            allSeats, wins, game.result, game.deltas.subList(0, game.rules.players()), game.wall.indicators(false), ura,
            game.finalScores, game.finalRanks);
    }
}

package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

/** A finished hand, with physical tile identities and the original, authoritative payments. */
public record ReplayHand(int number, int round, int dealer, int honba, int sticks,
                         List<Integer> initialPoints, List<List<Integer>> initialHands,
                         List<Integer> initialDora, List<Event> events, List<TableView.Seat> finalSeats,
                         List<Win> wins, String result, List<Integer> deltas, List<Integer> dora,
                         List<Integer> ura, List<Double> finalScores, List<Integer> finalRanks) {
    public enum Kind { DRAW, DISCARD, MELD, NUKI, RIICHI, DORA }

    /** Uncommitted kan/north declarations are retained when they are robbed. */
    public record Event(Kind kind, int seat, int tile, Meld meld, boolean tsumogiri, boolean riichi, boolean committed) {
        public Event {
            Objects.requireNonNull(kind);
            if (seat < (kind == Kind.DORA ? -1 : 0) || seat > 3) throw new IllegalArgumentException("Invalid replay seat");
            if (kind != Kind.MELD && kind != Kind.RIICHI) Tile.kind(tile);
            if (kind == Kind.MELD) Objects.requireNonNull(meld);
        }
        Event commit() { return new Event(kind, seat, tile, meld, tsumogiri, riichi, true); }
    }

    public record Yaku(String name, int han, boolean yakuman) {
        public Yaku {
            Objects.requireNonNull(name);
            if (name.length() > 128 || han < 0 || han > 26) throw new IllegalArgumentException("Invalid recorded yaku");
        }
    }

    public record Win(int seat, int from, int tile, HandScore score, List<Integer> deltas,
                      int pao, int honba, int sticks, List<Yaku> yaku, int dora, int ura, int redDora, int nukiDora) {
        public Win {
            Objects.requireNonNull(score);
            deltas = List.copyOf(deltas); yaku = List.copyOf(yaku);
        }
        Win withDeposit(int amount) {
            var updated = new java.util.ArrayList<>(deltas);
            updated.set(seat, updated.get(seat) + amount * 1000);
            return new Win(seat, from, tile, score, updated, pao, honba, amount, yaku, dora, ura, redDora, nukiDora);
        }
    }

    public ReplayHand {
        Objects.requireNonNull(result);
        initialPoints = List.copyOf(initialPoints);
        initialHands = initialHands.stream().map(List::copyOf).toList();
        initialDora = List.copyOf(initialDora); events = List.copyOf(events);
        finalSeats = List.copyOf(finalSeats); wins = List.copyOf(wins); deltas = List.copyOf(deltas);
        dora = List.copyOf(dora); ura = List.copyOf(ura);
        finalScores = List.copyOf(finalScores); finalRanks = List.copyOf(finalRanks);
        int players = initialHands.size();
        if (players < 3 || players > 4 || initialPoints.size() != players || finalSeats.size() != players
            || dealer < 0 || dealer >= players || round < 0 || honba < 0 || sticks < 0
            || events.size() > 1024 || dora.size() > 5 || ura.size() > 5 || initialDora.size() != 1)
            throw new IllegalArgumentException("Invalid replay hand");
        for (var hand : initialHands) {
            if (hand.size() != 13) throw new IllegalArgumentException("Initial hands need thirteen tiles");
            hand.forEach(Tile::kind);
        }
        if (number < 1 || round >= players * 4 || deltas.size() != players || wins.size() > players
            || !finalScores.isEmpty() && finalScores.size() != players || !finalRanks.isEmpty() && finalRanks.size() != players)
            throw new IllegalArgumentException("Invalid replay result");
        initialDora.forEach(Tile::kind); dora.forEach(Tile::kind); ura.forEach(Tile::kind);
        if (finalScores.stream().anyMatch(value -> !Double.isFinite(value))
            || finalRanks.stream().anyMatch(rank -> rank < 1 || rank > players))
            throw new IllegalArgumentException("Invalid final ranking");
        for (var event : events) {
            if (event.seat() >= players) throw new IllegalArgumentException("Inactive replay seat");
            if (event.kind() == Kind.MELD) validateMeld(event.meld(), players);
        }
        for (var seat : finalSeats) {
            Objects.requireNonNull(seat.name());
            if (seat.name().length() > 128 || seat.hand().size() > 14 || seat.melds().size() > 4
                || seat.river().size() > 100 || seat.norths().size() > 4 || seat.drawn() < Tile.ABSENT)
                throw new IllegalArgumentException("Invalid replay seat contents");
            seat.hand().forEach(Tile::kind); seat.norths().forEach(Tile::kind);
            seat.river().forEach(discard -> Tile.kind(discard.tile()));
            seat.melds().forEach(meld -> validateMeld(meld, players));
            if (seat.drawn() != Tile.ABSENT) Tile.kind(seat.drawn());
        }
        for (var win : wins) {
            if (win.seat() < 0 || win.seat() >= players || win.from() < -1 || win.from() >= players
                || win.pao() < -1 || win.pao() >= players || win.deltas().size() != players
                || win.yaku().size() > 64 || win.score().yaku().size() > 64)
                throw new IllegalArgumentException("Invalid replay win");
            Tile.kind(win.tile());
            if (win.score().yaku().stream().anyMatch(name -> name == null || name.length() > 128))
                throw new IllegalArgumentException("Invalid replay yaku name");
        }
    }

    private static void validateMeld(Meld meld, int players) {
        Objects.requireNonNull(meld.type());
        if (meld.fromSeat() < 0 || meld.fromSeat() >= players || meld.tiles().size() != (meld.kan() ? 4 : 3))
            throw new IllegalArgumentException("Invalid replay meld");
        meld.tiles().forEach(Tile::kind);
        if (!meld.closed() && !meld.tiles().contains(meld.calledTile())) throw new IllegalArgumentException("Invalid called tile");
    }
}

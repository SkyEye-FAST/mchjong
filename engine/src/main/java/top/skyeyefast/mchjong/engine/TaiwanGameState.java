package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

/** Private current-format save. Never a recipient projection. */
public record TaiwanGameState(int format, Rules rules, Opening opening, int roundWind, int continuation,
                              long revision, long decision, TaiwanGame.Phase phase, int turn, Wall wall,
                              List<Player> players, int drawn, int turnDrawn, TaiwanWinContext.DrawOrigin origin,
                              Set<Integer> turnWaits, int calls, int draws, Offer offer, List<Reply> replies,
                              Outcome outcome, Result settlement) {
    public static final int FORMAT = 1;
    public TaiwanGameState {
        if (format != FORMAT || roundWind < Tile.EAST || roundWind > Tile.NORTH || continuation < 0 || continuation > 1_000_000
            || revision < 1 || revision >= Long.MAX_VALUE - 1 || decision < 1 || decision >= Long.MAX_VALUE - 1
            || turn < 0 || turn > 3 || calls < 0 || calls > 40 || draws < 0 || draws > 144)
            throw new IllegalArgumentException("Invalid Taiwan save position");
        Objects.requireNonNull(rules); Objects.requireNonNull(opening); Objects.requireNonNull(phase);
        Objects.requireNonNull(wall); Objects.requireNonNull(origin);
        players = List.copyOf(players); replies = List.copyOf(replies); turnWaits = Set.copyOf(turnWaits);
        if (players.size() != 4 || replies.size() > 2 || turnWaits.stream().anyMatch(k -> k < 0 || k >= 34))
            throw new IllegalArgumentException("Invalid Taiwan save collections");
    }
    public record Rules(String name, Map<TaiwanRules.Pattern, Integer> values,
                        Map<TaiwanRules.Pattern, Set<TaiwanRules.Pattern>> exclusions,
                        Map<TaiwanRules.Pattern, TaiwanRules.Source> sources, TaiwanRules.Flowers flowers,
                        TaiwanRules.FlowerSets flowerSets, TaiwanRules.Pinfu pinfu, TaiwanRules.Replacements replacements,
                        Integer taiLimit, TaiwanRules.Reserve reserve, TaiwanRules.Payment payment) {
        public Rules {
            values = Map.copyOf(values); sources = Map.copyOf(sources);
            exclusions = exclusions.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, e -> Set.copyOf(e.getValue())));
            Objects.requireNonNull(flowers); Objects.requireNonNull(flowerSets); Objects.requireNonNull(pinfu);
            Objects.requireNonNull(replacements); Objects.requireNonNull(reserve); Objects.requireNonNull(payment);
            new TaiwanRules(name,values,exclusions,sources,flowers,flowerSets,pinfu,replacements,taiLimit,reserve,payment);
        }
        public TaiwanRules restore() { return new TaiwanRules(name,values,exclusions,sources,flowers,flowerSets,pinfu,replacements,taiLimit,reserve,payment); }
        public static Rules of(TaiwanRules r) { return new Rules(r.getName(),r.getValues(),r.getExclusions(),r.getSources(),r.getFlowers(),r.getFlowerSets(),r.getPinfu(),r.getReplacements(),r.getTaiLimit(),r.getReserve(),r.getPayment()); }
    }
    public record Opening(int dealer, List<Integer> dice) {
        public Opening { dice = List.copyOf(dice); new TaiwanOpening(dealer,dice); }
        public TaiwanOpening restore() { return new TaiwanOpening(dealer,dice); }
    }
    public record Wall(List<Integer> slots, int front, int tail, int kongs) {
        public Wall { slots = List.copyOf(slots); }
    }
    public record Player(List<Integer> hand, List<Meld> melds, List<FlowerTile> flowers, List<Integer> river,
                         TaiwanWinContext.Ready ready, boolean passed, int discards) {
        public Player {
            hand = List.copyOf(hand); melds = List.copyOf(melds); flowers = List.copyOf(flowers); river = List.copyOf(river);
            Objects.requireNonNull(ready);
            if (hand.size() > 17 || melds.size() > 5 || flowers.size() > 8 || river.size() > 144 || discards < river.size() || discards > 144)
                throw new IllegalArgumentException("Invalid Taiwan player zones");
        }
    }
    public record Action(TaiwanAction.Type type, List<Integer> tiles) {
        public Action {
            Objects.requireNonNull(type); tiles = List.copyOf(tiles);
            int count = switch (type) { case PASS, WIN -> 0; case DISCARD, READY_DISCARD, ADDED_KONG -> 1; case CHOW, PONG -> 2; case OPEN_KONG -> 3; case CONCEALED_KONG -> 4; };
            if (tiles.size() != count || tiles.stream().distinct().count() != count || tiles.stream().anyMatch(t -> t < 0 || t >= 136))
                throw new IllegalArgumentException("Invalid Taiwan action");
        }
        public static Action of(TaiwanAction a) { return new Action(a.getType(),a.getTiles()); }
        public TaiwanAction restore() { return new TaiwanAction(type,tiles); }
    }
    public record Reply(int seat, Action action) {
        public Reply { if (seat < 0 || seat > 3) throw new IllegalArgumentException("Invalid responder"); Objects.requireNonNull(action); }
    }
    public record Offer(int seat, int tile, Integer addedMeld) {
        public Offer { if (seat < 0 || seat > 3 || tile < 0 || tile >= 136 || addedMeld != null && (addedMeld < 0 || addedMeld >= 5)) throw new IllegalArgumentException("Invalid offer"); }
    }
    /** Terminal provenance used to rederive the scored result. */
    public record Outcome(Integer winner, Integer supplier, int tile, TaiwanWinContext.Method method,
                          TaiwanHandAnalyzer.FlowerEvent flowerEvent, Integer flowerPayer, Offer winningOffer) {
        public Outcome {
            if (winner != null && (winner < 0 || winner > 3) || supplier != null && (supplier < 0 || supplier > 3 || supplier.equals(winner))
                || flowerPayer != null && (flowerPayer < 0 || flowerPayer > 3 || flowerPayer.equals(winner))) throw new IllegalArgumentException("Invalid outcome");
            Objects.requireNonNull(method);
        }
    }
    public record Score(int pair, List<TaiwanHandAnalyzer.Group> groups, int winningGroup,
                        List<TaiwanHandAnalyzer.Award> awards, int rawTai, int tai) {
        public Score {
            groups = List.copyOf(groups); awards = List.copyOf(awards);
            if (pair < 0 || pair > 33 || groups.size() > 5 || winningGroup < -1 || winningGroup >= groups.size()
                || rawTai < 0 || rawTai > 1_000_000 || tai < 0 || tai > rawTai || awards.size() > 64
                || groups.stream().anyMatch(g -> g.type() == null || g.kind() < 0 || g.kind() > 33
                    || g.type() == TaiwanHandAnalyzer.GroupType.SEQUENCE && (g.kind() >= 27 || g.kind() % 9 > 6)))
                throw new IllegalArgumentException("Invalid Taiwan score");
        }
        public static Score of(TaiwanHandAnalyzer.Score s) { return s == null ? null : new Score(s.getShape().getPair(),s.getShape().getGroups(),s.getWinningGroup(),s.getAwards(),s.getRawTai(),s.getTai()); }
    }
    public record Flower(TaiwanHandAnalyzer.Award award, Score handScore, int rawTai, int tai) {
        public Flower { Objects.requireNonNull(award); }
        public static Flower of(TaiwanHandAnalyzer.FlowerScore s) { return s == null ? null : new Flower(s.getAward(),Score.of(s.getHandScore()),s.getRawTai(),s.getTai()); }
    }
    public record Result(Integer winner, Integer supplier, Score score, Flower flowerScore,
                         List<TaiwanSettlement.Transfer> transfers, int nextDealer, int nextContinuation, List<Long> deltas) {
        public Result { transfers = List.copyOf(transfers); deltas = List.copyOf(deltas); }
        public static Result of(TaiwanSettlement r) { return r == null ? null : new Result(r.getWinner(),r.getSupplier(),Score.of(r.getScore()),Flower.of(r.getFlowerScore()),r.getTransfers(),r.getNextDealer(),r.getNextContinuation(),r.getDeltas()); }
    }
}

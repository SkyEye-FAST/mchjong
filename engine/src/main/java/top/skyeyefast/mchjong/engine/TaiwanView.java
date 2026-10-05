package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

/** Recipient data only. Opponent concealed identities have no place in this contract. */
public record TaiwanView(long revision, long decision, TaiwanGameState.Rules rules, TaiwanGameState.Opening opening,
                         int roundWind, int continuation, TaiwanGame.Phase phase, int turn, int recipient,
                         int drawable, int reserve, List<Integer> wall, List<Seat> seats, Focus focus,
                         List<TaiwanGameState.Action> actions, boolean responded, Boolean passedWin, Result result) {
    public TaiwanView {
        Objects.requireNonNull(rules); Objects.requireNonNull(opening); Objects.requireNonNull(phase);
        wall = List.copyOf(wall); seats = List.copyOf(seats); actions = List.copyOf(actions);
        int size = rules.flowers() == TaiwanRules.Flowers.NONE ? 136 : 144;
        if (revision < 1 || decision < 1 || recipient < -1 || recipient > 3 || turn < 0 || turn > 3
            || roundWind < Tile.EAST || roundWind > Tile.NORTH || continuation < 0 || continuation > 1_000_000
            || drawable < 0 || reserve < 16 || reserve > 36 || seats.size() != 4 || wall.size() != size || actions.size() > 64
            || wall.stream().anyMatch(t -> t != Tile.HIDDEN && t != Tile.ABSENT)
            || wall.stream().filter(t -> t == Tile.HIDDEN).count() != drawable + reserve
            || (phase == TaiwanGame.Phase.REACTION) != (focus != null)
            || focus != null && focus.seat() != turn
            || (phase == TaiwanGame.Phase.FINISHED) != (result != null)
            || phase == TaiwanGame.Phase.FINISHED && (!actions.isEmpty() || responded)
            || recipient < 0 && (!actions.isEmpty() || responded || passedWin != null)
            || recipient >= 0 && passedWin == null || responded && !actions.isEmpty()
            || responded && (phase != TaiwanGame.Phase.REACTION || recipient == turn)
            || !actions.isEmpty() && (phase == TaiwanGame.Phase.TURN ? recipient != turn : recipient == turn))
            throw new IllegalArgumentException("Invalid Taiwan recipient view");
        for (int s = 0; s < 4; s++) {
            var p = seats.get(s);
            if (s == recipient ? p.concealed().size() != p.concealedCount() : !p.concealed().isEmpty() || p.drawn() != Tile.ABSENT)
                throw new IllegalArgumentException("Opponent hand escaped recipient filtering");
            for (var meld : p.melds()) {
                boolean hidden = meld.closed() && s != recipient;
                if (meld.tiles().stream().anyMatch(t -> hidden ? t != Tile.HIDDEN : t < 0 || t >= 136))
                    throw new IllegalArgumentException("Invalid concealed kong visibility");
                if (meld.closed() && meld.fromSeat() != s || !meld.closed() && (meld.fromSeat() == s
                    || meld.type() == Meld.Type.SEQUENCE && meld.fromSeat() != (s+3)%4))
                    throw new IllegalArgumentException("Invalid meld provenance");
            }
        }
        if (recipient >= 0) for (var action : actions) if (!seats.get(recipient).concealed().containsAll(action.tiles()))
            throw new IllegalArgumentException("Action tiles do not belong to recipient");
        if (result != null) {
            var awards = new java.util.ArrayList<>(result.awards());
            if (result.flowerAward() != null) awards.add(result.flowerAward());
            for (var award : awards) if (award.pattern() == null || award.units() < 1 || award.units() > 34
                || award.tai() != rules.values().get(award.pattern()) * award.units() || !award.source().equals(rules.sources().get(award.pattern())))
                throw new IllegalArgumentException("Invalid public Taiwan award");
            var totals = new long[4];
            for (var transfer : result.transfers()) {
                if (!Objects.equals(result.winner(),transfer.to()) || transfer.base() != rules.payment().base()
                    || transfer.amount() != Math.addExact(transfer.base(),Math.multiplyExact(rules.payment().perTai(),(long) transfer.handTai()+transfer.dealerTai())))
                    throw new IllegalArgumentException("Invalid public Taiwan transfer");
                totals[transfer.from()] = Math.subtractExact(totals[transfer.from()],transfer.amount());
                totals[transfer.to()] = Math.addExact(totals[transfer.to()],transfer.amount());
            }
            for (int s = 0; s < 4; s++) if (totals[s] != result.deltas().get(s)) throw new IllegalArgumentException("Inconsistent public deltas");
            boolean retain = result.winner() == null || result.winner() == opening.dealer();
            if (result.nextDealer() != (retain ? opening.dealer() : (opening.dealer()+1)%4) || result.nextContinuation() != (retain ? continuation+1 : 0))
                throw new IllegalArgumentException("Invalid public dealer succession");
        }
    }
    public record Seat(int concealedCount, List<Integer> concealed, int drawn, List<Meld> melds,
                       List<FlowerTile> flowers, List<Integer> river, TaiwanWinContext.Ready ready) {
        public Seat {
            concealed = List.copyOf(concealed); melds = List.copyOf(melds); flowers = List.copyOf(flowers); river = List.copyOf(river);
            Objects.requireNonNull(ready);
            if (concealedCount < 1 || concealedCount > 17 || melds.size() > 5 || flowers.size() > 8 || river.size() > 144
                || concealed.stream().anyMatch(t -> t < 0 || t >= 136) || river.stream().anyMatch(t -> t < 0 || t >= 136)
                || drawn != Tile.ABSENT && !concealed.contains(drawn)) throw new IllegalArgumentException("Invalid Taiwan seat view");
            for (var meld : melds) if (meld.tiles().size() != (meld.closed() || meld.type() == Meld.Type.OPEN_QUAD || meld.type() == Meld.Type.ADDED_QUAD ? 4 : 3)
                || meld.fromSeat() < 0 || meld.fromSeat() > 3 || (meld.closed() ? meld.calledTile() != Tile.ABSENT : !meld.tiles().contains(meld.calledTile())))
                throw new IllegalArgumentException("Invalid Taiwan meld view");
        }
    }
    public record Focus(int seat, int tile, boolean addedKong) {
        public Focus { if (seat < 0 || seat > 3 || tile < 0 || tile >= 136) throw new IllegalArgumentException("Invalid Taiwan focus"); }
    }
    /** Public awards/payments omit decomposition, winning placement and every private hand. */
    public record Result(Integer winner, Integer supplier, List<TaiwanHandAnalyzer.Award> awards,
                         TaiwanHandAnalyzer.Award flowerAward, int rawTai, int tai,
                         List<TaiwanSettlement.Transfer> transfers, List<Long> deltas, int nextDealer, int nextContinuation) {
        public Result {
            awards = List.copyOf(awards); transfers = List.copyOf(transfers); deltas = List.copyOf(deltas);
            if (winner != null && (winner < 0 || winner > 3) || supplier != null && (supplier < 0 || supplier > 3 || supplier.equals(winner))
                || deltas.size() != 4 || deltas.stream().mapToLong(Long::longValue).sum() != 0 || transfers.size() > 3
                || rawTai < 0 || tai < 0 || tai > rawTai || nextDealer < 0 || nextDealer > 3 || nextContinuation < 0 || nextContinuation > 1_000_001)
                throw new IllegalArgumentException("Invalid Taiwan result view");
        }
        public static Result of(TaiwanSettlement r) {
            if (r == null) return null;
            var score = r.getScore(); var flower = r.getFlowerScore();
            return new Result(r.getWinner(),r.getSupplier(),score == null ? List.of() : score.getAwards(),flower == null ? null : flower.getAward(),
                flower != null ? flower.getRawTai() : score == null ? 0 : score.getRawTai(),
                flower != null ? flower.getTai() : score == null ? 0 : score.getTai(),r.getTransfers(),r.getDeltas(),r.getNextDealer(),r.getNextContinuation());
        }
    }
}

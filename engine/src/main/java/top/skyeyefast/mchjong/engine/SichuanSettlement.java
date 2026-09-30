package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;

public final class SichuanSettlement {
    private SichuanSettlement() {}
    public enum Type { DISCARD_WIN, SELF_DRAW_WIN, DISCARD_KONG, CONCEALED_KONG, ADDED_KONG,
                       KONG_REFUND, KONG_TRANSFER, KONG_TRANSFER_TOP_UP, FLOWER_PIG, READY_PAYMENT }
    public enum Fan { ROOT, ALL_PUNGS, GOLDEN_SINGLE_WAIT, FULL_FLUSH, SEVEN_PAIRS,
                      WIN_AFTER_KONG, SHOOT_AFTER_KONG, ROBBING_KONG, UNDER_THE_SEA }
    public enum DrawStatus { WON, READY, NOT_READY, PASSIVE_FLOWER_PIG, ACTIVE_FLOWER_PIG }

    public record Entry(int id, Type type, int payer, int recipient, int amount, int relatedEntry) {
        public Entry {
            Objects.requireNonNull(type);
            if (id < 0 || payer < 0 || payer > 3 || (type == Type.FLOWER_PIG ? recipient != -1 : recipient < 0 || recipient > 3)
                || payer == recipient
                || amount < 1 || amount > 512 || (type == Type.KONG_REFUND || type == Type.KONG_TRANSFER || type == Type.KONG_TRANSFER_TOP_UP
                    ? relatedEntry < 0 || relatedEntry >= id : relatedEntry != -1))
                throw new IllegalArgumentException("Invalid Sichuan ledger entry");
        }
        public boolean kong() { return type == Type.DISCARD_KONG || type == Type.CONCEALED_KONG || type == Type.ADDED_KONG; }
        public boolean kongTransfer() { return type == Type.KONG_TRANSFER || type == Type.KONG_TRANSFER_TOP_UP; }
    }

    public record Score(int fan, int value, List<Fan> patterns) {
        public Score {
            patterns = List.copyOf(patterns);
            int sum = patterns.stream().mapToInt(pattern -> pattern == Fan.FULL_FLUSH || pattern == Fan.SEVEN_PAIRS ? 2 : 1).sum();
            if (fan != sum || fan < 0 || fan > 16 || value < 1 || value > 256)
                throw new IllegalArgumentException("Invalid Sichuan score");
        }
    }

    public record Win(int seat, int supplier, int tile, boolean selfDraw, boolean robbingKong, Score score) {
        public Win {
            Objects.requireNonNull(score);
            if (seat < 0 || seat > 3 || supplier < 0 || supplier > 3 || tile < 0 || tile >= 108
                || selfDraw != (seat == supplier) || selfDraw && robbingKong)
                throw new IllegalArgumentException("Invalid Sichuan win");
        }
    }

    public record Result(List<Win> wins, List<Entry> ledger, List<DrawStatus> drawStatus, boolean exhaustive) {
        public Result {
            wins = List.copyOf(wins);
            ledger = List.copyOf(ledger);
            drawStatus = List.copyOf(drawStatus);
            if (wins.size() > 3 || exhaustive != (wins.size() < 3) || drawStatus.size() != (exhaustive ? 4 : 0))
                throw new IllegalArgumentException("Invalid Sichuan result");
            if (exhaustive) for (int seat = 0; seat < 4; seat++) {
                int target = seat;
                if ((drawStatus.get(seat) == DrawStatus.WON) != wins.stream().anyMatch(win -> win.seat() == target))
                    throw new IllegalArgumentException("Invalid Sichuan draw winner");
            }
        }
        public List<Integer> deltas() {
            return points(ledger);
        }
        public int nextDealer(int dealer) {
            if (dealer < 0 || dealer > 3) throw new IllegalArgumentException("Invalid Sichuan dealer");
            if (wins.isEmpty()) return dealer;
            var first = wins.get(0);
            return wins.size() > 1 && !first.selfDraw() && wins.get(1).tile() == first.tile()
                && wins.get(1).supplier() == first.supplier() ? first.supplier() : first.seat();
        }
        public void validate(SichuanRules rules) {
            validateLedger(rules, wins, ledger);
            for (var entry : ledger) {
                if (entry.type() == Type.READY_PAYMENT && (!exhaustive || drawStatus.get(entry.payer()) == DrawStatus.READY
                    || drawStatus.get(entry.payer()) == DrawStatus.WON || drawStatus.get(entry.recipient()) != DrawStatus.READY
                    || entry.amount() > rules.value(rules.fanCap()) || Integer.bitCount(entry.amount()) != 1))
                    throw new IllegalArgumentException("Invalid Sichuan ready payment");
                int owner = entry.payer();
                boolean sanctioned = ledger.stream().anyMatch(item -> item.type() == Type.FLOWER_PIG && item.payer() == owner && item.id() < entry.id());
                if (entry.type() == Type.KONG_REFUND
                    && !sanctioned && (!exhaustive || !rules.refundKongWhenNotReady()))
                    throw new IllegalArgumentException("Invalid Sichuan draw refund");
                if (entry.type() == Type.KONG_REFUND && !sanctioned && (drawStatus.get(entry.payer()) == DrawStatus.READY
                    || drawStatus.get(entry.payer()) == DrawStatus.WON))
                    throw new IllegalArgumentException("Invalid Sichuan refund payer");
            }
            for (var source : ledger) if (source.kong()) {
                boolean refunded = ledger.stream().anyMatch(entry -> entry.type() == Type.KONG_REFUND && entry.relatedEntry() == source.id());
                boolean transferred = ledger.stream().anyMatch(entry -> entry.type() == Type.KONG_TRANSFER && entry.relatedEntry() == source.id());
                boolean mustRefund = !transferred && (ledger.stream().anyMatch(entry -> entry.type() == Type.FLOWER_PIG && entry.payer() == source.recipient())
                    || exhaustive && rules.refundKongWhenNotReady()
                        && drawStatus.get(source.recipient()) != DrawStatus.READY && drawStatus.get(source.recipient()) != DrawStatus.WON);
                if (refunded != mustRefund) throw new IllegalArgumentException("Missing Sichuan kong refund");
            }
            if (exhaustive) for (int seat = 0; seat < 4; seat++) {
                int target = seat;
                if ((drawStatus.get(seat) == DrawStatus.ACTIVE_FLOWER_PIG) != ledger.stream().anyMatch(entry -> entry.type() == Type.FLOWER_PIG && entry.payer() == target))
                    throw new IllegalArgumentException("Invalid Sichuan flower pig result");
            }
        }
    }

    public static List<Entry> kongTransfers(int payer, List<Entry> sources, List<Integer> winners, int firstId) {
        int total = sources.stream().mapToInt(Entry::amount).sum();
        if (total == 0) return List.of();
        int share = (total + winners.size() - 1) / winners.size();
        var transfers = new ArrayList<Entry>();
        int sourceIndex = 0;
        int available = sources.get(0).amount();
        for (int winner : winners) {
            int due = share;
            while (due > 0 && sourceIndex < sources.size()) {
                var source = sources.get(sourceIndex);
                int amount = Math.min(due, available);
                transfers.add(new Entry(firstId + transfers.size(), Type.KONG_TRANSFER, payer, winner, amount, source.id()));
                due -= amount; available -= amount;
                if (available == 0 && ++sourceIndex < sources.size()) available = sources.get(sourceIndex).amount();
            }
            if (due > 0) transfers.add(new Entry(firstId + transfers.size(), Type.KONG_TRANSFER_TOP_UP, payer, winner, due, sources.get(0).id()));
        }
        return List.copyOf(transfers);
    }

    public static void validateLedger(SichuanRules rules, List<Win> wins, List<Entry> ledger) {
        var retired = new HashSet<Integer>();
        var payments = ledger.stream().filter(entry -> entry.type() == Type.DISCARD_WIN || entry.type() == Type.SELF_DRAW_WIN).toList();
        int payment = 0;
        for (var win : wins) {
            if (!retired.add(win.seat()) || retired.contains(win.supplier()) && !win.selfDraw() || win.score().value() != rules.value(win.score().fan())
                || ledger.stream().anyMatch(entry -> entry.type() == Type.FLOWER_PIG && entry.payer() == win.seat()))
                throw new IllegalArgumentException("Invalid Sichuan winner order");
            for (int payer = 0; payer < 4; payer++) if (!retired.contains(payer) && (win.selfDraw() || payer == win.supplier())) {
                if (payment >= payments.size()) throw new IllegalArgumentException("Missing Sichuan win payment");
                var entry = payments.get(payment++);
                if (entry.payer() != payer || entry.recipient() != win.seat()
                    || entry.type() != (win.selfDraw() ? Type.SELF_DRAW_WIN : Type.DISCARD_WIN)
                    || entry.amount() != win.score().value() + (win.selfDraw() ? rules.selfDrawBonus() : 0))
                    throw new IllegalArgumentException("Invalid Sichuan win payment");
            }
        }
        if (payment != payments.size()) throw new IllegalArgumentException("Unmatched Sichuan win payment");
        var refunded = new HashSet<Integer>();
        var transferred = new HashSet<Integer>();
        var penalties = new HashSet<Integer>();
        var ledgerRetired = new HashSet<Integer>();
        for (int index = 0; index < ledger.size(); index++) {
            var entry = ledger.get(index);
            if (entry.id() != index) throw new IllegalArgumentException("Invalid ledger order");
            int amount = switch (entry.type()) {
                case CONCEALED_KONG -> rules.concealedKongPayment();
                case DISCARD_KONG -> rules.discardKongPayment();
                case ADDED_KONG -> rules.addedKongPayment();
                case FLOWER_PIG -> rules.activeFlowerPigPenalty();
                default -> entry.amount();
            };
            if (entry.amount() != amount || entry.type() == Type.FLOWER_PIG && !penalties.add(entry.payer()))
                throw new IllegalArgumentException("Invalid Sichuan payment");
            if ((entry.kong() || entry.type() == Type.DISCARD_WIN || entry.type() == Type.SELF_DRAW_WIN) && ledgerRetired.contains(entry.payer())
                || entry.kong() && ledgerRetired.contains(entry.recipient()))
                throw new IllegalArgumentException("Retired Sichuan payment participant");
            if (entry.type() == Type.DISCARD_WIN || entry.type() == Type.SELF_DRAW_WIN) ledgerRetired.add(entry.recipient());
            if (entry.kongTransfer()) {
                int end = index;
                while (end < ledger.size() && ledger.get(end).kongTransfer()) end++;
                int startWin = index;
                while (startWin > 0 && ledger.get(startWin - 1).type() == Type.DISCARD_WIN) startWin--;
                if (!rules.transferKongOnShoot() || startWin == index) throw new IllegalArgumentException("Unmatched kong transfer");
                var claimants = ledger.subList(startWin, index).stream().map(Entry::recipient).toList();
                var winningClaims = wins.stream().filter(win -> claimants.contains(win.seat())).toList();
                if (winningClaims.size() != claimants.size() || winningClaims.stream().anyMatch(win -> win.selfDraw()
                    || win.supplier() != entry.payer() || !win.score().patterns().contains(Fan.SHOOT_AFTER_KONG)
                    || win.tile() != winningClaims.get(0).tile()))
                    throw new IllegalArgumentException("Invalid kong transfer winners");
                var batch = ledger.subList(index, end);
                var sources = batch.stream().filter(item -> item.type() == Type.KONG_TRANSFER).map(Entry::relatedEntry)
                    .distinct().sorted().map(ledger::get).toList();
                for (var source : sources) if (!source.kong() || source.id() >= startWin || source.recipient() != entry.payer()
                    || refunded.contains(source.id()) || !transferred.add(source.id()))
                    throw new IllegalArgumentException("Invalid kong transfer source");
                if (!batch.equals(kongTransfers(entry.payer(), sources, claimants, index)))
                    throw new IllegalArgumentException("Invalid kong transfer allocation");
                index = end - 1;
            } else if (entry.type() == Type.KONG_REFUND) {
                var original = ledger.get(entry.relatedEntry());
                if (!original.kong() || !refunded.add(original.id()) || entry.payer() != original.recipient()
                    || entry.recipient() != original.payer() || entry.amount() != original.amount()
                    || transferred.contains(original.id()))
                    throw new IllegalArgumentException("Invalid kong refund");
            }
        }
    }

    public static List<Integer> points(List<Entry> ledger) {
        int[] points = new int[4];
        for (var entry : ledger) {
            points[entry.payer()] = Math.subtractExact(points[entry.payer()], entry.amount());
            if (entry.recipient() >= 0) points[entry.recipient()] = Math.addExact(points[entry.recipient()], entry.amount());
        }
        return java.util.Arrays.stream(points).boxed().toList();
    }
}

package top.skyeyefast.mchjong.engine;

import java.util.List;
import java.util.Objects;

public final class SichuanSettlement {
    private SichuanSettlement() {}
    public enum Type { DISCARD_WIN, SELF_DRAW_WIN, DISCARD_KONG, CONCEALED_KONG, ADDED_KONG,
                       KONG_REFUND, FLOWER_PIG, READY_PAYMENT }
    public enum Fan { KONG, ROOT, ALL_PUNGS, GOLDEN_SINGLE_WAIT, FULL_FLUSH, SEVEN_PAIRS,
                      WIN_AFTER_KONG, SHOOT_AFTER_KONG, ROBBING_KONG, UNDER_THE_SEA }
    public enum DrawStatus { WON, READY, NOT_READY, PASSIVE_FLOWER_PIG, ACTIVE_FLOWER_PIG }

    public record Entry(int id, Type type, int payer, int recipient, int amount, int relatedEntry) {
        public Entry {
            Objects.requireNonNull(type);
            if (id < 0 || payer < 0 || payer > 3 || (type == Type.FLOWER_PIG ? recipient != -1 : recipient < 0 || recipient > 3)
                || payer == recipient
                || amount < 1 || amount > 512 || (type == Type.KONG_REFUND ? relatedEntry < 0 || relatedEntry >= id : relatedEntry != -1))
                throw new IllegalArgumentException("Invalid Sichuan ledger entry");
        }
        public boolean kong() { return type == Type.DISCARD_KONG || type == Type.CONCEALED_KONG || type == Type.ADDED_KONG; }
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
            if (drawStatus.size() != (exhaustive ? 4 : 0)) throw new IllegalArgumentException("Invalid Sichuan result");
        }
        public List<Integer> deltas() {
            int[] points = new int[4];
            for (var entry : ledger) {
                points[entry.payer()] = Math.subtractExact(points[entry.payer()], entry.amount());
                if (entry.recipient() >= 0) points[entry.recipient()] = Math.addExact(points[entry.recipient()], entry.amount());
            }
            return java.util.Arrays.stream(points).boxed().toList();
        }
    }
}

package top.skyeyefast.mchjong.engine;

import java.util.List;

public record HandScore(int han, int fu, int yakuman, int ron, int tsumoDealer, int tsumoChild,
                        List<Yaku> yaku, int dora) {
    public HandScore {
        yaku = List.copyOf(yaku);
        if (yaku.size() > YakuSelection.MAX_AWARDS || yaku.stream().map(Yaku::name).distinct().count() != yaku.size())
            throw new IllegalArgumentException("Invalid scored yaku list");
    }

    /** Value for this interpretation before any compound-yakuman cap; yakuman use 13 han per unit. */
    public record Yaku(String name, int han, boolean yakuman) {
        public Yaku {
            if (name == null || name.isBlank() || name.length() > 128 || han < 1 || han > 26
                || yakuman && han % 13 != 0)
                throw new IllegalArgumentException("Invalid scored yaku");
        }
    }

    public boolean hasYaku(String name) { return yaku.stream().anyMatch(value -> value.name().equals(name)); }
}

package top.skyeyefast.mchjong.engine;

import java.util.List;

public record HandScore(int han, int fu, int yakuman, int ron, int tsumoDealer, int tsumoChild,
                        List<String> yaku, int dora) {
    public HandScore { yaku = List.copyOf(yaku); }
}

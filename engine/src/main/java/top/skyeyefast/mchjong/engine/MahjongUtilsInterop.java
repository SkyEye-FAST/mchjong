package top.skyeyefast.mchjong.engine;

import mahjongutils.hora.Hora;
import mahjongutils.hora.HoraHandPattern;

/** Java bridge for upstream Kotlin APIs whose public JVM surface is hidden from Kotlin callers. */
final class MahjongUtilsInterop {
    private MahjongUtilsInterop() {}

    static Hora withPattern(Hora source, HoraHandPattern pattern) {
        return new Hora(pattern, source.getDora(), source.getExtraYaku(), source.getOptions());
    }
}

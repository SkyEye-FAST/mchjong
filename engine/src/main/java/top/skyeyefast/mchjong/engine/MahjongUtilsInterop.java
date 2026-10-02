package top.skyeyefast.mchjong.engine;

import mahjongutils.hora.Hora;
import mahjongutils.hora.HoraHandPattern;
import mahjongutils.hora.HoraOptions;
import mahjongutils.shanten.CommonShantenArgs;
import mahjongutils.shanten.InternalShantenArgs;
import mahjongutils.shanten.ShantenKt;
import mahjongutils.shanten.UnionShantenResult;
import mahjongutils.CalcContext;
import mahjongutils.yaku.Yaku;
import java.util.Set;

/** Java bridge for upstream Kotlin APIs whose public JVM surface is hidden from Kotlin callers. */
final class MahjongUtilsInterop {
    private MahjongUtilsInterop() {}

    static String meldNotation(Meld meld) {
        StringBuilder text = new StringBuilder();
        meld.tiles().stream().map(Tile::kind).sorted().forEach(k -> text.append(k < 27 ? k % 9 + 1 : k - 26));
        text.append("mpsz".charAt(meld.kind() / 9));
        if (meld.closed()) return "0" + text.substring(1, 3) + "0" + text.charAt(4);
        return text.toString();
    }

    // 0.7.7 exposes these switches on the JVM, but marks them internal to Kotlin.
    // Keep every discard and the existing shape algorithm; callers supply visible
    // counts, legal declarations and bounded improvement search themselves.
    static UnionShantenResult analyze(CommonShantenArgs args, boolean goodShape) {
        mahjongutils.shanten.CommonShantenArgsKt.throwOnValidationError(args);
        return ShantenKt.shanten(new CalcContext(), new InternalShantenArgs(args.getTiles(), args.getFuro(),
            false, goodShape, args.getBestShantenOnly(), false, false));
    }

    static Hora score(HoraHandPattern pattern, int dora, Set<Yaku> extra, HoraOptions options) {
        return new Hora(pattern, dora, extra, options);
    }
}

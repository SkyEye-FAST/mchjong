package top.skyeyefast.mchjong.engine

import mahjongutils.hora.HoraHandPattern
import mahjongutils.yaku.Yaku
import mahjongutils.yaku.Yakus

/** Collects ordinary candidates before the library's yakuman short circuit, then applies table switches. */
internal object RiichiNativeAwards {
    fun find(pattern: HoraHandPattern, facts: YakuFacts, yakus: Yakus, extra: Set<Yaku>, selection: YakuSelection): List<Yaku> {
        val candidates = (yakus.allCommonYaku + yakus.allYakuman).filter {
            (pattern.menzen || !it.menzenOnly) && it.check(pattern)
        }.toSet() + extra
        val found = candidates.toMutableSet()
        found.removeAll { !selection.enabled(YakuCatalog.awardId(it.name)) }
        fun add(yaku: Yaku, matches: Boolean) {
            if (matches && selection.enabled(YakuCatalog.awardId(yaku.name))) found += yaku
        }
        for ((base, stronger) in listOf(yakus.Kokushi to yakus.KokushiThirteenWaiting,
            yakus.Suanko to yakus.SuankoTanki, yakus.Churen to yakus.ChurenNineWaiting, yakus.Richi to yakus.WRichi)) {
            add(base, stronger in candidates && stronger !in found)
        }
        add(yakus.Ipe, facts.closed() && yakus.Ryanpe !in found && facts.groups().filter { it.type() == "sequence" }
            .groupingBy { it.kind() }.eachCount().values.any { it >= 2 })
        // mahjong-utils 0.7.7 embeds these yakuman exclusions in the ordinary checkers.
        // Once a table disables the stronger award, its qualifying ordinary hand remains scoreable.
        val noNatural = found.none { it.isYakuman }
        if (noNatural) {
            add(yakus.Toitoi, facts.shape() == "regular" && facts.groups().all { it.type() != "sequence" })
            add(yakus.Sananko, facts.groups().count { it.type() != "sequence" && it.concealed() } >= 3)
            add(yakus.Sankantsu, facts.groups().count { it.type() == "quad" } >= 3)
            add(yakus.Honroto, facts.shape() != "thirteen_orphans" && facts.kinds().all { it >= 27 || it % 9 == 0 || it % 9 == 8 })
        }
        return found.toList()
    }
}

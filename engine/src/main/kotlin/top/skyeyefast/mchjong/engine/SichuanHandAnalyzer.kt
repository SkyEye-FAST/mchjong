package top.skyeyefast.mchjong.engine

import mahjongutils.models.Tile as LibraryTile
import mahjongutils.shanten.CommonShantenArgs

object SichuanHandAnalyzer {
    @JvmStatic
    fun score(hand: List<Int>, melds: List<Meld>, rules: SichuanRules,
              afterKong: Boolean, shootAfterKong: Boolean, robbing: Boolean, lastTile: Boolean): SichuanSettlement.Score? {
        if (hand.size + melds.size * 3 != 14) return null
        val counts = IntArray(27)
        hand.forEach { counts[Tile.kind(it)]++ }
        val sevenPairs = melds.isEmpty() && counts.all { it % 2 == 0 }
        val allPungs = counts.count { it % 3 == 2 } == 1 && counts.all { it % 3 != 1 }
        val regular = MahjongUtilsInterop.analyze(CommonShantenArgs(hand.map {
            LibraryTile[Tile.notation(Tile.kind(it))]
        }, bestShantenOnly = true), false).regular.shantenInfo.shantenNum == -1
        if (!sevenPairs && !regular) return null
        val patterns = mutableListOf<SichuanSettlement.Fan>()
        if (sevenPairs) patterns += SichuanSettlement.Fan.SEVEN_PAIRS
        else if (allPungs) {
            patterns += SichuanSettlement.Fan.ALL_PUNGS
            if (melds.size == 4) patterns += SichuanSettlement.Fan.GOLDEN_SINGLE_WAIT
        }
        val owned = hand + melds.flatMap { it.tiles() }
        if (owned.map { Tile.kind(it) / 9 }.distinct().size == 1) patterns += SichuanSettlement.Fan.FULL_FLUSH
        for ((kind, tiles) in owned.groupBy { Tile.kind(it) }) if (tiles.size == 4) {
            patterns += if (melds.any { it.kind() == kind && it.quad() }) SichuanSettlement.Fan.KONG else SichuanSettlement.Fan.ROOT
        }
        if (afterKong) patterns += SichuanSettlement.Fan.WIN_AFTER_KONG
        if (shootAfterKong) patterns += SichuanSettlement.Fan.SHOOT_AFTER_KONG
        if (robbing) patterns += SichuanSettlement.Fan.ROBBING_KONG
        if (lastTile) patterns += SichuanSettlement.Fan.UNDER_THE_SEA
        val fan = patterns.sumOf { if (it == SichuanSettlement.Fan.SEVEN_PAIRS || it == SichuanSettlement.Fan.FULL_FLUSH) 2 else 1 }
        return SichuanSettlement.Score(fan, rules.value(fan), patterns)
    }

    @JvmStatic
    fun readyValue(hand: List<Int>, melds: List<Meld>, voidSuit: Int, rules: SichuanRules): Int {
        val owned = hand + melds.flatMap { it.tiles() }
        if (voidSuit < 0 || owned.any { Tile.kind(it) / 9 == voidSuit } || hand.size + melds.size * 3 != 13) return 0
        return (0..<27).filter { it / 9 != voidSuit && owned.count { tile -> Tile.kind(tile) == it } < 4 }
            .maxOfOrNull { kind -> score(hand + kind * 4, melds, rules, false, false, false, false)?.value() ?: 0 } ?: 0
    }
}

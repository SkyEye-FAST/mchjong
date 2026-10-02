package top.skyeyefast.mchjong.engine

import mahjongutils.models.Tile as LibraryTile
import mahjongutils.shanten.CommonShantenArgs
import mahjongutils.shanten.ShantenWithoutGot

object SichuanHandAnalyzer {
    @JvmRecord
    data class Progress(val shanten: Int, val effectiveKinds: Set<Int>, val remainingCount: Int)

    /** Post-discard shape. Regular shapes use mahjong-utils; Sichuan counts a four as two pairs. */
    @JvmStatic
    fun analyze(hand: List<Int>, melds: List<Meld>, voidSuit: Int, visible: List<Int>): Progress {
        val owned = hand + melds.flatMap { it.tiles() }
        require(voidSuit in 0..2 && hand.size + 3 * melds.size == 13
            && owned.all { it in 0..<108 } && owned.distinct().size == owned.size
            && melds.none { it.type() == Meld.Type.SEQUENCE })
        // The library infers the required concealed groups from 13 - 3 * meld count.
        val regular = MahjongUtilsInterop.analyze(CommonShantenArgs(hand.map {
            LibraryTile[Tile.notation(Tile.kind(it))]
        }, bestShantenOnly = true), false).regular.shantenInfo as ShantenWithoutGot
        val counts = IntArray(27)
        hand.forEach { counts[Tile.kind(it)]++ }
        val pairs = if (melds.isEmpty()) 6 - counts.sumOf { it / 2 } else Int.MAX_VALUE
        val shanten = minOf(regular.shantenNum, pairs)
        val effective = sortedSetOf<Int>()
        if (regular.shantenNum == shanten) effective += regular.advance.map { Tile.parseKind(it.toString()) }
        if (pairs == shanten) effective += (0..<27).filter { counts[it] % 2 == 1 }
        val known = (owned + visible).filter { it in 0..<108 }.toSet().groupingBy(Tile::kind).eachCount()
        effective.removeIf { it / 9 == voidSuit || (known[it] ?: 0) >= 4 }
        return Progress(shanten, java.util.Collections.unmodifiableSet(effective), effective.sumOf { 4 - (known[it] ?: 0) })
    }

    @JvmStatic
    fun score(hand: List<Int>, melds: List<Meld>, rules: SichuanRules,
              afterKong: Boolean, shootAfterKong: Boolean, robbing: Boolean, lastTile: Boolean): SichuanSettlement.Score? {
        val owned = hand + melds.flatMap { it.tiles() }
        if (hand.size + melds.size * 3 != 14 || melds.any { it.type() == Meld.Type.SEQUENCE }
            || owned.any { it !in 0..<108 } || owned.distinct().size != owned.size) return null
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
        if (owned.map { Tile.kind(it) / 9 }.distinct().size == 1) patterns += SichuanSettlement.Fan.FULL_FLUSH
        val kongKinds = melds.filter { it.quad() }.map { it.kind() }.toSet()
        owned.groupBy { Tile.kind(it) }.filter { it.value.size == 4 }.forEach { (kind, _) ->
            patterns += if (rules.separateKongFan() && kind in kongKinds) SichuanSettlement.Fan.KONG else SichuanSettlement.Fan.ROOT
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
            .maxOfOrNull { kind ->
                val tile = (kind * 4..<kind * 4 + 4).first { it !in owned }
                score(hand + tile, melds, rules, false, false, false, false)?.value() ?: 0
            } ?: 0
    }
}

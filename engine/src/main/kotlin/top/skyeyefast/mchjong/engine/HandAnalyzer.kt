package top.skyeyefast.mchjong.engine

import mahjongutils.hora.HoraHandPattern
import mahjongutils.hora.HoraOptions
import mahjongutils.hora.hora
import mahjongutils.models.Furo
import mahjongutils.models.MentsuType
import mahjongutils.models.Wind
import mahjongutils.models.Tile as LibraryTile
import mahjongutils.models.hand.RegularHandPattern
import mahjongutils.models.hand.Hand
import mahjongutils.shanten.ShantenWithGot
import mahjongutils.shanten.ShantenWithoutGot
import mahjongutils.shanten.shanten
import mahjongutils.shanten.UnionShantenResult
import mahjongutils.yaku.Yakus

/** The only boundary to mahjong-utils. No Minecraft or network types are accepted here. */
object HandAnalyzer {
    private fun tiles(ids: List<Int>) = ids.map { LibraryTile[Tile.notation(Tile.kind(it))] }
    private fun furo(melds: List<Meld>) = melds.map { Furo(it.libraryNotation()) }
    private fun kind(tile: LibraryTile) = Tile.parseKind(tile.toString())

    // Shanten concerns only the concealed remainder. Passing declared quads to
    // upstream's move-suggestion API incorrectly suggests declaring those quads
    // again. Attach fixed melds to scoring patterns, not to the move search.
    private fun analyze(hand: List<Int>, melds: List<Meld>, best: Boolean): UnionShantenResult {
        val result = shanten(tiles(hand), bestShantenOnly = best)
        if (melds.isEmpty()) return result
        val declared = furo(melds)
        val patterns = result.regular.hand.patterns.map { it.copy(k = 4, furo = declared) }
        val regular = result.regular.copy(hand = Hand(result.hand.tiles, declared, patterns))
        return result.copy(hand = regular.hand, regular = regular)
    }

    @JvmStatic
    fun shantenNumber(hand: List<Int>, melds: List<Meld>): Int =
        analyze(hand, melds, true).shantenInfo.shantenNum

    @JvmStatic
    fun waits(hand: List<Int>, melds: List<Meld>): Set<Int> {
        if (hand.size % 3 != 1) return emptySet()
        val result = analyze(hand, melds, true).shantenInfo
        return if (result is ShantenWithoutGot && result.shantenNum == 0) {
            val owned = hand + melds.flatMap { it.tiles() }
            result.advance.map(::kind).filter { wait -> owned.count { Tile.kind(it) == wait } < 4 }.toSortedSet()
        } else emptySet()
    }

    @JvmStatic
    fun tenpaiDiscards(hand: List<Int>, melds: List<Meld>): Set<Int> {
        val result = analyze(hand, melds, false).shantenInfo
        return if (result is ShantenWithGot) {
            result.discardToAdvance.filterValues { it.shantenNum == 0 }.keys.map(::kind).filter { discard ->
                waits(hand - hand.first { Tile.kind(it) == discard }, melds).isNotEmpty()
            }.toSet()
        } else emptySet()
    }

    @JvmStatic
    fun bestDiscardKinds(hand: List<Int>, melds: List<Meld>): Set<Int> {
        val result = analyze(hand, melds, false).shantenInfo
        if (result !is ShantenWithGot || result.discardToAdvance.isEmpty()) return emptySet()
        val minimum = result.discardToAdvance.values.minOf { it.shantenNum }
        return result.discardToAdvance.filterValues { it.shantenNum == minimum }.keys.map(::kind).toSet()
    }

    @JvmStatic
    fun riichiKanKeepsMelds(handBeforeDraw: List<Int>, melds: List<Meld>, kanKind: Int): Boolean {
        val current = waits(handBeforeDraw, melds)
        if (current.isEmpty()) return false
        for (wait in current) {
            val complete = tiles(handBeforeDraw) + LibraryTile[Tile.notation(wait)]
            val analysis = shanten(complete, bestShantenOnly = true)
            for (pattern in analysis.hand.patterns) {
                if (pattern !is RegularHandPattern || pattern.menzenMentsu.none {
                    it.type == MentsuType.Kotsu && kind(it.tile) == kanKind
                }) return false
            }
        }
        return true
    }

    @JvmStatic
    fun score(hand: List<Int>, melds: List<Meld>, winningTile: Int, tsumo: Boolean,
              selfWind: Int, roundWind: Int, dora: Int, extra: List<String>, rules: RuleSet): HandScore? {
        val complete = if (hand.size + melds.size * 3 == 13) hand + winningTile else hand
        val analysis = analyze(complete, melds, true)
        if (analysis.shantenInfo.shantenNum != -1) return null
        val options = HoraOptions(false, true, rules.doubleWindPairFu(), rules.kiriageMangan(),
            rules.kazoeYakuman(), rules.doubleYakuman(), true)
        val yakus = Yakus(options)
        val extraYaku = extra.map(yakus::getYaku).toSet()
        val agari = LibraryTile[Tile.notation(Tile.kind(winningTile))]
        val self = Wind.entries[selfWind]
        val round = Wind.entries[roundWind]
        val baseline = hora(analysis, agari, tsumo, dora, self, round, extraYaku, options)

        // The upstream convenience function compares han before fu. A 3-han 70-fu
        // interpretation can beat 4-han 30-fu, so compare actual payments instead.
        val candidates = analysis.hand.patterns.flatMap {
            HoraHandPattern.build(it, agari, tsumo, self, round)
        }.map { baseline.copy(pattern = it) }.filter { it.yaku.isNotEmpty() }
        val result = candidates.maxWithOrNull(compareBy({
            if (!tsumo) {
                if (selfWind == 0) it.parentPoint.ron else it.childPoint.ron
            } else if (selfWind == 0) it.parentPoint.tsumo * (rules.players() - 1).toUInt()
            else it.childPoint.tsumoParent + it.childPoint.tsumoChild * (rules.players() - 2).toUInt()
        }, { it.han }, { it.hu })) ?: return null
        val dealer = selfWind == 0
        return HandScore(result.han, result.hu, if (result.hasYakuman) result.han / 13 else 0,
            Math.toIntExact((if (dealer) result.parentPoint.ron else result.childPoint.ron).toLong()),
            Math.toIntExact((if (dealer) result.parentPoint.tsumo else result.childPoint.tsumoParent).toLong()),
            Math.toIntExact((if (dealer) result.parentPoint.tsumo else result.childPoint.tsumoChild).toLong()),
            result.yaku.map { it.name }.sorted(), if (result.hasYakuman) 0 else dora)
    }
}

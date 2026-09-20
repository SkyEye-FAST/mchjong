package top.skyeyefast.mchjong.engine

import mahjongutils.hora.HoraHandPattern
import mahjongutils.hora.HoraOptions
import mahjongutils.hora.hora
import mahjongutils.hanhu.HanHuOptions
import mahjongutils.hanhu.getChildPointByHanHu
import mahjongutils.hanhu.getParentPointByHanHu
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
    private fun options(rules: RuleConfig) = HoraOptions(aotenjou = false, allowKuitan = rules.kuitan(),
        hasRenpuuJyantouHu = rules.doubleWindPairFu(), hasKiriageMangan = rules.kiriageMangan(),
        hasKazoeYakuman = rules.kazoeYakuman(), hasMultipleYakuman = rules.doubleYakuman(), hasComplexYakuman = rules.compoundYakuman())

    @JvmStatic
    fun yakuValues(names: List<String>, closed: Boolean, rules: RuleConfig): List<ReplayHand.Yaku> {
        val yakus = Yakus(options(rules))
        return names.map { name ->
            if (name == "Renhou" && rules.renhouMangan()) return@map ReplayHand.Yaku(name, 5, false)
            val yaku = yakus.getYaku(name)
            ReplayHand.Yaku(name, yaku.han - if (closed) 0 else yaku.furoLoss, yaku.isYakuman)
        }
    }

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

    private fun efficiency(result: ShantenWithoutGot) = TileEfficiency(result.shantenNum,
        result.advance.map(::kind).toSet(), result.goodShapeAdvance.orEmpty().map(::kind).toSet())

    @JvmStatic
    fun discardEfficiency(hand: List<Int>, melds: List<Meld>): Map<Int, TileEfficiency> {
        val result = analyze(hand, melds, false).shantenInfo as? ShantenWithGot ?: return emptyMap()
        return result.discardToAdvance.mapKeys { kind(it.key) }.mapValues { efficiency(it.value) }
    }

    @JvmStatic
    fun handEfficiency(hand: List<Int>, melds: List<Meld>): TileEfficiency =
        efficiency(analyze(hand, melds, false).shantenInfo as ShantenWithoutGot)

    @JvmStatic
    fun riichiKanKeepsMelds(handBeforeDraw: List<Int>, melds: List<Meld>, kanKind: Int): Boolean {
        // Inspect the original tenpai decompositions, including shapes whose
        // completing tile would be a fifth copy (WRC 8.9.1), not just live waits.
        val analysis = analyze(handBeforeDraw, melds, true)
        if (analysis.shantenInfo.shantenNum != 0) return false
        return analysis.hand.patterns.all { pattern ->
            pattern is RegularHandPattern && pattern.menzenMentsu.any {
                it.type == MentsuType.Kotsu && kind(it.tile) == kanKind
            }
        }
    }

    @JvmStatic
    fun score(hand: List<Int>, melds: List<Meld>, winningTile: Int, tsumo: Boolean,
              selfWind: Int, roundWind: Int, dora: Int, extra: List<String>, rules: RuleConfig): HandScore? {
        val complete = if (hand.size + melds.size * 3 == 13) hand + winningTile else hand
        val analysis = analyze(complete, melds, true)
        if (analysis.shantenInfo.shantenNum != -1) return null
        val options = options(rules)
        val yakus = Yakus(options)
        val renhou = rules.renhouMangan() && !tsumo && selfWind != 0 && extra.contains("Renhou")
        val extraYaku = extra.filter { it != "Renhou" && (it != "Ippatsu" || rules.ippatsu()) }.map(yakus::getYaku).toSet()
        val agari = LibraryTile[Tile.notation(Tile.kind(winningTile))]
        val self = Wind.entries[selfWind]
        val round = Wind.entries[roundWind]
        val baseline = hora(analysis, agari, tsumo, dora, self, round, extraYaku, options)

        // The upstream convenience function compares han before fu. A 3-han 70-fu
        // interpretation can beat 4-han 30-fu, so compare actual payments instead.
        val candidates = analysis.hand.patterns.flatMap {
            HoraHandPattern.build(it, agari, tsumo, self, round)
        }.map { MahjongUtilsInterop.withPattern(baseline, it) }.filter {
            val excludedIppatsu = if (!rules.ippatsuCountsTowardMinimum() && it.yaku.any { yaku -> yaku.name == "Ippatsu" }) 1 else 0
            it.yaku.isNotEmpty() && (it.hasYakuman || it.han - dora - excludedIppatsu >= rules.minHan())
        }.map { result ->
            // 0.7.7 omits 3-han 60-fu kiriage and applies the kazoe switch to natural
            // yakuman payments. Keep its yaku/fu analysis and correct the point-table inputs.
            val yakuman = if (result.hasYakuman) result.han / 13 else 0
            val pointHan = if (yakuman > 0) 13 else if (rules.kiriageMangan() && result.han == 3 && result.hu == 60) 5 else result.han
            val pointOptions = HanHuOptions(hasKiriageMangan = rules.kiriageMangan(),
                hasKazoeYakuman = yakuman > 0 || rules.kazoeYakuman())
            val parent = getParentPointByHanHu(pointHan, result.hu.coerceAtLeast(20), pointOptions)
            val child = getChildPointByHanHu(pointHan, result.hu.coerceAtLeast(20), pointOptions)
            val multiplier = yakuman.coerceAtLeast(1).toULong()
            val dealer = selfWind == 0
            HandScore(result.han, result.hu, yakuman,
                if (tsumo) 0 else Math.toIntExact(((if (dealer) parent.ron else child.ron) * multiplier).toLong()),
                if (!tsumo) 0 else Math.toIntExact(((if (dealer) parent.tsumo else child.tsumoParent) * multiplier).toLong()),
                if (!tsumo) 0 else Math.toIntExact(((if (dealer) parent.tsumo else child.tsumoChild) * multiplier).toLong()),
                result.yaku.map { it.name }.sorted(), if (yakuman > 0) 0 else dora)
        }
        val result = candidates.maxWithOrNull(compareBy({
            if (!tsumo) it.ron() else if (selfWind == 0) it.tsumoDealer() * (rules.players() - 1)
            else it.tsumoDealer() + it.tsumoChild() * (rules.players() - 2)
        }, { it.han() }, { it.fu() }))
        // WRC renhou is an alternative mangan, never five han added to the regular hand.
        if (renhou && (result == null || result.ron() <= 8000))
            return HandScore(5, 0, 0, 8000, 0, 0, listOf("Renhou"), 0)
        return result
    }
}

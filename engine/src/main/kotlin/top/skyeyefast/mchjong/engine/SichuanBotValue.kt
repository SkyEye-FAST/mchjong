package top.skyeyefast.mchjong.engine

import kotlin.math.exp
import kotlin.math.ln

/** Bounded, copy-consuming route fits. The analyzer alone validates and scores completed targets. */
internal class SichuanBotValue(private val rules: SichuanRules, private val voidSuit: Int,
                               private val known: Set<Int>) {
    data class Evaluation(val progress: SichuanHandAnalyzer.Progress, val utility: Double)
    private data class Fit(val need: IntArray, val last: Int, val missing: Int, val scarcity: Double)
    private data class ScoreKey(val counts: List<Int>, val melds: List<Meld>)
    private val scores = mutableMapOf<ScoreKey, SichuanSettlement.Score?>()
    private val live = IntArray(27) { kind -> 4 - known.count { Tile.kind(it) == kind } }

    fun evaluate(hand: List<Int>, melds: List<Meld>, progress: SichuanHandAnalyzer.Progress): Evaluation {
        val efficiency = -3.0 * progress.shanten + 0.035 * progress.remainingCount + 0.01 * progress.effectiveKinds.size
        if (rules.fanCap() == 0) return Evaluation(progress, efficiency)
        val held = IntArray(27)
        hand.forEach { held[Tile.kind(it)]++ }
        val fixed = melds.flatMap { it.tiles() }.toSet()
        val kinds = (0..<27).filter { it / 9 != voidSuit }
        var utility = efficiency

        fun fit(need: IntArray, last: Int): Fit? {
            var missing = 0
            var scarcity = 0.0
            for (kind in need.indices) {
                if (need[kind] > held[kind] + live[kind]) return null
                val deficit = maxOf(0, need[kind] - held[kind])
                missing += deficit
                if (deficit > 0) scarcity += deficit * ln(4.0 / live[kind])
            }
            return if (missing <= 7) Fit(need, last, missing, scarcity) else null
        }
        fun assess(targets: List<Fit>, root: Int = -1) {
            for (target in targets.filter { root < 0 || it.need[root] >= 4 }
                .sortedWith(compareBy<Fit> { it.missing }.thenBy { it.scarcity }).take(4)) {
                val score = scores.getOrPut(ScoreKey(target.need.toList(), melds)) {
                    val tiles = target.need.indices.flatMap { kind ->
                        (kind * 4..<kind * 4 + 4).filter { it !in fixed }.take(target.need[kind])
                    }
                    SichuanHandAnalyzer.score(tiles, melds, rules, false, false, false, false)
                } ?: continue
                // Capped payout, rather than raw fan: combinations above the cap add no reward.
                val fan = ln(score.value().toDouble()) / ln(2.0)
                val distance = maxOf(0, target.missing - 1)
                val availability = exp(-0.15 * target.scarcity)
                val support = target.need.indices.filter { target.need[it] > held[it] }.sumOf { live[it] }
                val value = -3.0 * distance + 3.5 * fan * exp(-0.12 * distance) * availability
                    + 0.035 * support + 0.1 * efficiency
                utility = maxOf(utility, value)
            }
        }
        fun targets(allowed: List<Int>, pairs: Boolean, sequences: Boolean, root: Int = -1) {
            val groups = allowed.map { kind -> IntArray(if (pairs) 2 else 3) { kind } } +
                if (sequences) allowed.filter { it % 9 <= 6 }.map { intArrayOf(it, it + 1, it + 2) } else emptyList()
            val order = compareBy<Fit> { it.missing + if (root >= 0) maxOf(0, 4 - it.need[root]) else 0 }
                .thenBy { it.scarcity }.thenBy { it.last }
            // Reserve the head before fitting groups, so a retained pair cannot also be spent as a pung.
            val heads = if (pairs) listOf(-1) else allowed
            var beam = heads.mapNotNull { head ->
                val initial = IntArray(27)
                if (head >= 0) initial[head] = 2
                fit(initial, -1)
            }
            repeat(if (pairs) 7 else 4 - melds.size) {
                val next = ArrayList<Fit>()
                for (state in beam) for (index in maxOf(0, state.last)..groups.lastIndex) {
                    val need = state.need.clone()
                    groups[index].forEach { need[it]++ }
                    fit(need, index)?.let(next::add)
                }
                beam = next.sortedWith(order).distinctBy { it.need.toList() }.take(12)
            }
            assess(beam, root)
        }

        if (melds.isEmpty()) targets(kinds, pairs = true, sequences = false)
        targets(kinds, pairs = false, sequences = false)
        for (suit in 0..2) if (suit != voidSuit && melds.all { it.kind() / 9 == suit }) {
            val pure = kinds.filter { it / 9 == suit }
            targets(pure, pairs = false, sequences = true)
            targets(pure, pairs = false, sequences = false)
            if (melds.isEmpty()) targets(pure, pairs = true, sequences = false)
        }
        // A declared quad already supplies fan. Concealed roots need all four retained copies
        // in a scorer-confirmed target; a triplet with no public fourth copy supplies no root route.
        if (melds.any { it.quad() }) targets(kinds, pairs = false, sequences = true)
        for (kind in kinds) if (held[kind] >= 3 && held[kind] + live[kind] == 4)
            targets(kinds, pairs = false, sequences = true, root = kind)
        return Evaluation(progress, utility)
    }
}

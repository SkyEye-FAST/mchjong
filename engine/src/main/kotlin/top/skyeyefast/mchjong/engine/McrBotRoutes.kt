package top.skyeyefast.mchjong.engine

import kotlin.math.exp
import kotlin.math.ln

/** Incomplete-hand evidence, never a sum of hypothetical fan. Scoped to one decision. */
internal class McrBotRoutes(private val owner: Int, private val seatWind: Int, private val roundWind: Int) {
    data class Route(val name: String, val distance: Int, val feasibility: Double)
    data class Assessment(val routes: List<Route>, val qualifyingCopies: Int) {
        val best: Double get() = routes.maxOfOrNull { it.feasibility } ?: 0.0
        fun retention(name: String?): Double = routes.firstOrNull { it.name == name }?.feasibility ?: 0.0
    }

    private data class Template(val name: String, val required: List<IntArray>, val allowed: List<IntArray>,
                                val pairs: List<Int>, val types: Boolean = false)
    private data class Fit(val need: IntArray, val last: Int, val missing: Int, val scarcity: Double)
    private data class ScoreKey(val counts: List<Int>, val melds: List<Meld>, val winningKind: Int)
    private val qualified = HashMap<ScoreKey, Boolean>()
    private val templateCache = HashMap<Boolean, List<Template>>()
    private val context = McrWinContext(McrWinContext.Method.DISCARD, seatWind, roundWind,
        false, McrWinContext.KongWin.NONE, false, 0)

    fun assess(hand: List<Int>, melds: List<Meld>, known: Set<Int>, progress: McrHandAnalyzer.Progress): Assessment {
        val held = IntArray(34)
        hand.forEach { held[Tile.kind(it)]++ }
        val live = IntArray(34) { 4 }
        known.forEach { if (it >= 0 && it < 136) live[Tile.kind(it)]-- }
        val fixed = melds.map { meld ->
            if (meld.type() == Meld.Type.SEQUENCE) meld.tiles().map(Tile::kind).sorted().toIntArray()
            else IntArray(3) { meld.kind() }
        }
        val routes = linkedMapOf<String, Route>()
        fun add(name: String, distance: Int, availability: Double) {
            val route = Route(name, distance, exp(-0.42 * distance) * availability)
            if (route.feasibility > (routes[name]?.feasibility ?: -1.0)) routes[name] = route
        }

        // The library owns special-form shanten. Copy-consuming targets add a stock
        // bound: unavailable singles may require a different pair/knitted layout.
        for (form in progress.forms) {
            val target = when (form.form) {
                McrHandAnalyzer.Form.SEVEN_PAIRS -> {
                    val need = IntArray(34)
                    var possible = true
                    repeat(7) {
                        val next = KINDS.filter { need[it] + 2 <= held[it] + live[it] }
                            .minWithOrNull(compareBy<Int> { maxOf(0, need[it] + 2 - held[it]) - maxOf(0, need[it] - held[it]) }
                                .thenBy { if (live[it] > 0) ln(4.0 / live[it]) else 0.0 })
                        if (next == null) possible = false else need[next] += 2
                    }
                    if (possible) fit(need, -1, held, live) else null
                }
                McrHandAnalyzer.Form.THIRTEEN_ORPHANS -> ORPHANS.mapNotNull { pair ->
                    val need = IntArray(34).apply { ORPHANS.forEach { this[it] = 1 }; this[pair]++ }
                    fit(need, -1, held, live)
                }.minWithOrNull(FIT_ORDER)
                McrHandAnalyzer.Form.HONORS_AND_KNITTED_TILES -> KNITTED.mapNotNull { kinds ->
                    val chosen = (kinds + HONORS).filter { held[it] + live[it] > 0 }
                        .sortedWith(compareByDescending<Int> { held[it] > 0 }.thenByDescending { live[it] }).take(14)
                    if (chosen.size < 14) null else fit(IntArray(34).apply { chosen.forEach { this[it] = 1 } }, -1, held, live)
                }.minWithOrNull(FIT_ORDER)
                // Complete knitted targets below also fit their extra group and head.
                McrHandAnalyzer.Form.KNITTED_STRAIGHT, McrHandAnalyzer.Form.REGULAR -> null
            }
            if (target != null) add(form.form.name, maxOf(form.shanten, target.missing - 1), exp(-0.18 * target.scarcity))
        }
        // Seven stars needs all seven distinct honors, in addition to a knitted suit layout.
        if (melds.isEmpty() && HONORS.all { held[it] + live[it] > 0 }) {
            for (knitted in KNITTED) {
                val available = knitted.filter { held[it] + live[it] > 0 }
                    .sortedByDescending { held[it] }.take(7)
                if (available.size == 7) {
                    val missing = (available + HONORS).count { held[it] == 0 }
                    add("SEVEN_STARS", (missing - 1).coerceAtLeast(0), availability(
                        IntArray(34).apply { (available + HONORS).forEach { this[it] = 1 } }, held, live))
                }
            }
        }

        val templates = templates(melds)
        // Compulsory structures compete by their copy-consuming deficit. Only fitting
        // targets can be scored; a fixed incompatible chow cannot become a pung.
        for (template in templates) {
            val todo = template.required.toMutableList()
            for (group in fixed) {
                val match = todo.indexOfFirst { it.contentEquals(group) }
                if (match >= 0) todo.removeAt(match)
            }
            if (fixed.any { group -> template.allowed.none { it.contentEquals(group) } }) continue
            val slots = 4 - fixed.size - todo.size
            if (slots < 0) continue
            val initial = IntArray(34)
            todo.forEach { group -> group.forEach { initial[it]++ } }
            val start = fit(initial, -1, held, live) ?: continue
            // Targets farther than eight missing draws have negligible evidence. This
            // bound also avoids expensive completion of unrelated distant templates.
            if (start.missing > 8) continue
            var beam = listOf(start)
            repeat(slots) {
                val next = ArrayList<Fit>()
                for (state in beam) for (index in maxOf(0, state.last)..template.allowed.lastIndex) {
                    val need = state.need.clone()
                    template.allowed[index].forEach { need[it]++ }
                    val candidate = fit(need, index, held, live) ?: continue
                    if (candidate.missing <= 8) next.add(candidate)
                }
                // A small beam retains alternatives without enumerating every regular
                // winning hand. This is a route heuristic, not another shanten solver.
                beam = next.sortedWith(FIT_ORDER).distinctBy { it.need.toList() }.take(8)
            }
            val complete = ArrayList<Fit>()
            for (state in beam) for (pair in template.pairs) {
                val need = state.need.clone()
                need[pair] += 2
                val candidate = fit(need, state.last, held, live) ?: continue
                if (candidate.missing > 8) continue
                if (template.types) {
                    val all = need.clone()
                    fixed.forEach { group -> group.forEach { all[it]++ } }
                    if ((0..2).any { suit -> (suit * 9 until suit * 9 + 9).none { all[it] > 0 } }
                        || (27..30).none { all[it] > 0 } || (31..33).none { all[it] > 0 }) continue
                }
                complete.add(candidate)
            }
            for (target in complete.sortedWith(FIT_ORDER).take(4)) {
                val distance = (target.missing - 1).coerceAtLeast(0)
                val viability = exp(-0.42 * distance) * exp(-0.18 * target.scarcity)
                if (viability <= (routes[template.name]?.feasibility ?: 0.0)) continue
                if (target.need.indices.filter { target.need[it] > held[it] }
                        .all { qualifies(target.need, melds, it) })
                    add(template.name, distance, exp(-0.18 * target.scarcity))
            }
        }

        var winningCopies = 0
        if (progress.shanten == 0) for (kind in progress.effectiveKinds) {
            val tile = (kind * 4 until kind * 4 + 4).firstOrNull { it !in known } ?: continue
            // Ordinary discard and ordinary self-draw are both real ways to qualify.
            // Flowers and speculative last-tile/kong bonuses never rescue a target.
            val ron = McrHandAnalyzer.score(hand, melds, owner, tile, context)
            val tsumo = McrHandAnalyzer.score(hand, melds, owner, tile,
                McrWinContext(McrWinContext.Method.SELF_DRAW, seatWind, roundWind,
                    false, McrWinContext.KongWin.NONE, false, 0))
            if (ron?.meetsMinimum() == true || tsumo?.meetsMinimum() == true) winningCopies += live[kind]
        }
        if (winningCopies > 0) add("QUALIFYING_WAIT", 0, 0.75 + 0.25 * (winningCopies / 8.0).coerceAtMost(1.0))
        return Assessment(routes.values.toList(), winningCopies)
    }

    private fun fit(need: IntArray, last: Int, held: IntArray, live: IntArray): Fit? {
        var missing = 0
        var scarcity = 0.0
        for (kind in need.indices) {
            if (need[kind] > held[kind] + live[kind]) return null
            val deficit = maxOf(0, need[kind] - held[kind])
            missing += deficit
            if (deficit > 0) scarcity += deficit * ln(4.0 / live[kind])
        }
        return Fit(need, last, missing, scarcity)
    }

    private fun availability(need: IntArray, held: IntArray, live: IntArray): Double =
        fit(need, -1, held, live)?.let { exp(-0.18 * it.scarcity) } ?: 0.0

    private fun qualifies(need: IntArray, melds: List<Meld>, winningKind: Int): Boolean =
        qualified.getOrPut(ScoreKey(need.toList(), melds, winningKind)) {
            val fixedIds = melds.flatMap { it.tiles() }.toSet()
            val tiles = ArrayList<Int>()
            for (kind in need.indices) tiles.addAll((kind * 4 until kind * 4 + 4).filter { it !in fixedIds }.take(need[kind]))
            // Score the tile that the route must actually acquire, never an arbitrary
            // retained head that could invent a single/closed wait point at eight fan.
            val winning = tiles.removeAt(tiles.indexOfFirst { Tile.kind(it) == winningKind })
            McrHandAnalyzer.score(tiles, melds, owner, winning, context)?.meetsMinimum() == true
        }

    private fun templates(melds: List<Meld>): List<Template> = templateCache.getOrPut(melds.all { it.closed() }) {
        val result = ArrayList<Template>()
        fun add(name: String, required: List<IntArray> = emptyList(), allowed: List<IntArray> = GROUPS,
                pairs: List<Int> = KINDS, types: Boolean = false) {
            result.add(Template(name, required, allowed, pairs, types))
        }
        val closed = melds.all { it.closed() }
        val valueHonors = (listOf(seatWind, roundWind) + (31..33)).distinct()
        val outsideGroups = GROUPS.filter { group -> group.any { it in ORPHANS } }
        if (closed) add("OUTSIDE", allowed = outsideGroups, pairs = ORPHANS)
        if (closed) for (honor in valueHonors) add("OUTSIDE", listOf(pung(honor)), outsideGroups, ORPHANS)
        for ((index, first) in valueHonors.withIndex()) for (second in valueHonors.drop(index + 1))
            add("OUTSIDE", listOf(pung(first), pung(second)), outsideGroups, ORPHANS)
        for (knitted in KNITTED)
            add("KNITTED_STRAIGHT", knitted.chunked(3).map { it.toIntArray() })
        if (closed) add("ALL_PUNGS", allowed = PUNGS)
        for (honor in valueHonors) add("ALL_PUNGS", listOf(pung(honor)), PUNGS)
        add("MIXED_TERMINALS", allowed = PUNGS.filter { it[0] in ORPHANS }, pairs = ORPHANS)
        for (suit in 0..2) {
            val pure = GROUPS.filter { it.all { kind -> kind < 27 && kind / 9 == suit } }
            val half = GROUPS.filter { it.all { kind -> kind >= 27 || kind / 9 == suit } }
            val purePairs = (suit * 9 until suit * 9 + 9).toList()
            val halfPairs = purePairs + HONORS
            add("FULL_FLUSH_$suit", allowed = pure, pairs = purePairs)
            if (closed) add("HALF_FLUSH_$suit", allowed = half, pairs = halfPairs)
            for (honor in valueHonors) add("HALF_FLUSH_$suit", listOf(pung(honor)), half, halfPairs)
            add("HALF_FLUSH_PUNGS_$suit", allowed = half.filter { it[0] == it[1] }, pairs = halfPairs)
            val outside = half.filter { group -> group.any { it in ORPHANS } }
            add("OUTSIDE_HALF_FLUSH_$suit", allowed = outside, pairs = ORPHANS.filter { it >= 27 || it / 9 == suit })
            add("PURE_STRAIGHT_$suit", listOf(chow(suit, 0), chow(suit, 3), chow(suit, 6)))
            for (start in 0..6) add("PURE_TRIPLE_CHOW_$suit", List(3) { chow(suit, start) })
            for (step in 1..2) for (start in 0..6 - 2 * step)
                add("PURE_SHIFTED_CHOWS_$suit", List(3) { chow(suit, start + it * step) })
            for (start in 0..6) add("PURE_SHIFTED_PUNGS_$suit", List(3) { pung(suit * 9 + start + it) })
        }
        for (start in 0..6) add("MIXED_TRIPLE_CHOW", List(3) { chow(it, start) })
        for (permutation in PERMUTATIONS) {
            add("MIXED_STRAIGHT", List(3) { chow(permutation[it], it * 3) })
            for (start in 0..4) {
                val groups = List(3) { chow(permutation[it], start + it) }
                if (closed) add("MIXED_SHIFTED_CHOWS", groups)
                for (honor in valueHonors) add("MIXED_SHIFTED_CHOWS", groups + listOf(pung(honor)))
            }
        }
        if (closed) add("ALL_TYPES", types = true)
        for (dragon in 31..33) add("ALL_TYPES", listOf(pung(dragon)), types = true)
        for ((name, ranks) in listOf("UPPER_TILES" to 6..8, "MIDDLE_TILES" to 3..5, "LOWER_TILES" to 0..2)) {
            val kinds = KINDS.filter { it < 27 && it % 9 in ranks }
            add(name, allowed = GROUPS.filter { group -> group.all { it in kinds } }, pairs = kinds)
        }
        result
    }

    companion object {
        private val KINDS = (0..33).toList()
        private val HONORS = (27..33).toList()
        private val ORPHANS = listOf(0, 8, 9, 17, 18, 26) + HONORS
        private fun pung(kind: Int) = IntArray(3) { kind }
        private fun chow(suit: Int, start: Int) = IntArray(3) { suit * 9 + start + it }
        private val PUNGS = KINDS.map(::pung)
        private val GROUPS = PUNGS + (0..2).flatMap { suit -> (0..6).map { chow(suit, it) } }
        private val PERMUTATIONS = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2),
            listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        private val KNITTED = PERMUTATIONS.map { suits -> (0..2).flatMap { lane ->
            (0..2).map { suits[lane] * 9 + lane + it * 3 }
        } }
        private val FIT_ORDER = compareBy<Fit> { it.missing }.thenBy { it.scarcity }
    }
}

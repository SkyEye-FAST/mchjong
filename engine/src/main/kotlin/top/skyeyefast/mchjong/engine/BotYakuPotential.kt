package top.skyeyefast.mchjong.engine

import kotlin.math.exp

/** Incomplete-hand evidence, not a scorer or a second shanten implementation.
 * Targets consume actual copies; alternatives compete instead of adding their han. */
internal class BotYakuPotential(private val rules: RuleConfig, private val yakuhai: (Int) -> Int) {
    companion object {
        private val SEQUENCES = (0 until 27).filter { it % 9 <= 6 }.map { intArrayOf(it, it + 1, it + 2) }
        private val TERMINALS = (0 until 27).filter { it % 9 == 0 || it % 9 == 8 }
        private val ORPHANS = TERMINALS + (27..33)
        private val OUTER_SEQUENCES = SEQUENCES.filter { it[0] % 9 == 0 || it[0] % 9 == 6 }
        private val OUTSIDE_GROUPS = OUTER_SEQUENCES + ORPHANS.map { kind -> IntArray(3) { kind } }
        private val TERMINAL_GROUPS = OUTER_SEQUENCES + TERMINALS.map { kind -> IntArray(3) { kind } }
        private val COMPANIONS = setOf("tanyao", "yakuhai", "honitsu", "chinitsu")
        private val TRIPLETS = (0 until 34).map { kind -> IntArray(3) { kind } }
        private val DOUBLE_SEQUENCES = SEQUENCES.map { listOf(it, it) }
        private val THREE_COLORS = (0..6).map { start -> (0..2).map { suit -> IntArray(3) { suit * 9 + start + it } } }
        private val STRAIGHTS = (0..2).map { suit -> (0..2).map { block -> IntArray(3) { suit * 9 + block * 3 + it } } }
        private val PROGRESS = DoubleArray(65) { exp(-0.65 * (it / 2.0)) }
    }
    @JvmRecord
    data class Route(
        val name: String,
        val family: String,
        val missing: Double,
        val progress: Double,
        val han: Double,
        val suit: Int,
        val simpleCompatible: Boolean,
    ) {
        fun value(): Double = progress * han
    }

    @JvmRecord
    data class Assessment(val routes: List<Route>, val plan: String, val han: Double, val attainableHan: Double) {
        companion object {
            val EMPTY = Assessment(emptyList(), "none", 0.0, 0.0)
        }
    }

    private data class Key(val counts: List<Int>, val melds: List<String>, val live: List<Int>)
    private val cache = HashMap<Key, Assessment>()

    fun assess(state: BotAnalysis.State, remaining: IntArray): Assessment {
        val counts = IntArray(34)
        state.hand().forEach { counts[Tile.kind(it)]++ }
        val live = IntArray(34) { remaining[it] + remaining[it + 34] }
        val key = Key(counts.toList(), state.melds().map { it.libraryNotation() }.sorted(), live.toList())
        return cache.getOrPut(key) { assess(counts, state.melds(), live) }
    }

    private fun assess(counts: IntArray, melds: List<Meld>, live: IntArray): Assessment {
        val closed = melds.all { it.closed() }
        val fixed = melds.map { meld ->
            if (meld.type() == Meld.Type.CHI) meld.tiles().map(Tile::kind).sorted().toIntArray()
            else IntArray(3) { meld.kind() }
        }
        val all = counts.clone()
        fixed.forEach { group -> group.forEach { all[it]++ } }
        val routes = linkedMapOf<String, Route>()
        fun add(name: String, family: String, missing: Double, han: Int, suit: Int = -1, simpleCompatible: Boolean = true) {
            if (!missing.isFinite()) return
            val distance = missing.coerceAtLeast(0.0)
            val half = (distance * 2).toInt()
            val progress = if (half in PROGRESS.indices && half / 2.0 == distance) PROGRESS[half] else exp(-0.65 * distance)
            if (progress * han > (routes[name]?.value() ?: -1.0))
                routes[name] = Route(name, family, distance, progress, han.toDouble(), suit, simpleCompatible)
        }

        // Match compulsory melds first. Fixed, incompatible melds consume slots;
        // neither another interpretation of a called meld nor a fifth copy is available.
        val need = IntArray(34)
        fun target(groups: List<IntArray>, pair: Int = -1): Double {
            val todo = if (fixed.isEmpty()) groups else {
                val unmatched = groups.toMutableList()
                for (group in fixed) {
                    val match = unmatched.indexOfFirst { it.contentEquals(group) }
                    if (match >= 0) unmatched.removeAt(match)
                    else if (pair >= 0 && pair in group) return Double.POSITIVE_INFINITY
                }
                unmatched
            }
            if (todo.size + melds.size > 4) return Double.POSITIVE_INFINITY
            need.fill(0)
            todo.forEach { group -> group.forEach { need[it]++ } }
            if (pair >= 0) need[pair] += 2
            var missing = 0.0
            for (kind in need.indices) {
                if (need[kind] > counts[kind] + live[kind]) return Double.POSITIVE_INFINITY
                missing += maxOf(0, need[kind] - counts[kind])
            }
            return missing
        }

        val sequences = SEQUENCES
        if (melds.isEmpty()) {
            // Copy-consuming sequence fit rewards complete groups and live two-sided
            // fragments; value-honor pairs and declared quads cannot support pinfu.
            add("pinfu", "sequence", fit(counts, live, sequences, (0 until 34).filter { yakuhai(it) == 0 }, true), 1)
            for (groups in DOUBLE_SEQUENCES) {
                val start = groups[0][0]
                add("iipeikou", "sequence", target(groups), 1, start / 9, start % 9 in 1..5)
            }
        }
        for (start in 0..6) {
            val groups = THREE_COLORS[start]
            add("sanshoku", "sequence", target(groups), if (closed) 2 else 1, simpleCompatible = start in 1..5)
        }
        for (suit in 0..2) {
            val groups = STRAIGHTS[suit]
            add("ittsu", "sequence", target(groups), if (closed) 2 else 1, suit)
        }
        if ((closed || rules.kuitan()) && fixed.all { group -> group.none(Tile::terminalOrHonor) }) {
            add("tanyao", "mixed", all.indices.filter(Tile::terminalOrHonor).sumOf { all[it] }.toDouble(), 1)
        }
        // Each honor is a separate alternative. Established honor sets can coexist;
        // an unsupported isolated honor is not treated as an assured open yaku.
        var established = 0
        for (kind in 27..33) if (all[kind] >= 3) established += yakuhai(kind)
        if (established > 0) add("yakuhai", "mixed", 0.0, established)
        for (kind in 27..33) if (yakuhai(kind) > 0 && counts[kind] > 0 && all[kind] < 3) {
            add("yakuhai", "mixed", target(listOf(TRIPLETS[kind])), established + yakuhai(kind))
        }

        for (pure in listOf(false, true)) {
            val groups = if (pure) TERMINAL_GROUPS else OUTSIDE_GROUPS
            if (fixed.all { group -> groups.any { it.contentEquals(group) } } && fixed.size < 4) {
                val distance = fit(counts, live, groups, if (pure) TERMINALS else ORPHANS, false, 4 - fixed.size,
                    fixed.none { it[0] != it[1] })
                add(if (pure) "junchan" else "chanta", "outside", distance +
                    if (!pure && all.slice(27..33).sum() == 0) 1.0 else 0.0, if (pure) { if (closed) 3 else 2 } else { if (closed) 2 else 1 })
            }
        }
        if (fixed.all { it[0] == it[1] }) {
            add("toitoi", "triplet", tripletDistance(counts, live, 4 - fixed.size), 2)
        }
        val concealed = melds.count { it.closed() }
        if (melds.size - concealed <= 1) {
            val need = 3 - concealed
            val triples = counts.indices.filter { counts[it] + live[it] >= 3 }
                .sortedWith(compareByDescending<Int> { minOf(3, counts[it]) }.thenBy { it }).take(maxOf(0, need))
            if (triples.size == maxOf(0, need)) {
                add("sanankou", "triplet", target(triples.map { TRIPLETS[it] }), 2)
            }
        }
        for (pair in Tile.WHITE..Tile.RED) {
            val dragons = (Tile.WHITE..Tile.RED).filter { it != pair }.map { TRIPLETS[it] }
            add("shousangen", "triplet", target(dragons, pair), 4) // two required dragon yakuhai included
        }
        for (suit in 0..2) {
            val suited = (suit * 9 until suit * 9 + 9).sumOf { all[it] }
            for (pure in listOf(false, true)) {
                fun allowed(kind: Int): Boolean = kind < 27 && kind / 9 == suit || !pure && kind >= 27
                if (fixed.any { group -> group.any { !allowed(it) } }) continue
                val off = all.indices.filter { !allowed(it) }.sumOf { all[it] }
                add(if (pure) "chinitsu" else "honitsu", "flush", off + maxOf(0, 7 - suited) * 0.5,
                    if (pure) { if (closed) 6 else 5 } else { if (closed) 3 else 2 }, suit)
            }
        }
        if (melds.isEmpty()) {
            val pairs = counts.indices.filter { counts[it] + live[it] >= 2 }
                .map { minOf(2, counts[it]) }.sortedDescending().take(7)
            if (pairs.size == 7) add("chiitoitsu", "pairs", (13 - pairs.sum()).toDouble(), 2)
            val orphans = ORPHANS
            if (orphans.all { counts[it] + live[it] >= 1 } && orphans.any { counts[it] + live[it] >= 2 }) {
                val covered = orphans.count { counts[it] > 0 } + if (orphans.any { counts[it] >= 2 }) 1 else 0
                add("kokushi", "orphans", (13 - covered).toDouble(), 13)
            }
        }

        // One primary route and at most one compatible companion. The companion
        // shares the weaker progress and is discounted, not independently summed.
        // In particular, competing sequence templates and triplet/pair structures
        // never accumulate. Flush suits and outside/simple restrictions must agree.
        var best = 0.0
        var plan = "none"
        var attainable = 0.0
        val companions = routes.values.filter { it.name in COMPANIONS }
        for (route in routes.values) {
            var value = route.value()
            var name = route.name
            var legalHan = route.han
            val side = companions.filter { companion(route, it) }
                .maxByOrNull { minOf(route.progress, it.progress) * it.han }
            if (side != null) {
                value += 0.5 * minOf(route.progress, side.progress) * side.han
                name += "+${side.name}"
                if (side.progress >= 0.25) legalHan += side.han
            }
            if (route.progress >= 0.25) attainable = maxOf(attainable, legalHan)
            if (value > best) { best = value; plan = name }
        }
        return Assessment(routes.values.toList(), plan, best, attainable)
    }

    private fun companion(a: Route, b: Route): Boolean {
        if (a.name == b.name || a.family == "orphans" || b.name !in COMPANIONS) return false
        if (a.name == "shousangen" && b.name == "yakuhai") return false // included above
        if (b.name == "yakuhai") return when (a.name) {
            "pinfu", "tanyao", "junchan", "chinitsu", "chiitoitsu" -> false
            else -> true
        }
        if (b.name == "tanyao") return a.simpleCompatible && when (a.name) {
            "ittsu", "chanta", "junchan", "yakuhai", "shousangen", "honitsu" -> false
            else -> true
        }
        if (a.family == "flush" || a.name == "sanshoku") return false
        if (a.suit >= 0 && a.suit != b.suit) return false
        return if (b.name == "honitsu") a.name != "tanyao" && a.name != "junchan"
            else a.name != "yakuhai" && a.name != "shousangen" && a.name != "chanta"
    }

    /** Exact copy allocation for the pair/triplet roles, not regular-hand shanten. */
    private fun tripletDistance(counts: IntArray, live: IntArray, groups: Int): Double {
        var dp = IntArray((groups + 1) * 2) { -100 }
        var next = IntArray(dp.size)
        dp[0] = 0
        for (kind in counts.indices) {
            dp.copyInto(next)
            for (n in 0..groups) for (pair in 0..1) {
                val index = n * 2 + pair
                if (dp[index] < 0) continue
                if (n < groups && counts[kind] + live[kind] >= 3)
                    next[index + 2] = maxOf(next[index + 2], dp[index] + minOf(3, counts[kind]))
                if (pair == 0 && counts[kind] + live[kind] >= 2)
                    next[index + 1] = maxOf(next[index + 1], dp[index] + minOf(2, counts[kind]))
            }
            val previous = dp
            dp = next
            next = previous
        }
        return if (dp.last() < 0) Double.POSITIVE_INFINITY else (groups * 3 + 1 - dp.last()).coerceAtLeast(0).toDouble()
    }

    /** A small, copy-consuming fit, tried with both sequence/triplet tie orders.
     * This is a graded structural deficit, not an assertion of yaku or exact distance. */
    private fun fit(counts: IntArray, live: IntArray, groups: List<IntArray>, pairs: List<Int>,
                    pinfu: Boolean, slots: Int = 4, needSequence: Boolean = false): Double {
        var best = Double.POSITIVE_INFINITY
        val held = IntArray(34)
        val supply = IntArray(34)
        for (pair in pairs) {
            if (counts[pair] == 0 || counts[pair] + live[pair] < 2) continue
            for (reverse in listOf(false, true)) {
                counts.copyInto(held)
                for (kind in supply.indices) supply[kind] = counts[kind] + live[kind]
                val usedPair = minOf(2, held[pair])
                held[pair] -= usedPair
                supply[pair] -= 2
                var covered = usedPair.toDouble()
                var sequence = false
                var ryanmen = false
                repeat(slots) {
                    var selected: IntArray? = null
                    var maximum = -1.0
                    for (index in groups.indices) {
                        val group = groups[if (reverse) groups.lastIndex - index else index]
                        val triplet = group[0] == group[1]
                        if (triplet && supply[group[0]] < 3 || !triplet &&
                            (supply[group[0]] < 1 || supply[group[1]] < 1 || supply[group[2]] < 1)) continue
                        var gain = if (triplet) minOf(3, held[group[0]]).toDouble() else
                            (if (held[group[0]] > 0) 1.0 else 0.0) +
                                (if (held[group[1]] > 0) 1.0 else 0.0) + (if (held[group[2]] > 0) 1.0 else 0.0)
                        if (pinfu && gain == 2.0 && !triplet) {
                            val twoSided = (held[group[0]] == 0 && group[0] % 9 < 6) || (held[group[2]] == 0 && group[0] % 9 > 0)
                            if (!twoSided) gain -= 0.45
                        }
                        if (gain > maximum) { maximum = gain; selected = group }
                    }
                    val group = selected
                    if (group != null) {
                        if (group[0] != group[1]) {
                            sequence = true
                            if (maximum == 2.0) ryanmen = true
                        }
                        covered += maximum
                        group.forEach { if (held[it] > 0) held[it]--; supply[it]-- }
                    }
                }
                val distance = maxOf(0.0, slots * 3 + 1 - covered) +
                    (if (pinfu && !ryanmen) 0.35 else 0.0) + (if (needSequence && !sequence) 2.0 else 0.0)
                best = minOf(best, distance)
            }
        }
        // Without a retained head the route still has gradual, weaker evidence.
        return if (best.isFinite()) best else (slots + 2).toDouble()
    }
}

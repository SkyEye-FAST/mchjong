package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.TaiwanAction.Type.*
import kotlin.math.exp
import kotlin.math.ln

/** Deterministic built-in opponent. The recipient view is its entire information boundary. */
object TaiwanBot {
    private data class Evaluation(val shanten: Double, val remaining: Double, val waits: Double,
                                  val tai: Double, val potential: Double, val open: Int)

    @JvmStatic
    fun choose(view: TaiwanView): Int {
        val actions = view.actions()
        val owner = view.recipient()
        if (owner < 0 || actions.isEmpty()) return -1
        actions.indexOfFirst { it.type() == WIN }.takeIf { it >= 0 }?.let { return it }
        if (actions.all { it.type() == PASS }) return 0
        val own = view.seats()[owner]
        val rules = view.rules().restore()
        val known = linkedSetOf<Int>().apply {
            addAll(own.concealed())
            addAll(own.melds().flatMap { it.tiles() })
            for (seat in view.seats()) {
                addAll(seat.river())
                addAll(seat.melds().filterNot { it.closed() }.flatMap { it.tiles() })
            }
            view.focus()?.let { add(it.tile()) }
        }
        val wind = Tile.EAST + (owner - view.opening().dealer() + 4) % 4
        fun context(method: TaiwanWinContext.Method, ready: TaiwanWinContext.Ready,
                    origin: TaiwanWinContext.DrawOrigin = TaiwanWinContext.DrawOrigin.ORDINARY) = TaiwanWinContext(
            method, wind, view.roundWind(), (owner - view.opening().dealer() + 4) % 4 + 1,
            own.flowers().toSet(), origin, false,
            TaiwanWinContext.Opening.NONE, ready)
        // Alternatives compete; only completed live waits claim scorer-awarded tai.
        fun potential(hand: List<Int>, melds: List<Meld>, visible: Set<Int>, shanten: Int): Double {
            val counts = (hand + melds.flatMap { it.tiles() }).groupingBy(Tile::kind).eachCount()
            val held = IntArray(34); hand.forEach { held[Tile.kind(it)]++ }
            val available = IntArray(34) { kind -> (kind * 4..<kind * 4 + 4).count { it !in visible } }
            fun value(pattern: TaiwanRules.Pattern) = rules.values.getValue(pattern).toDouble()
            val closed = melds.none { !it.closed() }
            val concealment = if (closed) maxOf(value(TaiwanRules.Pattern.CONCEALED), value(TaiwanRules.Pattern.CONCEALED_SELF_DRAW)) else 0.0
            val honors = listOf(Tile.RED to TaiwanRules.Pattern.RED_DRAGON, Tile.GREEN to TaiwanRules.Pattern.GREEN_DRAGON,
                Tile.WHITE to TaiwanRules.Pattern.WHITE_DRAGON, wind to TaiwanRules.Pattern.SEAT_WIND,
                view.roundWind() to TaiwanRules.Pattern.ROUND_WIND).maxOf { (kind, pattern) ->
                val count = counts.getOrDefault(kind, 0)
                if (count == 0 || count + available[kind] < 3) 0.0 else value(pattern) * exp(-0.7 * (3 - count).coerceAtLeast(0))
            }
            var pungs = 0.0
            if (melds.none { it.type() == Meld.Type.SEQUENCE }) for (head in 0..33) {
                if (held[head] + available[head] < 2) continue
                val groups = (0..33).filter { it != head && held[it] + available[it] >= 3 }
                    .sortedWith(compareBy<Int> { (3 - held[it]).coerceAtLeast(0) }.thenByDescending { available[it] }).take(5 - melds.size)
                if (groups.size != 5 - melds.size) continue
                val missing = (2 - held[head]).coerceAtLeast(0) + groups.sumOf { (3 - held[it]).coerceAtLeast(0) }
                val distance = (missing - 1).coerceAtLeast(0)
                if (distance > shanten + 2) continue
                // The head and five pungs consume distinct copies; scarce indispensable copies discount the route.
                val scarcity = groups.sumOf { if (held[it] >= 3) 0.0 else (3 - held[it]) * ln(4.0 / available[it]) } +
                    if (held[head] >= 2) 0.0 else (2 - held[head]) * ln(4.0 / available[head])
                pungs = maxOf(pungs, value(TaiwanRules.Pattern.ALL_TRIPLETS) * exp(-0.5 * distance - 0.18 * scarcity))
            }
            val flush = (0..2).maxOf { suit ->
                fun route(honors: Boolean): Double {
                    fun allowed(kind: Int) = kind < 27 && kind / 9 == suit || honors && kind >= 27
                    if (melds.any { !allowed(it.kind()) }) return 0.0
                    val kinds = (0..33).filter(::allowed)
                    val required = 17 - 3 * melds.size
                    val retained = kinds.sumOf { held[it] }
                    if (kinds.sumOf { held[it] + available[it] } < required) return 0.0
                    if (honors && (27..33).none { counts.getOrDefault(it, 0) > 0 || available[it] >= 2 }) return 0.0
                    val distance = (required - retained - 1).coerceAtLeast(0)
                    if (distance > minOf(3, shanten + 2)) return 0.0
                    data class Fit(val need: IntArray, val missing: Int, val last: Int)
                    fun fit(need: IntArray, last: Int): Fit? {
                        if (need.indices.any { need[it] > held[it] + available[it] }) return null
                        val missing = need.indices.sumOf { (need[it] - held[it]).coerceAtLeast(0) }
                        return if (missing <= minOf(4, shanten + 3)) Fit(need, missing, last) else null
                    }
                    val groups = kinds.map { listOf(it, it, it) } + kinds.filter { it < 27 && it % 9 <= 6 }.map { listOf(it, it + 1, it + 2) }
                    // A small beam reserves the head and consumes actual stock for five compatible groups.
                    var beam = kinds.mapNotNull { head -> fit(IntArray(34).also { it[head] = 2 }, -1) }
                    repeat(5 - melds.size) {
                        beam = beam.flatMap { state -> (state.last.coerceAtLeast(0)..groups.lastIndex).mapNotNull { index ->
                            fit(state.need.clone().also { need -> groups[index].forEach { need[it]++ } }, index)
                        } }.sortedWith(compareBy<Fit> { it.missing }.thenBy { it.last }).distinctBy { it.need.toList() }.take(6)
                    }
                    val target = beam.filter { !honors || (27..33).any { kind -> it.need[kind] > 0 || counts.getOrDefault(kind, 0) > 0 } }
                        .minByOrNull { it.missing } ?: return 0.0
                    val scarcity = target.need.indices.sumOf { kind -> if (target.need[kind] <= held[kind]) 0.0 else
                        (target.need[kind] - held[kind]) * ln(4.0 / available[kind]) }
                    return value(if (honors) TaiwanRules.Pattern.HALF_FLUSH else TaiwanRules.Pattern.FULL_FLUSH) *
                        exp(-0.5 * (target.missing - 1).coerceAtLeast(0) - 0.18 * scarcity)
                }
                maxOf(route(false), route(true))
            }
            return minOf(rules.taiLimit?.toDouble() ?: Double.MAX_VALUE, maxOf(concealment, honors, pungs, flush))
        }
        data class Key(val hand: List<Int>, val melds: List<Meld>, val visible: Set<Int>, val ready: TaiwanWinContext.Ready)
        val cache = HashMap<Key, Evaluation>()
        fun evaluate(hand: List<Int>, melds: List<Meld>, visible: Set<Int> = known,
                     ready: TaiwanWinContext.Ready = own.ready(), analysis: TaiwanHandAnalyzer.Analysis? = null): Evaluation =
            cache.getOrPut(Key(hand.map(Tile::kind).sorted(), melds, visible, ready)) {
                val progress = analysis ?: TaiwanHandAnalyzer.analyze(hand, melds, owner, visible.toList())
                val live = progress.effectiveTiles.filter { it.remaining > 0 }
                val remaining = live.sumOf { it.remaining }
                var value = 0.0
                if (progress.shanten == 0) for (wait in live) {
                    val tile = (wait.kind * 4..<wait.kind * 4 + 4).first { it !in visible }
                    val scores = TaiwanWinContext.Method.entries.filter { it != TaiwanWinContext.Method.ROBBING_KONG }.mapNotNull {
                        TaiwanHandAnalyzer.score(hand, melds, owner, tile, context(it, ready), rules)?.tai
                    }
                    value += wait.remaining * (scores.average().takeUnless { it.isNaN() } ?: 0.0)
                }
                Evaluation(progress.shanten.toDouble(), remaining.toDouble(), if (progress.shanten == 0) live.size.toDouble() else 0.0,
                    if (remaining == 0) 0.0 else value / remaining, potential(hand, melds, visible, progress.shanten), melds.count { !it.closed() })
            }
        fun utility(evaluation: Evaluation): Double {
            val value = if (evaluation.shanten <= 0) evaluation.tai else evaluation.potential * exp(-0.25 * evaluation.shanten)
            return -2.0 * evaluation.shanten + 0.06 * evaluation.remaining + 0.16 * evaluation.waits +
                0.9 * ln(1.0 + value) - 0.08 * evaluation.open
        }
        val order = compareByDescending<Evaluation>(::utility).thenBy { it.shanten }.thenByDescending { it.remaining }
            .thenByDescending { it.waits }.thenByDescending { it.tai }.thenBy { it.open }
        fun best(candidates: List<Evaluation>): Evaluation {
            val fastest = candidates.minOf { it.shanten }
            val retreat = if (fastest <= 0 || view.drawable() < 24) 0 else 1
            return candidates.filter { it.shanten <= fastest + retreat }.minWith(order)
        }
        fun bestDiscard(hand: List<Int>, melds: List<Meld>, visible: Set<Int> = known): Evaluation {
            val extra = hand.last()
            return best(TaiwanHandAnalyzer.discards(hand - extra, melds, owner, extra, visible.toList()).map { discard ->
                evaluate(hand - hand.first { Tile.kind(it) == discard.kind }, melds, visible, analysis = discard.analysis)
            })
        }
        var selected = actions.indexOfFirst { it.type() == PASS }
        var best: Evaluation? = null
        val discards = actions.indices.filter { actions[it].type() == DISCARD || actions[it].type() == READY_DISCARD }
        val analyses = if (discards.isEmpty()) emptyMap() else {
            val extra = own.concealed().last()
            TaiwanHandAnalyzer.discards(own.concealed() - extra, own.melds(), owner, extra, known.toList()).associate { it.kind to it.analysis }
        }
        val fastest = analyses.values.minOfOrNull { it.shanten }
        val retreat = if (fastest == null || fastest <= 0 || view.drawable() < 24) 0 else 1
        for (index in discards) {
            val action = actions[index]
            val tile = action.tiles().single()
            if (analyses.getValue(Tile.kind(tile)).shanten > fastest!! + retreat) continue
            val ready = if (action.type() != READY_DISCARD) own.ready() else if (own.river().isEmpty() && view.seats().all { it.melds().isEmpty() }) {
                if (wind == Tile.EAST) TaiwanWinContext.Ready.HEAVENLY else TaiwanWinContext.Ready.EARTHLY
            } else TaiwanWinContext.Ready.ORDINARY
            val candidate = evaluate(own.concealed() - tile, own.melds(), ready = ready, analysis = analyses.getValue(Tile.kind(tile)))
            val comparison = best?.let { order.compare(candidate, it) } ?: -1
            if (comparison < 0 || comparison == 0 && (tile < actions[selected].tiles().single() ||
                    tile == actions[selected].tiles().single() && action.type() == READY_DISCARD)) {
                best = candidate; selected = index
            }
        }
        if (selected < 0) return -1
        var baseline = best ?: evaluate(own.concealed(), own.melds())
        for ((index, action) in actions.withIndex()) {
            if (action.type() !in listOf(CHOW, PONG, OPEN_KONG, CONCEALED_KONG, ADDED_KONG)) continue
            val hand = own.concealed() - action.tiles().toSet()
            val melds = own.melds().toMutableList()
            when (action.type()) {
                CONCEALED_KONG -> melds += Meld(Meld.Type.CONCEALED_QUAD, action.tiles(), owner, Tile.ABSENT)
                ADDED_KONG -> {
                    val pung = melds.indexOfFirst { it.type() == Meld.Type.TRIPLET && it.kind() == Tile.kind(action.tiles().single()) }
                    val old = melds[pung]
                    melds[pung] = Meld(Meld.Type.ADDED_QUAD, old.tiles() + action.tiles(), old.fromSeat(), old.calledTile())
                }
                else -> {
                    val focus = requireNotNull(view.focus())
                    val type = when (action.type()) { CHOW -> Meld.Type.SEQUENCE; PONG -> Meld.Type.TRIPLET; else -> Meld.Type.OPEN_QUAD }
                    melds += Meld(type, (action.tiles() + focus.tile()).sorted(), focus.seat(), focus.tile())
                }
            }
            val kong = action.type() !in listOf(CHOW, PONG)
            val candidate = if (!kong) bestDiscard(hand, melds) else {
                // First protect the guaranteed continuation that discards the replacement.
                // Then weight one replacement/best-discard step over public unseen ordinary copies.
                // Flowers are not assigned speculative awards; no actual wall identity is used.
                val guaranteed = evaluate(hand, melds)
                if (view.drawable() <= 1 || guaranteed.shanten > baseline.shanten) continue
                val outcomes = (0..33).mapNotNull { kind ->
                    val unseen = (kind * 4..<kind * 4 + 4).filter { it !in known }
                    if (unseen.isEmpty()) null else {
                        val tile = unseen.first()
                        val score = if (view.passedWin() == true && action.type() != ADDED_KONG) null else
                            TaiwanHandAnalyzer.score(hand, melds, owner, tile, context(TaiwanWinContext.Method.SELF_DRAW, own.ready(), TaiwanWinContext.DrawOrigin.KONG_REPLACEMENT), rules)
                        val evaluation = if (score != null) Evaluation(-1.0, 0.0, 0.0, score.tai.toDouble(), guaranteed.potential, guaranteed.open)
                            else bestDiscard(hand + tile, melds, known + tile)
                        unseen.size to evaluation
                    }
                }
                val total = outcomes.sumOf { it.first }.toDouble()
                if (total == 0.0) continue
                fun mean(field: (Evaluation) -> Double) = outcomes.sumOf { (copies, evaluation) -> copies * field(evaluation) } / total
                Evaluation(mean { it.shanten }, mean { it.remaining }, mean { it.waits }, mean { it.tai },
                    mean { it.potential }, guaranteed.open)
            }
            // Calls need clear speed/availability or live scoring gains; a heuristic route alone cannot buy opening.
            val improves = (candidate.shanten < baseline.shanten - if (kong) 0.2 + 0.1 * (candidate.open - baseline.open).coerceAtLeast(0) else 0.0) || candidate.shanten == baseline.shanten &&
                (candidate.remaining >= baseline.remaining + 2 || candidate.remaining == baseline.remaining &&
                    candidate.waits >= baseline.waits && candidate.tai > baseline.tai + 0.5)
            if (improves && order.compare(candidate, baseline) < 0) { baseline = candidate; selected = index }
        }
        return selected
    }
}

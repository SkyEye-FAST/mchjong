package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.TaiwanAction.Type.*

/** Deterministic built-in opponent. The recipient view is its entire information boundary. */
object TaiwanBot {
    private data class Evaluation(val shanten: Double, val remaining: Double, val waits: Double,
                                  val tai: Double, val potential: Double, val open: Int)
    private val order = compareBy<Evaluation> { it.shanten }.thenByDescending { it.remaining }
        .thenByDescending { it.waits }.thenByDescending { it.tai }.thenByDescending { it.potential }
        .thenBy { it.open }

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
        // These are modest support estimates, not claims of awarded tai. Live waits use the scorer below.
        fun potential(hand: List<Int>, melds: List<Meld>, visible: Set<Int>): Double {
            val counts = (hand + melds.flatMap { it.tiles() }).groupingBy(Tile::kind).eachCount()
            fun value(pattern: TaiwanRules.Pattern) = rules.values.getValue(pattern).toDouble()
            val closed = melds.none { !it.closed() }
            val concealment = if (closed) maxOf(value(TaiwanRules.Pattern.CONCEALED), value(TaiwanRules.Pattern.CONCEALED_SELF_DRAW)) else 0.0
            val honors = listOf(31 to TaiwanRules.Pattern.RED_DRAGON, 32 to TaiwanRules.Pattern.GREEN_DRAGON,
                33 to TaiwanRules.Pattern.WHITE_DRAGON, wind to TaiwanRules.Pattern.SEAT_WIND,
                view.roundWind() to TaiwanRules.Pattern.ROUND_WIND).sumOf { (kind, pattern) ->
                val count = counts.getOrDefault(kind, 0)
                val available = (kind * 4..<kind * 4 + 4).count { it !in visible }
                if (count + available < 3) 0.0 else value(pattern) * (count.coerceAtMost(3) / 3.0)
            }
            val triplets = counts.values.sumOf { it.coerceAtMost(3) / 3.0 } / 5.0
            val pungs = if (melds.none { it.type() == Meld.Type.SEQUENCE }) value(TaiwanRules.Pattern.ALL_TRIPLETS) * triplets else 0.0
            val flush = (0..2).maxOf { suit ->
                if (melds.any { it.kind() < 27 && it.kind() / 9 != suit }) 0.0 else {
                    val suited = counts.filterKeys { it < 27 && it / 9 == suit }.values.sum()
                    val total = counts.values.sum().toDouble()
                    maxOf(value(TaiwanRules.Pattern.FULL_FLUSH) * suited / total,
                        value(TaiwanRules.Pattern.HALF_FLUSH) * (suited + counts.filterKeys { it >= 27 }.values.sum()) / total)
                }
            }
            return minOf(rules.taiLimit?.toDouble() ?: Double.MAX_VALUE, maxOf(concealment + honors, pungs, flush))
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
                    if (remaining == 0) 0.0 else value / remaining, potential(hand, melds, visible), melds.count { !it.closed() })
            }
        fun bestDiscard(hand: List<Int>, melds: List<Meld>, visible: Set<Int> = known): Evaluation {
            val extra = hand.last()
            return TaiwanHandAnalyzer.discards(hand - extra, melds, owner, extra, visible.toList()).map { discard ->
                evaluate(hand - hand.first { Tile.kind(it) == discard.kind }, melds, visible, analysis = discard.analysis)
            }.minWith(order)
        }
        var selected = actions.indexOfFirst { it.type() == PASS }
        var best: Evaluation? = null
        val discards = actions.indices.filter { actions[it].type() == DISCARD || actions[it].type() == READY_DISCARD }
        val analyses = if (discards.isEmpty()) emptyMap() else {
            val extra = own.concealed().last()
            TaiwanHandAnalyzer.discards(own.concealed() - extra, own.melds(), owner, extra, known.toList()).associate { it.kind to it.analysis }
        }
        for (index in discards) {
            val action = actions[index]
            val tile = action.tiles().single()
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

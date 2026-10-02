package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.McrAction.Type.*

/** One deterministic policy. Its entire information boundary is the recipient-safe view. */
object McrBot {
    private data class Evaluation(val progress: McrHandAnalyzer.Progress, val routes: McrBotRoutes.Assessment) {
        fun utility(anchor: String?): Double = -0.75 * progress.shanten + 4.0 * routes.best +
            0.18 * routes.retention(anchor) + 0.3 * (progress.remainingCount / 40.0).coerceAtMost(1.0) +
            0.08 * progress.effectiveKinds.size / 34.0
    }

    private data class Key(val hand: List<Int>, val melds: List<Meld>, val visible: Set<Int>)

    @JvmStatic
    fun choose(view: McrView): Int {
        val actions = view.actions()
        if (view.viewerSeat() < 0 || actions.isEmpty()) return -1
        if (view.qualifyingWin()) return actions.indexOfFirst { it.type() == WIN }
        // Forced draws/replacements belong exclusively to the session scheduler.
        if (actions.none { it.type() == PASS || it.type() == DISCARD }) return -1
        val own = view.seats()[view.viewerSeat()]
        val known = linkedSetOf<Int>().apply {
            addAll(own.hand())
            addAll(own.melds().flatMap { it.tiles() })
            for (seat in view.seats()) {
                addAll(seat.river().map { it.tile() })
                addAll(seat.melds().filter { !it.closed() }.flatMap { it.tiles() })
            }
            view.focus()?.let { add(it.tile()) }
        }
        val routes = McrBotRoutes(view.viewerSeat(), own.wind(), view.roundWind())
        val cache = HashMap<Key, Evaluation>()
        fun evaluate(hand: List<Int>, melds: List<Meld>, visible: Set<Int> = known): Evaluation {
            val owned = (hand + melds.flatMap { it.tiles() }).toSet()
            return cache.getOrPut(Key(hand.sorted(), melds, visible)) {
                val progress = McrHandAnalyzer.analyze(hand, melds, view.viewerSeat(), (visible - owned).toList())
                Evaluation(progress, routes.assess(hand, melds, visible, progress))
            }
        }

        val discards = actions.withIndex().filter { it.value.type() == DISCARD }
            .associate { it.index to evaluate(own.hand() - it.value.tiles().single(), own.melds()) }
        val unchanged = if (discards.isEmpty()) evaluate(own.hand(), own.melds()) else null
        // Reconstruct commitment from retained structure, without private state or a saved plan.
        val anchor = (discards.values + listOfNotNull(unchanged)).flatMap { it.routes.routes }
            .maxByOrNull { it.feasibility }?.name
        val order = compareByDescending<Evaluation> { it.utility(anchor) }
            .thenBy { it.progress.shanten }.thenByDescending { it.progress.remainingCount }
            .thenByDescending { it.progress.effectiveKinds.size }
        fun bestDiscard(hand: List<Int>, melds: List<Meld>, visible: Set<Int> = known): Evaluation {
            val candidates = hand.distinctBy(Tile::kind).map { tile -> evaluate(hand - tile, melds, visible) }
            val fastest = candidates.minOf { it.progress.shanten }
            return candidates.filter { it.progress.shanten <= fastest + 1 }.minWith(order)
        }

        var selected = actions.indexOfFirst { it.type() == PASS }
        var baseline: Evaluation? = null
        val fastest = discards.values.minOfOrNull { it.progress.shanten }
        for ((index, progress) in discards) {
            if (progress.progress.shanten > requireNotNull(fastest) + 1) continue
            val action = actions[index]
            val comparison = baseline?.let { order.compare(progress, it) } ?: -1
            if (comparison < 0 || comparison == 0 && action.tiles().single() < actions[selected].tiles().single()) {
                baseline = progress
                selected = index
            }
        }
        // Forced draws/replacements belong exclusively to the session scheduler.
        if (selected < 0) return -1
        var best = baseline ?: requireNotNull(unchanged)
        for ((index, action) in actions.withIndex()) {
            if (action.type() !in listOf(CHOW, PUNG, MELDED_KONG, CONCEALED_KONG)) continue
            val hand = own.hand() - action.tiles().toSet()
            val melds = own.melds().toMutableList()
            val kong = action.type() == MELDED_KONG || action.type() == CONCEALED_KONG
            when {
                action.type() == CONCEALED_KONG -> melds.add(Meld(Meld.Type.CONCEALED_QUAD,
                    action.tiles(), view.viewerSeat(), Tile.ABSENT))
                action.type() == MELDED_KONG && action.tiles().size == 1 -> {
                    val pung = melds.indexOfFirst { it.type() == Meld.Type.TRIPLET && it.kind() == Tile.kind(action.tiles().single()) }
                    val old = melds[pung]
                    melds[pung] = Meld(Meld.Type.ADDED_QUAD, old.tiles() + action.tiles(), old.fromSeat(), old.calledTile())
                }
                else -> {
                    val focus = requireNotNull(view.focus())
                    val type = when (action.type()) { CHOW -> Meld.Type.SEQUENCE; PUNG -> Meld.Type.TRIPLET; else -> Meld.Type.OPEN_QUAD }
                    melds.add(Meld(type, (action.tiles() + focus.tile()).sorted(), focus.seat(), focus.tile()))
                }
            }
            val progress = if (!kong) bestDiscard(hand, melds) else {
                // Compare a guaranteed continuation: discard the unknown replacement.
                // Drawing it still consumes a public possible copy, so check every kind.
                if (order.compare(evaluate(hand, melds), best) >= 0) continue
                (0 until 34).mapNotNull { kind ->
                    (kind * 4 until kind * 4 + 4).firstOrNull { it !in known }
                }.map { tile -> evaluate(hand, melds, known + tile) }.maxWithOrNull(order) ?: continue
            }
            if (progress.progress.shanten > best.progress.shanten + 1) continue
            if (order.compare(progress, best) < 0) {
                best = progress
                selected = index
            }
        }
        return selected
    }
}

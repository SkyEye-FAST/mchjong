package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.McrAction.Type.*

/** One deterministic policy. Its entire information boundary is the recipient-safe view. */
object McrBot {
    private val order = compareBy<McrHandAnalyzer.Progress> { it.shanten }
        .thenByDescending { it.remainingCount }.thenByDescending { it.effectiveKinds.size }

    @JvmStatic
    fun choose(view: McrView): Int {
        val actions = view.actions()
        if (view.viewerSeat() < 0 || actions.isEmpty()) return -1
        if (view.qualifyingWin()) return actions.indexOfFirst { it.type() == WIN }
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
        fun evaluate(hand: List<Int>, melds: List<Meld>, visible: Set<Int> = known): McrHandAnalyzer.Progress {
            val owned = (hand + melds.flatMap { it.tiles() }).toSet()
            return McrHandAnalyzer.analyze(hand, melds, view.viewerSeat(), (visible - owned).toList())
        }
        fun bestDiscard(hand: List<Int>, melds: List<Meld>, visible: Set<Int> = known): McrHandAnalyzer.Progress =
            hand.distinctBy(Tile::kind).map { tile -> evaluate(hand - tile, melds, visible) }.minWith(order)

        var selected = actions.indexOfFirst { it.type() == PASS }
        var baseline: McrHandAnalyzer.Progress? = null
        for ((index, action) in actions.withIndex()) if (action.type() == DISCARD) {
            val progress = evaluate(own.hand() - action.tiles().single(), own.melds())
            val comparison = baseline?.let { order.compare(progress, it) } ?: -1
            if (comparison < 0 || comparison == 0 && action.tiles().single() < actions[selected].tiles().single()) {
                baseline = progress
                selected = index
            }
        }
        // Forced draws/replacements belong exclusively to the session scheduler.
        if (selected < 0) return -1
        var best = baseline ?: evaluate(own.hand(), own.melds())
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
                // Require guaranteed progress even when the unknown replacement must be discarded.
                // Then compare the best mandatory discard for every publicly possible replacement kind.
                if (order.compare(evaluate(hand, melds), best) >= 0) continue
                (0 until 34).mapNotNull { kind ->
                    (kind * 4 until kind * 4 + 4).firstOrNull { it !in known }
                }.map { tile -> bestDiscard(hand + tile, melds, known + tile) }.maxWithOrNull(order) ?: continue
            }
            if (order.compare(progress, best) < 0) {
                best = progress
                selected = index
            }
        }
        return selected
    }
}

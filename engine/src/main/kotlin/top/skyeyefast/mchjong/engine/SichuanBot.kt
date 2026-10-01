package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.SichuanAction.Type.*

/** One deterministic policy, restricted to the recipient view and its issued action indices. */
object SichuanBot {
    private val order = compareBy<SichuanHandAnalyzer.Progress> { it.shanten }
        .thenByDescending { it.remainingCount }.thenByDescending { it.effectiveKinds.size }

    @JvmStatic
    fun choose(view: SichuanView): Int {
        val actions = view.actions()
        if (view.viewerSeat() < 0 || actions.isEmpty()) return -1
        actions.indexOfFirst { it.type() == WIN }.takeIf { it >= 0 }?.let { return it }
        val own = view.seats()[view.viewerSeat()]
        val known = linkedSetOf<Int>().apply {
            addAll(own.hand())
            for (seat in view.seats()) {
                addAll(seat.river().map { it.tile() })
                for (meld in seat.melds()) {
                    // Sichuan exposes the middle two tiles of a concealed kong: all four copies are accounted for.
                    if (meld.closed()) {
                        val kind = Tile.kind(meld.tiles().first { it >= 0 })
                        addAll(kind * 4..<kind * 4 + 4)
                    } else addAll(meld.tiles())
                }
            }
            if (view.focus() >= 0) add(view.focus())
        }
        val cache = mutableMapOf<Pair<List<Int>, List<Meld>>, SichuanHandAnalyzer.Progress>()
        fun progress(hand: List<Int>, melds: List<Meld>, suit: Int = own.voidSuit()): SichuanHandAnalyzer.Progress =
            if (suit != own.voidSuit()) SichuanHandAnalyzer.analyze(hand, melds, suit, known.toList())
            else cache.getOrPut(hand.map(Tile::kind).sorted() to melds) {
                SichuanHandAnalyzer.analyze(hand, melds, suit, known.toList())
            }
        val value = if (own.voidSuit() >= 0) SichuanBotValue(view.rules(), own.voidSuit(), known) else null
        val evaluations = mutableMapOf<Pair<List<Int>, List<Meld>>, SichuanBotValue.Evaluation>()
        fun evaluate(hand: List<Int>, melds: List<Meld>): SichuanBotValue.Evaluation =
            evaluations.getOrPut(hand.map(Tile::kind).sorted() to melds) {
                value!!.evaluate(hand, melds, progress(hand, melds))
            }
        val valueOrder = compareByDescending<SichuanBotValue.Evaluation> { it.utility }
            .thenComparator { a, b -> order.compare(a.progress, b.progress) }
        fun bestDiscard(hand: List<Int>, melds: List<Meld>): SichuanBotValue.Evaluation {
            val missing = hand.any { Tile.kind(it) / 9 == own.voidSuit() }
            val choices = hand.filter { (!missing || Tile.kind(it) / 9 == own.voidSuit())
                && (own.firstDiscard() == Tile.ABSENT || own.river().isNotEmpty() || it == own.firstDiscard()) }
            val candidates = choices.distinctBy(Tile::kind).map { evaluate(hand - it, melds) }
            val fastest = candidates.minOf { it.progress.shanten }
            return candidates.filter { it.progress.shanten <= fastest + 1 }.minWith(valueOrder)
        }

        if (view.phase() == SichuanGame.Phase.VOIDING) {
            val counts = IntArray(27)
            own.hand().forEach { counts[Tile.kind(it)]++ }
            // Removing a suit costs both tiles and their existing pair/triplet/connected support.
            fun cost(suit: Int): Int = (suit * 9..<suit * 9 + 9).sumOf { kind ->
                4 * counts[kind] + 2 * (counts[kind] / 2) + 4 * (counts[kind] / 3) +
                    (1..2).sumOf { gap -> if (kind % 9 + gap < 9) minOf(counts[kind], counts[kind + gap]) else 0 }
            }
            fun efficiency(suit: Int): SichuanHandAnalyzer.Progress {
                if (own.hand().size == 13) return progress(own.hand(), own.melds(), suit)
                val missing = own.hand().filter { Tile.kind(it) / 9 == suit }
                return missing.ifEmpty { own.hand() }.distinctBy(Tile::kind)
                    .map { progress(own.hand() - it, own.melds(), suit) }.minWith(order)
            }
            val suit = actions.filter { it.type() == VOID_SUIT }.map { it.suit() }.distinct()
                .minWith(Comparator { a, b -> cost(a).compareTo(cost(b)).takeIf { it != 0 }
                    ?: order.compare(efficiency(a), efficiency(b)).takeIf { it != 0 } ?: a.compareTo(b) })
            val choices = actions.indices.filter { actions[it].type() == VOID_SUIT && actions[it].suit() == suit }
            if (choices.size == 1) return choices.single()
            // The secret physical first discard is selected together with the suit, from issued choices only.
            return choices.minWith(Comparator { a, b ->
                val left = actions[a].tiles().single()
                val right = actions[b].tiles().single()
                fun support(tile: Int): Int {
                    val kind = Tile.kind(tile)
                    return 2 * (counts[kind] - 1) + (-2..2).filter { it != 0 && kind % 9 + it in 0..8 }
                        .sumOf { counts[kind + it] }
                }
                val comparison = if (own.hand().size == 14)
                    order.compare(progress(own.hand() - left, own.melds(), suit), progress(own.hand() - right, own.melds(), suit))
                else support(left).compareTo(support(right))
                comparison.takeIf { it != 0 } ?: left.compareTo(right)
            })
        }

        var selected = actions.indexOfFirst { it.type() == PASS || it.type() == DRAW }
        var baseline: SichuanBotValue.Evaluation? = null
        val discards = actions.indices.filter { actions[it].type() == DISCARD }
            .associateWith { evaluate(own.hand() - actions[it].tiles().single(), own.melds()) }
        val fastest = discards.values.minOfOrNull { it.progress.shanten }
        for ((index, evaluation) in discards) {
            val action = actions[index]
            if (evaluation.progress.shanten > fastest!! + 1) continue
            val comparison = baseline?.let { valueOrder.compare(evaluation, it) } ?: -1
            if (comparison < 0 || comparison == 0 && action.tiles().single() < actions[selected].tiles().single()) {
                baseline = evaluation
                selected = index
            }
        }
        if (selected < 0) return -1
        // Clearing the void suit takes priority over optional calls.
        if (own.hand().any { Tile.kind(it) / 9 == own.voidSuit() }) return selected
        var best = baseline ?: evaluate(own.hand(), own.melds())
        for ((index, action) in actions.withIndex()) {
            if (action.type() !in listOf(PUNG, DISCARD_KONG, CONCEALED_KONG, ADDED_KONG)) continue
            val hand = own.hand() - action.tiles().toSet()
            val melds = own.melds().toMutableList()
            when (action.type()) {
                ADDED_KONG -> {
                    val pung = melds.indexOfFirst { it.type() == Meld.Type.TRIPLET && it.kind() == Tile.kind(action.tiles().single()) }
                    val old = melds[pung]
                    melds[pung] = Meld(Meld.Type.ADDED_QUAD, old.tiles() + action.tiles(), old.fromSeat(), old.calledTile())
                }
                CONCEALED_KONG -> melds += Meld(Meld.Type.CONCEALED_QUAD, action.tiles(), view.viewerSeat(), Tile.ABSENT)
                else -> melds += Meld(if (action.type() == PUNG) Meld.Type.TRIPLET else Meld.Type.OPEN_QUAD,
                    (action.tiles() + view.focus()).sorted(), view.supplier(), view.focus())
            }
            // For a kong, discarding the unknown replacement always preserves this 13-tile remainder.
            val evaluation = if (action.type() == PUNG) bestDiscard(hand, melds) else evaluate(hand, melds)
            val income = when (action.type()) {
                DISCARD_KONG -> view.rules().discardKongPayment()
                CONCEALED_KONG -> view.rules().concealedKongPayment()
                ADDED_KONG -> if (action.tiles().single() == own.drawn()) view.rules().addedKongPayment() else 0
                else -> 0
            }
            val sanctioned = view.ledger().any { it.type() == SichuanSettlement.Type.FLOWER_PIG && it.payer() == view.viewerSeat() }
            val payers = if (action.type() == DISCARD_KONG) 1 else view.seats().count { !it.won() } - 1
            val bonus = if (!sanctioned) minOf(0.45, 0.2 * kotlin.math.ln(1.0 + income * payers)) else 0.0
            // Income can settle a close comparison, but cannot buy a shanten retreat.
            val limit = best.progress.shanten + if (action.type() == PUNG) 1 else 0
            if (evaluation.progress.shanten <= limit && evaluation.utility + bonus > best.utility + 0.15) {
                best = evaluation.copy(utility = evaluation.utility + bonus)
                selected = index
            }
        }
        return selected
    }
}

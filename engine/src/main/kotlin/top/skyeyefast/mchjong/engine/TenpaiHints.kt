package top.skyeyefast.mchjong.engine

/** Cached structural waits; availability is the number of unseen copies, not a peek at the wall. */
class TenpaiHints {
    @JvmRecord
    data class Wait(val kind: Int, val remaining: Int)

    private var hand: List<Int> = emptyList()
    private var melds: List<Meld> = emptyList()
    private var kinds: Set<Int> = emptySet()
    private var snapshot: RiichiView? = null
    private var result: List<Wait> = emptyList()

    fun waits(view: RiichiView): List<Wait> {
        if (snapshot !== view) {
            result = calculate(view)
            snapshot = view
        }
        return result
    }

    private fun calculate(view: RiichiView): List<Wait> {
        if (
            view.viewerSeat() !in view.seats().indices || view.exitVote() != null ||
            view.phase() != RiichiView.Phase.TURN && view.phase() != RiichiView.Phase.REACTION && view.phase() != RiichiView.Phase.DRAW
        ) return emptyList()
        val self = view.seats()[view.viewerSeat()]
        val size = self.hand().size + self.melds().size * 3
        if (size != 13 || self.hand().any { it < 0 }) return emptyList()
        if (hand != self.hand() || melds != self.melds()) {
            hand = self.hand()
            melds = self.melds()
            kinds = RiichiHandAnalyzer.waits(hand, melds)
        }
        val known = VisibleTiles.counts(view)
        return java.util.List.copyOf(
            kinds.asSequence()
                .filter { !view.rules().sanma() || it == 0 || it >= 8 }
                .sorted()
                .map { Wait(it, maxOf(0, 4 - known[it])) }
                .toList(),
        )
    }
}

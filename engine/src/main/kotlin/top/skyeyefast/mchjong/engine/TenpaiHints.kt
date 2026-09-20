package top.skyeyefast.mchjong.engine

/** Cached structural waits; availability is the number of unseen copies, not a peek at the wall. */
class TenpaiHints {
    @JvmRecord
    data class Wait(val kind: Int, val remaining: Int)

    private var hand: List<Int> = emptyList()
    private var melds: List<Meld> = emptyList()
    private val byDiscard = HashMap<Int, Set<Int>>()
    private var snapshot: TableView? = null
    private var lastDiscard = Tile.ABSENT
    private var result: List<Wait> = emptyList()

    fun waits(view: TableView, discard: Int): List<Wait> {
        if (snapshot !== view || lastDiscard != discard) {
            result = calculate(view, discard)
            snapshot = view
            lastDiscard = discard
        }
        return result
    }

    private fun calculate(view: TableView, discard: Int): List<Wait> {
        if (
            view.viewerSeat() !in view.seats().indices || view.exitVote() != null ||
            view.phase() != Game.Phase.TURN && view.phase() != Game.Phase.REACTION && view.phase() != Game.Phase.DRAW
        ) return emptyList()
        val self = view.seats()[view.viewerSeat()]
        val size = self.hand().size + self.melds().size * 3
        if ((size != 13 && size != 14) || self.hand().any { it < 0 }) return emptyList()
        var key = Tile.ABSENT
        if (size == 14) {
            if (
                discard !in self.hand() || view.actions().none {
                    (it.type() == Action.Type.DISCARD || it.type() == Action.Type.RIICHI) && discard in it.tiles()
                }
            ) return emptyList()
            key = Tile.kind(discard)
        }
        if (hand != self.hand() || melds != self.melds()) {
            hand = self.hand()
            melds = self.melds()
            byDiscard.clear()
        }
        val kinds = byDiscard.getOrPut(key) {
            val concealed = hand.toMutableList()
            if (key >= 0) concealed.remove(concealed.first { Tile.kind(it) == key })
            HandAnalyzer.waits(concealed, melds)
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

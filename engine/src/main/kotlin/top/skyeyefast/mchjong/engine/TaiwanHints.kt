package top.skyeyefast.mchjong.engine

/** Convenience facts from a recipient-safe view; never a prediction of hidden draws. */
class TaiwanHints {
    @JvmRecord
    data class TileHint(val kind: Int, val remaining: Int, val discardTai: Int, val drawTai: Int,
                        val currentTai: Int)
    @JvmRecord
    data class Preview(val discard: Boolean, val shanten: Int, val tiles: List<TileHint>, val passedWin: Boolean)

    private var snapshot: TaiwanView? = null
    private var lastDiscard = Tile.ABSENT
    private var result: Preview? = null

    fun preview(view: TaiwanView, discard: Int): Preview? {
        if (snapshot !== view || lastDiscard != discard) {
            result = calculate(view, discard)
            snapshot = view
            lastDiscard = discard
        }
        return result
    }

    private fun calculate(view: TaiwanView, discard: Int): Preview? {
        val owner = view.recipient()
        if (owner !in 0..3 || view.phase() !in listOf(TaiwanGame.Phase.TURN, TaiwanGame.Phase.REACTION)) return null
        val own = view.seats()[owner]
        val size = own.concealed().size + 3 * own.melds().size
        if (size !in 16..17) return null
        val preview = size == 17 && view.actions().any { it.type() == TaiwanAction.Type.DISCARD && discard in it.tiles() }
        // Without a selected discard, a drawn hand shows its pre-draw structure (also when READY).
        val removed = if (preview) discard else if (size == 17) own.drawn() else Tile.ABSENT
        if (size == 17 && removed !in own.concealed()) return null
        val hand = if (size == 17) own.concealed() - removed else own.concealed()
        val owned = (hand + own.melds().flatMap { it.tiles() }).toSet()
        val known = linkedSetOf<Int>().apply {
            addAll(own.concealed())
            addAll(own.melds().flatMap { it.tiles() })
            addAll(own.flowers().map { it.id() })
            view.seats().forEach { seat ->
                addAll(seat.river())
                addAll(seat.melds().filter { !it.closed() }.flatMap { it.tiles() })
            }
            view.focus()?.let { add(it.tile()) }
        }
        val counts = known.filterNot(Tile::isFlower).groupingBy(Tile::kind).eachCount()
        // Public exhaustion changes availability, never the structural candidate set.
        val progress = TaiwanHandAnalyzer.analyze(hand, own.melds(), owner, emptyList())
        val waits = if (progress.shanten == 0) TaiwanHandAnalyzer.waits(hand, own.melds(), owner) else emptySet()
        val kinds = if (progress.shanten == 0) waits else progress.effectiveTiles.map { it.kind }
        val rules = view.rules().restore()
        val wind = Tile.EAST + Math.floorMod(owner - view.opening().dealer(), 4)
        val tiles = kinds.sorted().map { kind ->
            val tile = (kind * 4..<kind * 4 + 4).first { it !in owned }
            fun tai(method: TaiwanWinContext.Method, current: Boolean = false): Int {
                val context = TaiwanWinContext(method, wind, view.roundWind(), wind - Tile.EAST + 1,
                    own.flowers().toSet(), TaiwanWinContext.DrawOrigin.ORDINARY,
                    current && method == TaiwanWinContext.Method.DISCARD && view.drawable() == 0,
                    TaiwanWinContext.Opening.NONE, own.ready())
                return TaiwanHandAnalyzer.score(hand, own.melds(), owner, tile, context, rules)?.tai ?: -1
            }
            val current = !preview && view.focus()?.let { it.seat() != owner && Tile.kind(it.tile()) == kind } == true
            TileHint(kind, maxOf(0, 4 - (counts[kind] ?: 0)),
                if (kind in waits) tai(TaiwanWinContext.Method.DISCARD) else -1,
                if (kind in waits) tai(TaiwanWinContext.Method.SELF_DRAW) else -1,
                if (current && kind in waits) tai(if (view.focus()!!.addedKong()) TaiwanWinContext.Method.ROBBING_KONG
                    else TaiwanWinContext.Method.DISCARD, true) else -1)
        }
        return Preview(preview, progress.shanten, java.util.List.copyOf(tiles), view.passedWin() == true)
    }
}

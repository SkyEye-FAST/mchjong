package top.skyeyefast.mchjong.engine

/** Shape and scorer facts from the recipient's hand and public tiles only. */
class McrHints {
    @JvmRecord
    data class TileHint(val kind: Int, val remaining: Int, val discardFan: Int, val drawFan: Int,
                        val currentFan: Int)
    @JvmRecord
    data class Preview(val discard: Boolean, val shanten: Int, val tiles: List<TileHint>, val winForbidden: Boolean)

    private var snapshot: McrView? = null
    private var lastDiscard = Tile.ABSENT
    private var result: Preview? = null

    fun preview(view: McrView, discard: Int): Preview? {
        if (snapshot !== view || lastDiscard != discard) {
            result = calculate(view, discard)
            snapshot = view
            lastDiscard = discard
        }
        return result
    }

    private fun calculate(view: McrView, discard: Int): Preview? {
        if (view.viewerSeat() !in 0..3 || view.phase() !in listOf(McrGame.Phase.TURN, McrGame.Phase.REACTION, McrGame.Phase.DRAW)) return null
        val own = view.seats()[view.viewerSeat()]
        val size = own.hand().size + 3 * own.melds().size
        if (size !in 13..14 || own.hand().any { it < 0 }) return null
        val preview = size == 14
        if (preview && view.actions().none { it.type() == McrAction.Type.DISCARD && discard in it.tiles() }) return null
        val hand = if (preview) own.hand() - discard else own.hand()
        val owned = (hand + own.melds().flatMap { it.tiles() }).toSet()
        val public = linkedSetOf<Int>()
        view.seats().forEach { seat ->
            public += seat.river().map { it.tile() }
            public += seat.melds().filter { !it.closed() }.flatMap { it.tiles() }
        }
        view.focus()?.let { public += it.tile() }
        if (preview) public += discard
        // A proposed discard stays known, even though it no longer belongs to the analyzed hand.
        val known = (public + own.hand() + own.melds().flatMap { it.tiles() }).toSet()
        val counts = known.groupingBy(Tile::kind).eachCount()
        val progress = McrHandAnalyzer.analyze(hand, own.melds(), view.viewerSeat(), (known - owned).toList())
        val waits = if (progress.shanten == 0) McrHandAnalyzer.waits(hand, own.melds(), view.viewerSeat()) else emptySet()
        val kinds = if (progress.shanten == 0) waits else progress.effectiveKinds
        val tiles = kinds.sorted().map { kind ->
            val tile = (kind * 4..<kind * 4 + 4).first { it !in owned }
            fun fan(method: McrWinContext.Method, current: Boolean = false): Int {
                val focus = view.focus()
                val context = McrWinContext(method, own.wind(), view.roundWind(),
                    current && view.remaining() == 0 && focus?.addedKong() != true,
                    if (current && focus?.addedKong() == true) McrWinContext.KongWin.ROBBED else McrWinContext.KongWin.NONE,
                    public.count { Tile.kind(it) == kind && (!current || it != focus?.tile()) } == 3,
                    own.flowers().size)
                return McrHandAnalyzer.score(hand, own.melds(), view.viewerSeat(), tile, context)?.nonFlowerFan() ?: -1
            }
            TileHint(kind, maxOf(0, 4 - (counts[kind] ?: 0)),
                if (kind in waits) fan(McrWinContext.Method.DISCARD) else -1,
                if (kind in waits) fan(McrWinContext.Method.SELF_DRAW) else -1,
                if (!preview && view.focus()?.let { Tile.kind(it.tile()) == kind } == true && kind in waits)
                    fan(McrWinContext.Method.DISCARD, true) else -1)
        }
        return Preview(preview, progress.shanten, java.util.List.copyOf(tiles), own.winForbidden())
    }
}

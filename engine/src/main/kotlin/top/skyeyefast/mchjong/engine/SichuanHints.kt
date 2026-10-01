package top.skyeyefast.mchjong.engine

/** Sichuan structure/ready-value semantics, without access to private game state. */
class SichuanHints {
    @JvmRecord
    data class TileHint(val kind: Int, val remaining: Int, val fan: Int, val value: Int)
    @JvmRecord
    data class Preview(val discard: Boolean, val voidSuit: Int, val voidTiles: List<Int>, val shanten: Int,
                       val tiles: List<TileHint>, val readyValue: Int, val passedFan: Int)
    private var snapshot: SichuanView? = null
    private var lastDiscard = Tile.ABSENT
    private var result: Preview? = null

    fun preview(view: SichuanView, discard: Int): Preview? {
        if (snapshot !== view || lastDiscard != discard) {
            result = calculate(view, discard)
            snapshot = view
            lastDiscard = discard
        }
        return result
    }

    private fun calculate(view: SichuanView, discard: Int): Preview? {
        if (view.viewerSeat() !in 0..3 || view.phase() !in listOf(SichuanGame.Phase.TURN, SichuanGame.Phase.REACTION)) return null
        val own = view.seats()[view.viewerSeat()]
        if (own.won() || own.voidSuit() < 0 || own.hand().any { it < 0 }) return null
        val size = own.hand().size + 3 * own.melds().size
        if (size !in 13..14) return null
        val preview = size == 14 && view.actions().any { it.type() == SichuanAction.Type.DISCARD && discard in it.tiles() }
        val hand = if (preview) own.hand() - discard else own.hand()
        val missing = hand.filter { Tile.kind(it) / 9 == own.voidSuit() }
        if (missing.isNotEmpty()) return Preview(preview, own.voidSuit(), java.util.List.copyOf(missing), -1, emptyList(), 0,
            if (preview) -1 else view.passedFan())
        if (size == 14 && !preview) return null
        val owned = (hand + own.melds().flatMap { it.tiles() }).toSet()
        val known = linkedSetOf<Int>().apply {
            addAll(own.hand())
            view.seats().forEach { seat ->
                addAll(seat.river().map { it.tile() })
                seat.melds().forEach { meld ->
                    // The two face-up middle tiles publicly establish all four copies of a concealed kong.
                    if (meld.closed()) {
                        val kind = Tile.kind(meld.tiles().first { it >= 0 })
                        addAll(kind * 4..<kind * 4 + 4)
                    } else addAll(meld.tiles())
                }
            }
            if (view.focus() >= 0) add(view.focus())
        }
        val counts = known.groupingBy(Tile::kind).eachCount()
        val progress = SichuanHandAnalyzer.analyze(hand, own.melds(), own.voidSuit(), known.toList())
        val ready = SichuanHandAnalyzer.readyValue(hand, own.melds(), own.voidSuit(), view.rules())
        val tiles = if (ready > 0) (0..<27).filter { it / 9 != own.voidSuit() && owned.count { tile -> Tile.kind(tile) == it } < 4 }
            .mapNotNull { kind ->
                val tile = (kind * 4..<kind * 4 + 4).first { it !in owned }
                val score = SichuanHandAnalyzer.score(hand + tile, own.melds(), view.rules(), false, false, false, false)
                    ?: return@mapNotNull null
                TileHint(kind, maxOf(0, 4 - (counts[kind] ?: 0)), score.fan(), score.value())
            }
        else progress.effectiveKinds.sorted().map { TileHint(it, maxOf(0, 4 - (counts[it] ?: 0)), -1, 0) }
        return Preview(preview, own.voidSuit(), emptyList(), progress.shanten, java.util.List.copyOf(tiles), ready,
            if (preview && ready == 0) -1 else view.passedFan())
    }
}

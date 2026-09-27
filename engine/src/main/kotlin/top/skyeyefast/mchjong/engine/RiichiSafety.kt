package top.skyeyefast.mchjong.engine

/** Passed public discards against established riichi; draw identities are never read. */
internal object RiichiSafety {
    @JvmStatic
    fun from(events: List<ReplayHand.Event>): Map<Int, Long> {
        val safe = mutableMapOf<Int, Long>()
        var pending = Tile.ABSENT
        for (event in events) {
            if (event.kind == ReplayHand.Kind.DORA) continue
            // A subsequent action proves the preceding discard's ron window closed.
            // The newest discard remains pending, including a riichi declaration tile.
            if (pending >= 0) {
                val bit = 1L shl Tile.kind(pending)
                safe.replaceAll { _, mask -> mask or bit }
                pending = Tile.ABSENT
            }
            when (event.kind) {
                ReplayHand.Kind.RIICHI -> safe[event.seat] = 0L
                ReplayHand.Kind.DISCARD -> pending = event.tile
                // Custom rules can allow declarations that change the waits.
                ReplayHand.Kind.MELD, ReplayHand.Kind.NUKI -> if (event.committed && event.seat in safe) safe[event.seat] = 0L
                else -> Unit
            }
        }
        return safe
    }
}

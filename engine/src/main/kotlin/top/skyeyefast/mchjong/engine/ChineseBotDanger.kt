package top.skyeyefast.mchjong.engine

/** Public, per-opponent loss estimates. Rivers are evidence, never furiten or suji certificates. */
internal object ChineseBotDanger {
    data class Opponent(val seat: Int, val losses: List<Double>)
    data class Assessment(val opponents: List<Opponent>) {
        fun against(seat: Int, kind: Int): Double = opponents.firstOrNull { it.seat == seat }?.losses?.get(kind) ?: 0.0
        fun expectedLoss(kind: Int): Double = opponents.sumOf { it.losses[kind] }
        fun replacementLoss(known: Set<Int>, kinds: Int): Double {
            val copies = IntArray(kinds) { kind -> (kind * 4..<kind * 4 + 4).count { it !in known } }
            val total = copies.sum()
            return if (total == 0) 0.0 else copies.indices.sumOf { expectedLoss(it) * copies[it] } / total
        }
    }
    private fun pressure(melds: Int, river: Int, remaining: Int): Double =
        (0.045 * (melds - 1).coerceAtLeast(0) + 0.007 * (river - 5).coerceAtLeast(0) +
            0.07 * ((24 - remaining) / 24.0).coerceIn(0.0, 1.0)).coerceAtMost(0.38)

    private fun demand(kind: Int, unseen: IntArray): Double {
        var support = if (unseen[kind] >= 2) 0.6 else if (unseen[kind] == 1) 0.3 else 0.0
        if (kind < 27) for (start in kind - 2..kind) {
            if (start < 0 || start / 9 != kind / 9 || start % 9 > 6) continue
            val others = (start..start + 2).filter { it != kind }
            if (others.all { unseen[it] > 0 }) support += 0.25
        }
        // Special forms and changing concealed shapes prevent declaring a repeat absolutely safe.
        return support.coerceIn(0.25, 1.35)
    }
    private fun relevance(kind: Int, suit: Int?, fixed: List<Int>, river: List<Int>): Double {
        val suited = if (suit == null) 1.0 else if (kind >= 27) 1.1 else if (kind / 9 == suit) 1.8 else 0.55
        val repeat = if (river.takeLast(4).any { Tile.kind(it) == kind }) 0.85 else 1.0
        val exposed = if (fixed.count { it == kind } >= 3) 0.4 else 1.0
        return suited * repeat * exposed
    }

    @JvmStatic fun mcr(view: McrView, known: Set<Int>): Assessment {
        val unseen = IntArray(34) { kind -> (kind * 4..<kind * 4 + 4).count { it !in known } }
        return Assessment(view.seats().mapIndexedNotNull { index, seat ->
            if (index == view.viewerSeat()) return@mapIndexedNotNull null
            val public = seat.melds().filter { it.tiles().all { tile -> tile >= 0 } }
            val fixed = public.flatMap { it.tiles() }.map(Tile::kind)
            val suit = if (public.size >= 2) fixed.filter { it < 27 }.map { it / 9 }.distinct().singleOrNull() else null
            val pungs = public.filter { it.type() != Meld.Type.SEQUENCE }.map { it.kind() }
            // A partial flush/pung signal predicts a route, not an already awarded fan total.
            val estimatedFan = when {
                pungs.count { it in Tile.EAST..Tile.NORTH } == 4 || pungs.count { it >= 31 } == 3 -> 88
                suit != null -> 16
                pungs.size >= 2 && pungs.any { it >= 31 || it == seat.wind() || it == view.roundWind() } -> 10
                else -> 8
            }
            // Flowers increase payment but cannot satisfy the eight non-flower fan gate.
            val qualification = if (estimatedFan == 88) 1.0 else if (estimatedFan > 8) 0.55 + 0.075 * public.size else if (public.size >= 3) 0.45 else 0.6
            val threat = pressure(seat.melds().size, seat.river().size, view.remaining()) * qualification
            val river = seat.river().map { it.tile() }
            Opponent(index, (0..33).map { kind ->
                // Everyone pays eight on ron; the discarder bears the additional fan, including flowers.
                threat * (estimatedFan + seat.flowers().size) * demand(kind, unseen) * relevance(kind, suit, fixed, river)
            })
        })
    }

    @JvmStatic fun sichuan(view: SichuanView, known: Set<Int>): Assessment {
        val unseen = IntArray(27) { kind -> (kind * 4..<kind * 4 + 4).count { it !in known } }
        return Assessment(view.seats().mapIndexedNotNull { index, seat ->
            if (index == view.viewerSeat() || seat.won()) return@mapIndexedNotNull null
            val fixed = seat.melds().flatMap { it.tiles() }.filter { it >= 0 }.map(Tile::kind)
            val suit = if (seat.melds().size >= 2) fixed.map { it / 9 }.distinct().singleOrNull() else null
            val fan = 1 + seat.melds().count { it.quad() } + (if (seat.melds().size >= 2) 1 else 0) + (if (suit != null) 2 else 0)
            val value = (1 shl minOf(view.rules().fanCap(), fan)).toDouble()
            val threat = pressure(seat.melds().size, seat.river().size, view.wall().remaining())
            val river = seat.river().map { it.tile() }
            Opponent(index, (0..26).map { kind ->
                // A missing-suit tile cannot complete this opponent's hand. Retired winners never collect again.
                if (kind / 9 == seat.voidSuit()) 0.0 else threat * value * demand(kind, unseen) * relevance(kind, suit, fixed, river)
            })
        })
    }
}

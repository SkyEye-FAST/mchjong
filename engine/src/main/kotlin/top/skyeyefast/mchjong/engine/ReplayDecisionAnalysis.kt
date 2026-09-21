package top.skyeyefast.mchjong.engine

import java.util.HashSet

/** Shape-only replay analysis using information that was visible to the acting player at that decision. */
object ReplayDecisionAnalysis {
    @JvmRecord
    data class Improvement(val kind: Int, val remaining: Int)

    @JvmRecord
    data class Candidate(val shanten: Int, val live: Int, val improving: List<Improvement>)

    @JvmStatic
    fun analyze(match: ReplayMatch, handIndex: Int, decision: ReplayHand.Decision): List<Candidate?> {
        val hand = match.hands[handIndex]
        val frame = ReplayPlayback.at(match, handIndex, decision.eventCursor)
        val self = frame.seats[decision.seat]
        val known = visibleCounts(frame, decision.seat, match.rules.sanma())
        val byDiscard = HandAnalyzer.discardEfficiency(self.hand(), self.melds(), false)
        return decision.options.map { action ->
            if (action.type() != Action.Type.DISCARD && action.type() != Action.Type.RIICHI || action.tiles().isEmpty()) {
                null
            } else {
                val shape = byDiscard[Tile.kind(action.tiles().first())] ?: return@map null
                val improvements = shape.improving.asSequence()
                    .filter { kind -> !match.rules.sanma() || kind == 0 || kind >= 8 }
                    .sorted()
                    .map { kind -> Improvement(kind, maxOf(0, copies(match.rules.sanma(), kind) - known[kind])) }
                    .toList()
                Candidate(shape.shanten, improvements.sumOf { it.remaining }, java.util.List.copyOf(improvements))
            }
        }
    }

    private fun visibleCounts(frame: ReplayPlayback.Frame, viewer: Int, sanma: Boolean): IntArray {
        val visible = HashSet<Int>()
        visible += frame.seats[viewer].hand()
        for (seat in frame.seats) {
            seat.river().forEach { visible += it.tile() }
            seat.melds().forEach { visible += it.tiles() }
            visible += seat.norths()
        }
        visible += frame.dora
        visible.removeIf { it < 0 }
        val counts = IntArray(34)
        for (tile in visible) counts[Tile.kind(tile)]++
        if (sanma) for (kind in 1..7) counts[kind] = 4
        return counts
    }

    private fun copies(sanma: Boolean, kind: Int): Int = if (sanma && kind in 1..7) 0 else 4
}

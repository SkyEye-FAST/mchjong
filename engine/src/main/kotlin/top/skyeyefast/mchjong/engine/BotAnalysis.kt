package top.skyeyefast.mchjong.engine

import java.util.ArrayList
import java.util.Collections
import kotlin.math.ln1p
import kotlin.math.pow

/** Decision-local shape cache and bounded draw/discard search. All draws are face categories. */
internal class BotAnalysis(private val view: TableView, private val level: BotDifficulty) {
    @JvmField
    val value = BotValue(view)

    @JvmField
    val unseen = IntArray(68)

    @JvmField
    val defence: BotDefence

    private val discards = HashMap<ShapeKey, Map<Int, TileEfficiency>>()
    private val bestDiscards = HashMap<ShapeKey, Map<Int, TileEfficiency>>()
    private val hands = HashMap<ShapeKey, TileEfficiency>()
    private val waits = HashMap<ShapeKey, Set<Int>>()
    private val advances = HashMap<State, Double>()

    @JvmField
    var drawNodes = 0

    @JvmField
    var advanceNodes = 0

    @JvmField
    var tenpaiLeaves = 0

    class State(
        hand: kotlin.collections.List<Int>,
        melds: kotlin.collections.List<Meld>,
        norths: kotlin.collections.List<Int>,
        private val riverValue: Long,
        private val riichiValue: Boolean,
        private val ronBlockedValue: Boolean,
        private val riichiHanValue: Int,
    ) {
        private val handValue = Collections.unmodifiableList(ArrayList(hand))
        private val meldsValue = Collections.unmodifiableList(ArrayList(melds))
        private val northsValue = Collections.unmodifiableList(ArrayList(norths))
        private val faceSum = handValue.sumOf { face(it) }
        private var orderValue: String? = null

        fun orderKey(): String = orderValue ?: handValue.map { face(it) }.sorted().toString().also { orderValue = it }

        fun removedFrom(before: State): Int = before.faceSum - faceSum

        fun hand(): kotlin.collections.List<Int> = handValue

        fun melds(): kotlin.collections.List<Meld> = meldsValue

        fun norths(): kotlin.collections.List<Int> = northsValue

        fun river(): Long = riverValue

        fun riichi(): Boolean = riichiValue

        fun ronBlocked(): Boolean = ronBlockedValue

        fun riichiHan(): Int = riichiHanValue

        fun discard(tile: Int, declare: Boolean): State {
            val next = ArrayList(handValue)
            next.remove(tile)
            return State(
                next,
                meldsValue,
                northsValue,
                riverValue or (1L shl Tile.kind(tile)),
                riichiValue || declare,
                ronBlockedValue,
                if (riichiValue || declare) riichiHanValue else 1,
            )
        }

        fun draw(tile: Int): State {
            val next = ArrayList(handValue)
            next += tile
            return State(next, meldsValue, northsValue, riverValue, riichiValue, riichiValue && ronBlockedValue, riichiHanValue)
        }

        override fun equals(other: Any?): Boolean =
            other is State &&
                handValue == other.handValue &&
                meldsValue == other.meldsValue &&
                northsValue == other.northsValue &&
                riverValue == other.riverValue &&
                riichiValue == other.riichiValue &&
                ronBlockedValue == other.ronBlockedValue &&
                riichiHanValue == other.riichiHanValue

        override fun hashCode(): Int {
            var result = handValue.hashCode()
            result = 31 * result + meldsValue.hashCode()
            result = 31 * result + northsValue.hashCode()
            result = 31 * result + riverValue.hashCode()
            result = 31 * result + riichiValue.hashCode()
            result = 31 * result + ronBlockedValue.hashCode()
            result = 31 * result + riichiHanValue
            return result
        }

        override fun toString(): String =
            "State[hand=$handValue, melds=$meldsValue, norths=$northsValue, river=$riverValue, riichi=$riichiValue, " +
                "ronBlocked=$ronBlockedValue, riichiHan=$riichiHanValue]"
    }

    @JvmRecord
    data class Utility(val speed: Double, val retention: Double, val value: Double, val waits: Double, val legality: Double) {
        fun total(): Double = speed + retention + value + waits + legality
    }

    @JvmRecord
    data class Evaluation(
        val shanten: Int,
        val live: Int,
        val points: Double,
        val utility: Double,
        val waits: BotValue.Waits,
        val potential: BotValue.Potential,
        val terms: Utility,
    )

    private data class ShapeKey(val hand: kotlin.collections.List<Int>, val melds: kotlin.collections.List<String>) {
        constructor(state: State) : this(
            state.hand().map { Tile.kind(it) }.sorted(),
            state.melds().map { it.libraryNotation() }.sorted(),
        )
    }

    init {
        // The playing composition, not equipment stock or physical-copy identities.
        for (tile in Tile.set(view.rules().sanma(), view.rules().redFives())) unseen[face(tile)]++
        for (tile in VisibleTiles.tiles(view)) unseen[face(tile)]--
        for (i in unseen.indices) unseen[i] = maxOf(0, unseen[i])
        defence = BotDefence(view, level, value, unseen)
    }

    fun initial(): State {
        val self = view.seats()[view.viewerSeat()]
        var river = 0L
        for (discard in self.river()) river = river or (1L shl Tile.kind(discard.tile()))
        // The engine clears temporary furiten on a real draw's discard. Root 14-tile
        // states are evaluated only after that discard (or a replacement declaration).
        // A chi/pon discard has no drawn tile and must retain the temporary block.
        val blocked = view.ronBlocked() && (self.riichi() || self.drawn() < 0)
        return State(self.hand(), self.melds(), self.norths(), river, self.riichi(), blocked, view.riichiHan())
    }

    fun discards(state: State): Map<Int, TileEfficiency> =
        discards.getOrPut(ShapeKey(state)) { HandAnalyzer.discardEfficiency(state.hand(), state.melds(), false) }

    fun shape(state: State): TileEfficiency =
        hands.getOrPut(ShapeKey(state)) { HandAnalyzer.handEfficiency(state.hand(), state.melds(), false) }

    fun evaluate(state: State, shape: TileEfficiency, remaining: IntArray): Evaluation {
        val live = live(shape.improving, remaining)
        val key = ShapeKey(state)
        // HARD values all actual tenpai continuations instead of requesting the
        // library's separate nested good-shape enumeration for these same roots.
        val potential = value.potential(state, shape.shanten, remaining)
        hands.putIfAbsent(key, shape)
        val waitValue = if (shape.shanten == 0) {
            value.waits(state, waits.getOrPut(key) { HandAnalyzer.waits(state.hand(), state.melds()) }, remaining)
        } else {
            BotValue.Waits.EMPTY
        }
        val points = if (shape.shanten == 0) waitValue.average() else potential.estimate
        // Ordinal utilities, not fitted win/deal-in probabilities or expected monetary returns.
        val valueTerm = if (level == BotDifficulty.EASY) {
            if (shape.shanten == 0) minOf(16.0, points / 500) else 0.0
        } else ln1p(points / 1000) * if (shape.shanten == 0) 16 else 6
        val legality = (if (level != BotDifficulty.EASY && !potential.viable && shape.shanten > 0) -45.0 else 0.0) +
            if (shape.shanten == 0 && waitValue.quality() == 0.0) -55.0 else 0.0
        val terms = Utility(speed(shape, remaining), potential.retention, valueTerm, waitValue.quality() * 2, legality)
        return Evaluation(shape.shanten, live, points, terms.total(), waitValue, potential, terms)
    }

    /** One draw and best discard; unseen tiles are an exchangeable sampling approximation,
     * including opponents' tiles/dead wall, never a claim about the actual live wall. */
    fun forward(state: State, baseline: Evaluation, replacement: Boolean): Double {
        val distance = if (
            view.phase() == Game.Phase.REACTION &&
            state.melds().size == view.seats()[view.viewerSeat()].melds().size
        ) {
            Math.floorMod(view.viewerSeat() - view.turn(), view.rules().players())
        } else {
            view.rules().players()
        }
        if (!replacement && view.remaining() < maxOf(1, distance)) return baseline.utility
        if (!replacement && baseline.shanten == 1) {
            return advances.getOrPut(state) { advance(state, baseline, distance) }
        }
        val branches = unseen.count { it > 0 }
        if (drawNodes + branches > SEARCH_ROOTS * 37) return baseline.utility
        // Both endpoints use the same immediate evaluator, without another draw
        // hidden inside a good-shape analysis at the continuation leaves.
        val leafBaseline = baseline.utility
        var sum = 0.0
        var total = 0
        for (face in unseen.indices) {
            val count = unseen[face]
            if (count == 0) continue
            drawNodes++
            val remaining = unseen.clone()
            remaining[face]--
            val drawn = tile(face)
            val withDraw = state.draw(drawn)
            var best = Double.NEGATIVE_INFINITY
            if (baseline.shanten == 0 && Tile.kind(drawn) in shape(state).improving) {
                val win = value.score(state, drawn, true, replacement)
                if (win != null) {
                    sum += count * (120 + ln1p(value.payment(win) / 1000.0) * 16)
                    total += count
                    continue
                }
            }
            val shapes = if (state.riichi()) mapOf(Tile.kind(drawn) to shape(state)) else discards(withDraw)
            // Rank each structural continuation once. Every tenpai continuation
            // near readiness uses scoring; distant hands keep a two-leaf beam.
            val candidates = ArrayList<State>()
            val faces = HashSet<Int>()
            for (discard in withDraw.hand()) {
                if (state.riichi() && discard != drawn || !faces.add(face(discard))) continue
                candidates += withDraw.discard(discard, false)
            }
            val ready = if (baseline.shanten <= 1) candidates.filter { shapes[removedFace(withDraw, it) % 34]!!.shanten == 0 } else emptyList()
            val leaves = ready.ifEmpty {
                val order = compareByDescending<Pair<State, Double>> { it.second }
                    .thenBy { it.first.orderKey() }
                val upper = candidates.map { next ->
                    val candidateShape = shapes[removedFace(withDraw, next) % 34]!!
                    next to (speed(candidateShape, remaining) + value.rankUpper(next, candidateShape.shanten))
                }.sortedWith(order)
                val beam = ArrayList<Pair<State, Double>>(3)
                for ((next, ceiling) in upper) {
                    // This is a conservative numerical bound, not an extra beam
                    // cutoff. No omitted candidate can beat the retained top two.
                    if (beam.size == 2 && ceiling < beam[1].second) break
                    val candidateShape = shapes[removedFace(withDraw, next) % 34]!!
                    val potential = value.potential(next, candidateShape.shanten, remaining)
                    beam += next to (speed(candidateShape, remaining) + potential.retention + ln1p(potential.estimate / 1000) * 6)
                    beam.sortWith(order)
                    if (beam.size > 2) beam.removeAt(2)
                }
                beam.map { it.first }
            }
            for (next in leaves) {
                val candidateShape = shapes[removedFace(withDraw, next) % 34]!!
                best = maxOf(best, continuation(withDraw, next, candidateShape, remaining, replacement, distance))
            }
            sum += count * if (best.isFinite()) best else leafBaseline
            total += count
        }
        return if (total == 0) baseline.utility else baseline.utility + sum / total - leafBaseline
    }

    /** Every live improving face and every tenpai discard. Non-advancing draws
     * retain the root estimate; they do not invoke another beam or another ply. */
    private fun advance(state: State, baseline: Evaluation, distance: Int): Double {
        var gain = 0.0
        val improving = shape(state).improving
        for (face in unseen.indices) {
            val count = unseen[face]
            if (count == 0 || face % 34 !in improving) continue
            advanceNodes++
            val remaining = unseen.clone()
            remaining[face]--
            val withDraw = state.draw(tile(face))
            val shapes = bestDiscards.getOrPut(ShapeKey(withDraw)) {
                HandAnalyzer.bestDiscardEfficiency(withDraw.hand(), withDraw.melds())
            }
            val faces = HashSet<Int>()
            var best = Double.NEGATIVE_INFINITY
            for (discard in withDraw.hand()) {
                val shape = shapes[Tile.kind(discard)] ?: continue
                if (shape.shanten != 0 || !faces.add(face(discard))) continue
                tenpaiLeaves++
                best = maxOf(best, continuation(withDraw, withDraw.discard(discard, false), shape, remaining, false, distance))
            }
            if (best.isFinite()) gain += count * (best - baseline.utility)
        }
        return baseline.utility + gain / maxOf(1, unseen.sum())
    }

    private fun continuation(before: State, next: State, shape: TileEfficiency, remaining: IntArray,
                             replacement: Boolean, distance: Int): Double {
        val evaluated = evaluate(next, shape, remaining)
        var utility = evaluated.utility
        if (shape.shanten == 0 && !next.riichi() && next.melds().all { it.closed() } &&
            view.remaining() - (if (replacement) 1 else maxOf(1, distance)) >= view.rules().minRiichiWall() &&
            (!view.rules().needsRiichiDeposit() || view.seats()[view.viewerSeat()].points() >= 1000)) {
            val declared = State(next.hand(), next.melds(), next.norths(), next.river(), true, next.ronBlocked(), before.riichiHan())
            val ready = evaluate(declared, shape, remaining)
            utility = maxOf(utility, ready.utility - riichiCost(ready))
        }
        val discard = tile(removedFace(before, next))
        return utility - defence.penalty(discard, defence.mode(evaluated)) + defence.reserve(next)
    }

    fun riichiCost(hand: Evaluation): Double {
        val draws = maxOf(1.0, view.remaining() / view.rules().players().toDouble())
        // Conditional gain is weighed against a certain deposit and locked defence.
        // The exchangeable-draw chance is an approximation, not a calibrated win rate.
        val mass = maxOf(1, unseen.sum()).toDouble()
        val chance = 1 - (1 - minOf(.99, hand.waits.quality() / mass)).pow(draws)
        val deposit = if (view.rules().needsRiichiDeposit()) 10 * (1 - chance) else 0.0
        return deposit + defence.pressure() * 8 + 4 / draws
    }

    companion object {
        const val SEARCH_ROOTS = 3

        @JvmStatic
        fun face(tile: Int): Int = Tile.kind(tile) + if (Tile.red(tile)) 34 else 0

        fun tile(face: Int): Int = Tile.id(face % 34, 0, face >= 34)

        fun live(kinds: Set<Int>, remaining: IntArray): Int = kinds.sumOf { remaining[it] + remaining[it + 34] }

        private fun speed(shape: TileEfficiency, remaining: IntArray): Double {
            // One exchangeable draw can advance at most one shanten. Raw ukeire
            // must not be worth arbitrarily many steps in a smaller playing set.
            val mass = maxOf(1, remaining.sum()).toDouble()
            return 55 * (live(shape.improving, remaining) / mass - shape.shanten)
        }

        private fun removedFace(before: State, after: State): Int =
            after.removedFrom(before)
    }
}

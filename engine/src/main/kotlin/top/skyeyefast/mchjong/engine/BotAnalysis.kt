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
    private val advances = HashMap<State, Forecast>()

    @JvmField
    var drawNodes = 0

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

    @JvmRecord
    data class Forecast(
        val endpoint: Double,
        val danger: Double,
        val reserve: Double,
        val riichi: Double,
        val win: Double,
    ) {
        fun total(): Double = endpoint - danger + reserve - riichi + win
    }

    private data class ShapeKey(val hand: kotlin.collections.List<Int>, val melds: kotlin.collections.List<String>) {
        constructor(state: State) : this(
            state.hand().map { Tile.kind(it) }.sorted(),
            state.melds().map { MahjongUtilsInterop.meldNotation(it) }.sorted(),
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
        discards.getOrPut(ShapeKey(state)) { RiichiHandAnalyzer.discardEfficiency(state.hand(), state.melds(), false) }

    fun shape(state: State): TileEfficiency =
        hands.getOrPut(ShapeKey(state)) { RiichiHandAnalyzer.handEfficiency(state.hand(), state.melds(), false) }

    fun evaluate(state: State, shape: TileEfficiency, remaining: IntArray): Evaluation {
        val live = live(shape.improving, remaining)
        val key = ShapeKey(state)
        // HARD values all actual tenpai continuations instead of requesting the
        // library's separate nested good-shape enumeration for these same roots.
        val potential = value.potential(state, shape.shanten, remaining)
        hands.putIfAbsent(key, shape)
        val waitValue = if (shape.shanten == 0) {
            value.waits(state, waits.getOrPut(key) { RiichiHandAnalyzer.waits(state.hand(), state.melds()) }, remaining)
        } else {
            BotValue.Waits.EMPTY
        }
        val points = if (shape.shanten == 0) waitValue.average() else potential.estimate
        // Ordinal utilities, not fitted win/deal-in probabilities or expected monetary returns.
        val valueTerm = pointUtility(points, shape.shanten == 0) +
            if (shape.shanten == 0) minOf(8.0, waitValue.quality()) * view.riichiSticks() * 0.4 else 0.0
        val legality = (if (level != BotDifficulty.EASY && !potential.viable && shape.shanten > 0) -45.0 else 0.0) +
            if (shape.shanten == 0 && waitValue.quality() == 0.0) -55.0 else 0.0
        val terms = Utility(speed(shape, remaining), potential.retention, valueTerm, waitValue.quality() * 2, legality)
        return Evaluation(shape.shanten, live, points, terms.total(), waitValue, potential, terms)
    }

    /** One draw and best discard; unseen tiles are an exchangeable sampling approximation,
     * including opponents' tiles/dead wall, never a claim about the actual live wall. */
    private fun distance(state: State): Int = if (
            view.phase() == TableView.Phase.REACTION &&
            state.melds().size == view.seats()[view.viewerSeat()].melds().size
        ) {
            Math.floorMod(view.viewerSeat() - view.turn(), view.rules().players())
        } else {
            view.rules().players()
        }

    fun canReachNextTurn(state: State): Boolean = view.remaining() >= maxOf(1, distance(state))

    fun forward(state: State, baseline: Evaluation, replacement: Boolean): Forecast {
        val distance = distance(state)
        if (!replacement && !canReachNextTurn(state))
            return Forecast(baseline.utility, 0.0, defence.reserve(state), 0.0, 0.0)
        if (!replacement && baseline.shanten == 1) {
            return advances.getOrPut(state) { advance(state, baseline, distance) }
        }
        val branches = unseen.count { it > 0 }
        check(drawNodes + branches <= SEARCH_ROOTS * 37) { "General search exceeded its root budget" }
        // Both endpoints use the same immediate evaluator, without another draw
        // hidden inside a good-shape analysis at the continuation leaves.
        var endpoint = 0.0
        var danger = 0.0
        var reserve = 0.0
        var riichi = 0.0
        var win = 0.0
        var total = 0
        for (face in unseen.indices) {
            val count = unseen[face]
            if (count == 0) continue
            drawNodes++
            val remaining = unseen.clone()
            remaining[face]--
            val drawn = tile(face)
            val withDraw = state.draw(drawn)
            var best: Forecast? = null
            if (baseline.shanten == 0 && Tile.kind(drawn) in shape(state).improving) {
                val scored = value.score(state, drawn, true, replacement)
                if (scored != null) {
                    win += count * (120 + ln1p((value.winningPayment(state, drawn, scored, unseen) +
                        view.riichiSticks() * 1000) / 1000.0) * 16)
                    total += count
                    continue
                }
            }
            val shapes = if (state.riichi()) mapOf(Tile.kind(drawn) to shape(state)) else discards(withDraw)
            // Every ready continuation is scored. Other continuations retain
            // distinct speed, value and safety routes within the leaf budget.
            val candidates = ArrayList<Pair<State, TileEfficiency>>()
            val faces = HashSet<Int>()
            for (discard in withDraw.hand()) {
                if (state.riichi() && discard != drawn || !faces.add(face(discard))) continue
                candidates += withDraw.discard(discard, false) to shapes[Tile.kind(discard)]!!
            }
            val ready = if (baseline.shanten <= 1) candidates.filter { it.second.shanten == 0 } else emptyList()
            val leaves = if (ready.isEmpty()) selectLeaves(withDraw, candidates, remaining) else
                ready.map { (next, candidateShape) -> Triple(next, candidateShape, evaluate(next, candidateShape, remaining)) }
            for ((next, candidateShape, evaluated) in leaves) {
                val result = continuation(withDraw, next, candidateShape, evaluated, remaining, replacement, distance)
                if (best == null || result.total() > best.total()) best = result
            }
            val result = best ?: Forecast(baseline.utility, 0.0, defence.reserve(state), 0.0, 0.0)
            endpoint += count * result.endpoint
            danger += count * result.danger
            reserve += count * result.reserve
            riichi += count * result.riichi
            win += count * result.win
            total += count
        }
        if (total == 0) return Forecast(baseline.utility, 0.0, defence.reserve(state), 0.0, 0.0)
        return Forecast(endpoint / total, danger / total, reserve / total, riichi / total, win / total)
    }

    /** All advancing tenpai discards, plus bounded same-shanten improvements.
     * Keeping the drawn tile can improve a wait shape before it advances shanten. */
    private fun advance(state: State, baseline: Evaluation, distance: Int): Forecast {
        var endpoint = 0.0
        var danger = 0.0
        var reserve = 0.0
        var riichi = 0.0
        var total = 0
        val improving = shape(state).improving
        for (face in unseen.indices) {
            val count = unseen[face]
            if (count == 0) continue
            val remaining = unseen.clone()
            remaining[face]--
            val withDraw = state.draw(tile(face))
            val shapes = bestDiscards.getOrPut(ShapeKey(withDraw)) {
                RiichiHandAnalyzer.bestDiscardEfficiency(withDraw.hand(), withDraw.melds())
            }
            val faces = HashSet<Int>()
            var best: Forecast? = null
            val candidates = ArrayList<Pair<State, TileEfficiency>>()
            for (discard in withDraw.hand()) {
                val shape = shapes[Tile.kind(discard)] ?: continue
                if (!faces.add(face(discard))) continue
                candidates += withDraw.discard(discard, false) to shape
            }
            val leaves = if (face % 34 in improving) {
                candidates.map { (next, shape) -> Triple(next, shape, evaluate(next, shape, remaining)) }
            } else {
                val ranked = selectLeaves(withDraw, candidates, remaining)
                // Keep the unchanged hand as a value/defence baseline even when another
                // shape has more immediate improving tiles.
                val unchanged = withDraw.discard(tile(face), false)
                if (ranked.any { it.first == unchanged }) ranked else {
                    val unchangedShape = shape(state)
                    ranked + Triple(unchanged, unchangedShape, evaluate(unchanged, unchangedShape, remaining))
                }
            }
            for ((next, shape, evaluated) in leaves) {
                val result = continuation(withDraw, next, shape, evaluated, remaining, false, distance)
                if (best == null || result.total() > best.total()) best = result
            }
            val result = best ?: Forecast(baseline.utility, 0.0, defence.reserve(state), 0.0, 0.0)
            endpoint += count * result.endpoint
            danger += count * result.danger
            reserve += count * result.reserve
            riichi += count * result.riichi
            total += count
        }
        if (total == 0) return Forecast(baseline.utility, 0.0, defence.reserve(state), 0.0, 0.0)
        return Forecast(endpoint / total, danger / total, reserve / total, riichi / total, 0.0)
    }

    private fun selectLeaves(before: State, candidates: List<Pair<State, TileEfficiency>>,
                             remaining: IntArray): List<Triple<State, TileEfficiency, Evaluation>> {
        if (candidates.size <= 2) return candidates.map { (state, shape) -> Triple(state, shape, evaluate(state, shape, remaining)) }
        data class Leaf(val state: State, val shape: TileEfficiency, val evaluated: Evaluation,
                        val safety: Double) {
            fun offense(): Double = evaluated.utility - evaluated.terms.speed
            fun total(): Double = evaluated.utility + safety
        }
        val leaves = candidates.distinctBy { it.first }.map { (state, shape) ->
            val evaluated = evaluate(state, shape, remaining)
            val discarded = tile(removedFace(before, state))
            Leaf(state, shape, evaluated,
                defence.reserve(state) - defence.penalty(discarded, defence.mode(evaluated)))
        }
        fun dominates(first: Leaf, second: Leaf): Boolean =
            first.evaluated.shanten <= second.evaluated.shanten && first.evaluated.live >= second.evaluated.live &&
                first.offense() >= second.offense() && first.safety >= second.safety &&
                (first.evaluated.shanten < second.evaluated.shanten || first.evaluated.live > second.evaluated.live ||
                    first.offense() > second.offense() || first.safety > second.safety)
        val frontier = leaves.filter { leaf -> leaves.none { it !== leaf && dominates(it, leaf) } }
        val selected = mutableListOf(frontier.maxWithOrNull(compareBy<Leaf> { it.total() }.thenByDescending { it.state.orderKey() })!!)
        val dimensions: List<(Leaf) -> Double> = listOf(
            { it.evaluated.terms.speed }, { it.offense() }, { it.safety },
        )
        val ranges = dimensions.map { dimension -> frontier.maxOf(dimension) - frontier.minOf(dimension) }
        while (selected.size < 2) {
            val next = frontier.asSequence().filter { it !in selected }.maxWithOrNull(
                compareBy<Leaf> { leaf ->
                    dimensions.indices.maxOf { dimension ->
                        (dimensions[dimension](leaf) - selected.maxOf(dimensions[dimension])) /
                            maxOf(1.0, ranges[dimension])
                    }
                }.thenBy { it.total() }.thenByDescending { it.state.orderKey() },
            ) ?: break
            selected += next
        }
        return selected.map { Triple(it.state, it.shape, it.evaluated) }
    }

    private fun continuation(before: State, next: State, shape: TileEfficiency, evaluated: Evaluation, remaining: IntArray,
                             replacement: Boolean, distance: Int): Forecast {
        var utility = evaluated.utility
        var selected = evaluated
        var riichi = 0.0
        if (shape.shanten == 0 && !next.riichi() && next.melds().all { it.closed() } &&
            view.remaining() - (if (replacement) 1 else maxOf(1, distance)) >= view.rules().minRiichiWall() &&
            (!view.rules().needsRiichiDeposit() || view.seats()[view.viewerSeat()].points() >= 1000)) {
            val declared = State(next.hand(), next.melds(), next.norths(), next.river(), true, next.ronBlocked(), before.riichiHan())
            val ready = evaluate(declared, shape, remaining)
            val cost = riichiCost(ready, view.remaining() - (if (replacement) 1 else maxOf(1, distance)))
            if (ready.utility - cost > utility) {
                utility = ready.utility
                selected = ready
                riichi = cost
            }
        }
        val discard = tile(removedFace(before, next))
        return Forecast(utility, defence.penalty(discard, defence.mode(selected)), defence.reserve(next),
            riichi, 0.0)
    }

    fun riichiCost(hand: Evaluation, remaining: Int = view.remaining()): Double {
        val draws = maxOf(1.0, remaining / view.rules().players().toDouble())
        // Conditional gain is weighed against a certain deposit and locked defence.
        // The exchangeable-draw chance is an approximation, not a calibrated win rate.
        val mass = maxOf(1, unseen.sum()).toDouble()
        val chance = 1 - (1 - minOf(.99, hand.waits.quality() / mass)).pow(draws)
        // Price a forfeited deposit on the same payout scale as the candidate hand.
        val deposit = if (view.rules().needsRiichiDeposit())
            (pointUtility(hand.points, true) - pointUtility(maxOf(0.0, hand.points - 1000), true)) * (1 - chance) else 0.0
        return deposit + defence.pressure() * 8 + 4 / draws +
            if (defence.placementUrgency(hand.points) < 1) 5.0 else 0.0
    }

    private fun pointUtility(points: Double, ready: Boolean): Double =
        if (level == BotDifficulty.EASY) { if (ready) minOf(16.0, points / 500) else 0.0 }
        else ln1p(points / 1000) * if (ready) 16 else 6

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

package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.Action.Type.ABORT_NINE
import top.skyeyefast.mchjong.engine.Action.Type.ADDED_KAN
import top.skyeyefast.mchjong.engine.Action.Type.BUILD_WALL
import top.skyeyefast.mchjong.engine.Action.Type.CHI
import top.skyeyefast.mchjong.engine.Action.Type.CLOSED_KAN
import top.skyeyefast.mchjong.engine.Action.Type.DISCARD
import top.skyeyefast.mchjong.engine.Action.Type.DRAW
import top.skyeyefast.mchjong.engine.Action.Type.NEXT
import top.skyeyefast.mchjong.engine.Action.Type.NUKI
import top.skyeyefast.mchjong.engine.Action.Type.OPEN_KAN
import top.skyeyefast.mchjong.engine.Action.Type.PASS
import top.skyeyefast.mchjong.engine.Action.Type.PICK_UP_DICE
import top.skyeyefast.mchjong.engine.Action.Type.ROLL_DICE
import top.skyeyefast.mchjong.engine.Action.Type.PON
import top.skyeyefast.mchjong.engine.Action.Type.RIICHI
import top.skyeyefast.mchjong.engine.Action.Type.RON
import top.skyeyefast.mchjong.engine.Action.Type.SHUFFLE
import top.skyeyefast.mchjong.engine.Action.Type.SKIP_SETTLEMENT
import top.skyeyefast.mchjong.engine.Action.Type.TAKE_PACKET
import top.skyeyefast.mchjong.engine.Action.Type.TSUMO

/** Deterministic decisions from own/public information and engine-issued legal actions. */
internal class TrainingBot private constructor(
    private val view: TableView,
    private val level: BotDifficulty,
) {
    private val analysis = BotAnalysis(view, level)
    private val defence = analysis.defence
    private val initial = analysis.initial()

    @JvmRecord
    data class Adjustments(val action: Double, val danger: Double, val reserve: Double, val riichi: Double,
                           val callPressure: Double) {
        fun immediate(): Double = action - danger - riichi - callPressure
        fun total(): Double = immediate() + reserve
    }

    private data class Choice(
        val index: Int,
        val state: BotAnalysis.State,
        val evaluation: BotAnalysis.Evaluation,
        val discard: Int,
        val replacement: Boolean,
        val adjustment: Double,
        val key: String,
    )

    private fun choice(
        index: Int,
        state: BotAnalysis.State,
        shape: TileEfficiency,
        discard: Int,
        replacement: Boolean,
        adjustment: Double,
    ): Choice = Choice(
        index,
        state,
        analysis.evaluate(state, shape, analysis.unseen),
        discard,
        replacement,
        adjustment,
        stable(view.actions()[index]) + ":" + if (discard < 0) -1 else BotAnalysis.face(discard),
    )

    private fun choose(): Int {
        val choices = mutableListOf<Choice>()
        val unique = hashSetOf<String>()
        val shapes = if (initial.hand().size % 3 == 2) analysis.discards(initial) else emptyMap()
        val indices = view.actions().indices.sortedWith(
            compareBy<Int> { stable(view.actions()[it]) }
                .thenBy { view.actions()[it].tiles().sorted().toString() },
        )
        for (i in indices) {
            val action = view.actions()[i]
            if (!unique.add(stable(action))) continue
            when (action.type()) {
                DISCARD, RIICHI -> {
                    val tile = action.tiles().first()
                    val next = initial.discard(tile, action.type() == RIICHI)
                    choices += choice(i, next, shapes[Tile.kind(tile)]!!, tile, false, 0.0)
                }
                PASS -> choices += choice(i, initial, analysis.shape(initial), Tile.ABSENT, false, 0.0)
                CHI, PON -> addCall(choices, i, action)
                OPEN_KAN, CLOSED_KAN, ADDED_KAN, NUKI -> {
                    val next = declaration(action)
                    val shape = analysis.shape(next)
                    val cost = declarationCost(action, next)
                    choices += choice(i, next, shape, Tile.ABSENT, true, -cost)
                }
                else -> Unit
            }
        }
        if (choices.isEmpty()) throw IllegalStateException("No evaluated legal bot action")
        val finalDiscards = if (view.remaining() == 0 && view.phase() == TableView.Phase.TURN) {
            choices.filter { view.actions()[it.index].type() == DISCARD }
        } else emptyList()
        val baseline = choices.asSequence()
            .filter { view.actions()[it.index].type() == DISCARD || view.actions()[it.index].type() == PASS }
            .minWithOrNull(
                compareBy<Choice> { it.evaluation.shanten }
                    .thenByDescending { it.evaluation.utility }
                    .thenBy { it.key },
            ) ?: choices.first()
        val minimum = choices.asSequence()
            .filter { view.actions()[it.index].type() == DISCARD || view.actions()[it.index].type() == PASS }
            .minOfOrNull { it.evaluation.shanten } ?: baseline.evaluation.shanten
        val mode = defence.mode(baseline.evaluation)
        val abort = index(view.actions(), ABORT_NINE)
        if (abort >= 0 && baseline.evaluation.shanten >= 4 && baseline.evaluation.live < 18) return abort
        var fold: Choice? = null
        if (mode == BotDefence.Mode.FOLD && !initial.riichi()) {
            val pass = index(view.actions(), PASS)
            // Safety can break completed groups and increase shanten.
            fold = if (pass >= 0) {
                baseline
            } else {
                choices.asSequence()
                    .filter { view.actions()[it.index].type() == DISCARD }
                    .minWithOrNull(compareBy<Choice> { defence.danger(it.discard) }.thenByDescending { it.evaluation.utility }.thenBy { it.key })
                    ?: baseline
            }
            val safe = fold
            // Judge a call by the resulting hand. A valuable, fast continuation
            // can justify attacking even when the unchanged hand would fold.
            choices.removeIf { it !== safe && (!viable(it) || defence.mode(it.evaluation) != BotDefence.Mode.PUSH) }
            if (choices.size == 1) return exhaustiveChoice(safe, finalDiscards).index
        }
        val searchDecision = level == BotDifficulty.HARD || choices.any { it.replacement }
        // Reserve the unchanged alternative before call-discard branches consume
        // the bounded search. Replacement draws need a searched baseline at EASY too.
        val reserveBaseline = view.actions()[baseline.index].type() == PASS || choices.any { it.replacement }
        choices.sortWith(compareBy<Choice> { reserveBaseline && it !== baseline }
            .thenBy { reserveBaseline && !it.replacement }
            .thenByDescending { score(it) }.thenBy { it.key })
        val eligible = mutableListOf<Choice>()
        for (candidate in choices) {
            val type = view.actions()[candidate.index].type()
            if ((type == CHI || type == PON || type == OPEN_KAN) && !viable(candidate)) {
                continue
            }
            if (type == RIICHI && candidate.evaluation.waits.quality() == 0.0) {
                continue
            }
            if (candidate !== fold && candidate.evaluation.shanten > minimum + 1) {
                continue
            }
            // A larger raw ukeire count is not evidence that going backwards is
            // faster. Basic evaluators preserve an available viable route; HARD
            // must actually search a retreat, while dead/yakuless routes can escape.
            val safer = candidate.discard >= 0 && baseline.discard >= 0 &&
                defence.danger(candidate.discard) < defence.danger(baseline.discard)
            val retreat = candidate.evaluation.shanten > minimum && !candidate.replacement && candidate !== fold && !safer
            if (retreat && viable(baseline) && baseline.evaluation.live > 0 && !searchDecision) {
                continue
            }
            eligible += candidate
        }
        val general = eligible.filter { searchDecision && (it.replacement || analysis.canReachNextTurn(it.state)) &&
            !(it.evaluation.shanten == 1 && !it.replacement) }
        val frontier = general.filter { candidate -> general.none { it !== candidate && dominates(it, candidate) } }
        val roots = selectRoots(frontier, general, baseline, fold)
        var bestScore = Double.NEGATIVE_INFINITY
        var best = baseline
        for (candidate in eligible) {
            val nextTurn = candidate.replacement || analysis.canReachNextTurn(candidate.state)
            val oneShanten = searchDecision && nextTurn && candidate.evaluation.shanten == 1 && !candidate.replacement
            if (searchDecision && nextTurn && !oneShanten && candidate !in roots) continue
            val forecast = if (searchDecision && nextTurn) analysis.forward(candidate.state, candidate.evaluation, candidate.replacement)
                else BotAnalysis.Forecast(candidate.evaluation.utility, 0.0, defence.reserve(candidate.state), 0.0, 0.0)
            val candidateScore = adjustments(candidate).immediate() + forecast.total()
            if (candidateScore > bestScore || candidateScore == bestScore && candidate.key < best.key) {
                bestScore = candidateScore
                best = candidate
            }
        }
        return exhaustiveChoice(best, finalDiscards).index
    }

    private fun exhaustiveChoice(selected: Choice, discards: List<Choice>): Choice {
        if (discards.isEmpty() || view.actions()[selected.index].type() != DISCARD) return selected
        val own = view.seats()[view.viewerSeat()]
        val otherNagashi = view.seats().indices.any { seat ->
            val player = view.seats()[seat]
            seat != view.viewerSeat() && Settlement.nagashiEligible(view.rules(), player.melds(), player.river())
        }
        fun settlement(candidate: Choice): Int {
            val river = own.river() + Discard(candidate.discard, false, false, false)
            if (Settlement.nagashiEligible(view.rules(), candidate.state.melds(), river)) return 2
            return if (!otherNagashi && LegalActions.formalTenpai(candidate.state.hand(), candidate.state.melds(), view.rules())) 1 else 0
        }
        // No future draws remain. Prefer a better draw settlement only when the
        // discard is no more dangerous; dead/yakuless waits still count as tenpai.
        val current = settlement(selected)
        val danger = defence.danger(selected.discard)
        val better = discards.filter { defence.danger(it.discard) <= danger && settlement(it) > current }
            .minWithOrNull(compareByDescending<Choice> { settlement(it) }
                .thenBy { defence.danger(it.discard) }.thenByDescending { score(it) }.thenBy { it.key })
            ?: return selected
        return better
    }

    private fun offense(candidate: Choice): Double = candidate.evaluation.utility - candidate.evaluation.terms.speed

    private fun safety(candidate: Choice): Double = adjustments(candidate).total()

    private fun dominates(first: Choice, second: Choice): Boolean {
        if (first.replacement != second.replacement) return false
        val a = first.evaluation
        val b = second.evaluation
        return a.shanten <= b.shanten && a.live >= b.live && offense(first) >= offense(second) &&
            safety(first) >= safety(second) &&
            (a.shanten < b.shanten || a.live > b.live || offense(first) > offense(second) || safety(first) > safety(second))
    }

    private fun selectRoots(frontier: List<Choice>, all: List<Choice>, baseline: Choice, fold: Choice?): Set<Choice> {
        val selected = linkedSetOf<Choice>()
        if (baseline in all && (view.actions()[baseline.index].type() == PASS || all.any { it.replacement })) selected += baseline
        if (fold in all) selected += fold!!
        all.filter { it.replacement }.maxWithOrNull(compareBy<Choice> { score(it) }.thenByDescending { it.key })
            ?.let { selected += it }
        val dimensions: List<(Choice) -> Double> = listOf(
            { it.evaluation.terms.speed }, ::offense, ::safety,
        )
        val ranges = dimensions.map { dimension ->
            (frontier.maxOfOrNull(dimension) ?: 0.0) - (frontier.minOfOrNull(dimension) ?: 0.0)
        }
        if (selected.isEmpty()) frontier.maxWithOrNull(compareBy<Choice> { score(it) }.thenByDescending { it.key })
            ?.let { selected += it }
        while (selected.size < BotAnalysis.SEARCH_ROOTS) {
            val next = frontier.asSequence().filter { it !in selected }.maxWithOrNull(
                compareBy<Choice> { candidate ->
                    dimensions.indices.maxOf { dimension ->
                        val best = selected.maxOfOrNull(dimensions[dimension]) ?: Double.NEGATIVE_INFINITY
                        (dimensions[dimension](candidate) - best) / maxOf(1.0, ranges[dimension])
                    }
                }.thenBy { score(it) }.thenByDescending { it.key },
            ) ?: break
            selected += next
        }
        return selected
    }

    private fun viable(candidate: Choice): Boolean =
        if (candidate.evaluation.shanten == 0) candidate.evaluation.waits.quality() > 0 else candidate.evaluation.potential.viable

    private fun score(candidate: Choice): Double = candidate.evaluation.utility + adjustments(candidate).total()

    private fun adjustments(candidate: Choice): Adjustments {
        val type = view.actions()[candidate.index].type()
        // PASS and the post-call discard already compare speed, live stock,
        // routes, payments and the conditional closed option. Only public threat
        // pressure remains here, rather than charging a fixed opening tax again.
        return Adjustments(candidate.adjustment,
            if (candidate.discard >= 0) defence.penalty(candidate.discard, defence.mode(candidate.evaluation)) else 0.0,
            defence.reserve(candidate.state),
            if (type == RIICHI) analysis.riichiCost(candidate.evaluation) else 0.0,
            if (type == CHI || type == PON) defence.pressure() * 2 else 0.0)
    }

    private fun addCall(choices: MutableList<Choice>, index: Int, action: Action) {
        val next = declaration(action)
        val forbidden = LegalActions.forbiddenAfterCall(action, view.focus().tile())
        val shapes = analysis.discards(next)
        val faces = hashSetOf<Int>()
        for (tile in next.hand()) {
            if (Tile.kind(tile) in forbidden || !faces.add(BotAnalysis.face(tile))) continue
            choices += choice(index, next.discard(tile, false), shapes[Tile.kind(tile)]!!, tile, false, 0.0)
        }
    }

    private fun declaration(action: Action): BotAnalysis.State {
        val hand = initial.hand().toMutableList()
        for (tile in action.tiles()) hand.remove(tile)
        val melds = initial.melds().toMutableList()
        val norths = initial.norths().toMutableList()
        when (action.type()) {
            NUKI -> norths.addAll(action.tiles())
            ADDED_KAN -> {
                for (i in melds.indices) {
                    val old = melds[i]
                    if (old.type() == Meld.Type.TRIPLET && old.kind() == Tile.kind(action.tiles().first())) {
                        val tiles = old.tiles().toMutableList()
                        tiles.addAll(action.tiles())
                        melds[i] = Meld(Meld.Type.ADDED_QUAD, tiles, old.fromSeat(), old.calledTile())
                        break
                    }
                }
            }
            else -> {
                val closed = action.type() == CLOSED_KAN
                val tiles = action.tiles().toMutableList()
                if (!closed) tiles += view.focus().tile()
                tiles.sortWith(Tile.ORDER)
                melds += Meld(
                    action.type().meldType(),
                    tiles,
                    if (closed) view.viewerSeat() else view.focus().seat(),
                    if (closed) Tile.ABSENT else view.focus().tile(),
                )
            }
        }
        val blocked = initial.ronBlocked() && (initial.riichi() || !view.rules().callsClearFuriten())
        return BotAnalysis.State(
            hand,
            melds,
            norths,
            initial.river(),
            initial.riichi(),
            blocked,
            if (initial.riichi()) initial.riichiHan() else 1,
        )
    }

    private fun declarationCost(action: Action, after: BotAnalysis.State): Double {
        val tile = action.tiles().first()
        val nuki = action.type() == NUKI
        var risk = if (action.type() == OPEN_KAN) 0.0 else defence.danger(tile)
        if (action.type() == CLOSED_KAN) {
            risk *= if (view.rules().robConcealedKan() && Tile.terminalOrHonor(Tile.kind(tile))) 0.08 else 0.0
        }
        if (nuki && !view.rules().robNorthWithoutKokushi()) risk *= 0.08
        var cost = risk * 15 + if (nuki) 0.0 else defence.pressure() * 3
        if (!nuki && view.rules().kanDora()) cost += newIndicatorCost(after)
        return cost
    }

    private fun newIndicatorCost(after: BotAnalysis.State): Double {
        // Unknown indicators are averaged over unseen faces, never revealed in advance.
        val total = analysis.unseen.sum()
        var own = 0.0
        var opponents = 0.0
        for (face in analysis.unseen.indices) {
            if (analysis.unseen[face] == 0) continue
            val kind = Tile.doraAfter(face % 34, view.rules().sanma())
            val weight = analysis.unseen[face] / maxOf(1, total).toDouble()
            own += weight * (
                after.hand().count { Tile.kind(it) == kind } +
                    after.melds().sumOf { meld -> meld.tiles().count { Tile.kind(it) == kind } } +
                    after.norths().count { Tile.kind(it) == kind }
                )
            for (threat in defence.threats) {
                val opponent = view.seats()[threat.seat]
                val publicCount = opponent.melds().sumOf { meld -> meld.tiles().count { Tile.kind(it) == kind } } +
                    opponent.norths().count { Tile.kind(it) == kind }
                val concealed = opponent.hand().size * (analysis.unseen[kind] + analysis.unseen[kind + 34]) /
                    maxOf(1, total).toDouble()
                opponents += weight * threat.pressure * (publicCount + concealed)
            }
        }
        return opponents * 5 - own * 3
    }

    companion object {
        @JvmStatic
        fun choose(view: TableView, level: BotDifficulty): Int {
            if (view.actions().isEmpty()) throw IllegalArgumentException("A bot needs a legal decision")
            for (type in listOf(RON, TSUMO, SKIP_SETTLEMENT, NEXT, SHUFFLE, BUILD_WALL, PICK_UP_DICE, ROLL_DICE, TAKE_PACKET, DRAW)) {
                val index = index(view.actions(), type)
                if (index >= 0) return index
            }
            if (view.actions().size == 1) return 0
            return TrainingBot(view, level).choose()
        }

        private fun index(actions: List<Action>, type: Action.Type): Int = actions.indexOfFirst { it.type() == type }

        private fun stable(action: Action): String =
            action.type().name + action.tiles().map(BotAnalysis::face).sorted()
    }
}

package top.skyeyefast.mchjong.engine

import top.skyeyefast.mchjong.engine.Action.Type.ABORT_NINE
import top.skyeyefast.mchjong.engine.Action.Type.ADDED_KAN
import top.skyeyefast.mchjong.engine.Action.Type.BUILD_WALL
import top.skyeyefast.mchjong.engine.Action.Type.CHI
import top.skyeyefast.mchjong.engine.Action.Type.CLOSED_KAN
import top.skyeyefast.mchjong.engine.Action.Type.DISCARD
import top.skyeyefast.mchjong.engine.Action.Type.DRAW
import top.skyeyefast.mchjong.engine.Action.Type.DRAW_WIND
import top.skyeyefast.mchjong.engine.Action.Type.NEXT
import top.skyeyefast.mchjong.engine.Action.Type.NUKI
import top.skyeyefast.mchjong.engine.Action.Type.OPEN_KAN
import top.skyeyefast.mchjong.engine.Action.Type.PASS
import top.skyeyefast.mchjong.engine.Action.Type.PON
import top.skyeyefast.mchjong.engine.Action.Type.READY
import top.skyeyefast.mchjong.engine.Action.Type.RIICHI
import top.skyeyefast.mchjong.engine.Action.Type.RON
import top.skyeyefast.mchjong.engine.Action.Type.SHUFFLE
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
                    val opportunity = minOf(8.0, BotAnalysis.live(shape.improving, analysis.unseen) * 0.25)
                    choices += choice(i, next, shape, Tile.ABSENT, true, opportunity - cost)
                }
                else -> Unit
            }
        }
        if (choices.isEmpty()) throw IllegalStateException("No evaluated legal bot action")
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
            if (choices.size == 1) return safe.index
        }
        choices.sortWith(compareByDescending<Choice> { score(it) }.thenBy { it.key })
        var bestScore = Double.NEGATIVE_INFINITY
        var best = baseline
        var roots = 0
        for (candidate in choices) {
            val type = view.actions()[candidate.index].type()
            if ((type == CHI || type == PON || type == OPEN_KAN) && !viable(candidate)) continue
            if (type == RIICHI && candidate.evaluation.waits.quality() == 0.0) continue
            if (candidate !== fold && candidate.evaluation.shanten > minimum + 1) continue
            // A larger raw ukeire count is not evidence that going backwards is
            // faster. Basic evaluators preserve an available viable route; HARD
            // must actually search a retreat, while dead/yakuless routes can escape.
            val safer = candidate.discard >= 0 && baseline.discard >= 0 &&
                defence.danger(candidate.discard) < defence.danger(baseline.discard)
            val retreat = candidate.evaluation.shanten > minimum && !candidate.replacement && candidate !== fold && !safer
            if (
                retreat && viable(baseline) && baseline.evaluation.live > 0 &&
                (level != BotDifficulty.HARD || roots >= BotAnalysis.SEARCH_ROOTS)
            ) continue
            var candidateScore = score(candidate)
            val expand = candidate !== fold && (level == BotDifficulty.HARD || candidate.replacement)
            if (expand && roots < BotAnalysis.SEARCH_ROOTS && candidate.evaluation.shanten <= minimum + 1) {
                val forward = analysis.forward(candidate.state, candidate.evaluation, candidate.replacement)
                candidateScore += forward - candidate.evaluation.utility -
                    if (candidate.replacement) minOf(8.0, candidate.evaluation.live * 0.25) else 0.0
                roots++
            }
            if (candidateScore > bestScore || candidateScore == bestScore && candidate.key < best.key) {
                bestScore = candidateScore
                best = candidate
            }
        }
        return best.index
    }

    private fun viable(candidate: Choice): Boolean =
        if (candidate.evaluation.shanten == 0) candidate.evaluation.waits.quality() > 0 else analysis.value.potential(candidate.state).viable

    private fun score(candidate: Choice): Double {
        var score = candidate.evaluation.utility + candidate.adjustment
        if (candidate.discard >= 0) score -= defence.penalty(candidate.discard, defence.mode(candidate.evaluation))
        score += defence.reserve(candidate.state)
        val type = view.actions()[candidate.index].type()
        if (type == RIICHI) {
            score -= analysis.riichiCost(candidate.evaluation) +
                if (defence.placementUrgency(candidate.evaluation.points) < 1) 5 else 0
        }
        if (type == CHI || type == PON || type == OPEN_KAN) {
            // A closed hand retains a future riichi/tsumo path and defensive options.
            if (initial.melds().all { it.closed() }) score -= if (level == BotDifficulty.EASY) 4 else 8
            score -= defence.pressure() * 2
        }
        if (candidate.evaluation.shanten == 0) {
            score += minOf(8.0, candidate.evaluation.waits.quality()) * view.riichiSticks() * 0.4
        }
        return score
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
                    if (old.type() == Meld.Type.PON && old.kind() == Tile.kind(action.tiles().first())) {
                        val tiles = old.tiles().toMutableList()
                        tiles.addAll(action.tiles())
                        melds[i] = Meld(Meld.Type.ADDED_KAN, tiles, old.fromSeat(), old.calledTile())
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
                    Meld.Type.valueOf(action.type().name),
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
            for (type in listOf(RON, TSUMO, NEXT, READY, DRAW_WIND, SHUFFLE, BUILD_WALL, TAKE_PACKET, DRAW)) {
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

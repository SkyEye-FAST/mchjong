package top.skyeyefast.mchjong.engine

import kotlin.math.ln1p

/** Complete hands use the engine scorer. Incomplete hands have explicitly heuristic potential. */
internal class BotValue(private val view: TableView) {
    @JvmField
    val dora: IntArray = HandBonuses.indicators(view.wall(), view.rules().sanma())

    private val scores = HashMap<ScoreKey, HandScore?>()
    private val potentialPayments = HashMap<Int, Double>()
    private val routes = BotYakuPotential(view.rules(), ::yakuhai)

    private data class ScoreKey(
        val hand: List<Int>,
        val melds: List<String>,
        val bonus: Int,
        val winning: Int,
        val tsumo: Boolean,
        val riichiHan: Int,
        val replacement: Boolean,
    )

    @JvmRecord
    data class Potential(val viable: Boolean, val estimate: Double, val retention: Double,
                         val routes: BotYakuPotential.Assessment, val closedOption: Double)

    @JvmRecord
    data class Waits(val ron: Double, val tsumo: Double, val ronTiles: Int, val tsumoTiles: Int) {
        fun quality(): Double = (ronTiles + tsumoTiles) / 2.0

        fun average(): Double = (ron + tsumo) / maxOf(1, ronTiles + tsumoTiles)

        companion object {
            val EMPTY = Waits(0.0, 0.0, 0, 0)
        }
    }

    fun bonus(tile: Int): Int = HandBonuses.tile(tile, dora)

    fun bonus(state: BotAnalysis.State, winning: Int): Int =
        HandBonuses.count(state.hand(), state.melds(), state.norths(), winning, dora)

    fun wind(): Int = Math.floorMod(view.viewerSeat() - view.dealer(), view.rules().players())

    fun yakuhai(kind: Int): Int =
        (if (kind >= Tile.WHITE) 1 else 0) +
            (if (kind == Tile.EAST + wind()) 1 else 0) +
            (if (kind == Tile.EAST + view.round() / view.rules().players()) 1 else 0)

    fun potential(state: BotAnalysis.State, shanten: Int, remaining: IntArray): Potential {
        val bonuses = bonus(state, Tile.ABSENT)
        // Ready hands use legal waits, not a second speculative yaku reward.
        if (shanten == 0) return Potential(true, 0.0, bonuses * 3.0, BotYakuPotential.Assessment.EMPTY, 0.0)
        val assessment = routes.assess(state, remaining)
        val closed = state.melds().all { it.closed() }
        val canRiichi = closed && view.remaining() >= view.rules().minRiichiWall() + view.rules().players() * shanten &&
            (!view.rules().needsRiichiDeposit() || view.seats()[view.viewerSeat()].points() >= 1000)
        val option = if (state.riichi()) state.riichiHan().toDouble() else if (canRiichi) 0.35 / (1 + 0.25 * shanten) else 0.0
        val viable = minOf(assessment.attainableHan, assessment.han) + (if (canRiichi) 1 else 0) >= view.rules().minHan()
        val potentialHan = assessment.han + option + bonuses
        val estimate = if (viable) {
            // Cache point-table endpoints, not each continuously varying route
            // estimate. Interpolation remains identical to HandAnalyzer's scenarios.
            val lower = kotlin.math.floor(potentialHan).toInt().coerceAtLeast(1)
            val fraction = (potentialHan - lower).coerceIn(0.0, 1.0)
            estimatedPayment(lower) * (1 - fraction) + estimatedPayment(lower + 1) * fraction
        } else {
            0.0
        }
        // Keeping the declaration option matters at both levels, including EASY
        // where incomplete-hand payout is deliberately absent from utility.
        return Potential(viable, estimate, minOf(24.0, (assessment.han + option) * 7) +
            (if (viable) bonuses * 3.0 else 0.0), assessment, option)
    }

    private fun estimatedPayment(han: Int): Double = potentialPayments.getOrPut(han) {
        HandAnalyzer.estimatedPayment(han.toDouble(), wind() == 0, false, view.rules())
    }

    /** Upper bound for the general beam's retention/potential ranking only.
     * Every supported plan is at most 13 estimated yaku han; an established
     * declaration adds at most two. Legal tenpai scoring bypasses this beam. */
    fun rankUpper(state: BotAnalysis.State, shanten: Int): Double {
        val bonuses = bonus(state, Tile.ABSENT)
        if (shanten == 0) return bonuses * 3.0
        return 24 + bonuses * 3.0 + ln1p(estimatedPayment(15 + bonuses) / 1000) * 6
    }

    fun score(state: BotAnalysis.State, winning: Int, tsumo: Boolean, replacement: Boolean): HandScore? {
        val bonus = bonus(state, winning)
        val key = ScoreKey(
            state.hand().map { BotAnalysis.face(it) }.sorted(),
            state.melds().map { it.libraryNotation() }.sorted(),
            bonus,
            BotAnalysis.face(winning),
            tsumo,
            if (state.riichi()) state.riichiHan() else 0,
            replacement,
        )
        val extra = buildList {
            if (state.riichi()) add(if (state.riichiHan() == 2) "WRichi" else "Richi")
            if (replacement && tsumo) add("Rinshan")
        }
        if (!scores.containsKey(key)) {
            scores[key] = HandAnalyzer.score(
                state.hand(),
                state.melds(),
                winning,
                tsumo,
                wind(),
                view.round() / view.rules().players(),
                bonus,
                extra,
                view.rules(),
            )
        }
        return scores[key]
    }

    fun payment(score: HandScore): Int {
        if (score.ron() > 0) return score.ron()
        return if (wind() == 0) {
            score.tsumoDealer() * (view.rules().players() - 1)
        } else {
            score.tsumoDealer() + score.tsumoChild() * (view.rules().players() - 2)
        }
    }

    fun waits(state: BotAnalysis.State, kinds: Set<Int>, remaining: IntArray): Waits {
        // Any discarded structural wait makes the entire ron wait set furiten, including
        // waits that fail a custom yaku minimum. Tsumo is evaluated independently.
        val furiten = state.ronBlocked() || kinds.any { state.river() and (1L shl it) != 0L }
        var ron = 0.0
        var tsumo = 0.0
        var ronTiles = 0
        var tsumoTiles = 0
        for (kind in kinds) {
            for (face in intArrayOf(kind, kind + 34)) {
                val count = remaining[face]
                if (count == 0) continue
                val tile = BotAnalysis.tile(face)
                val ronScore = if (furiten) null else score(state, tile, false, false)
                val tsumoScore = score(state, tile, true, false)
                if (ronScore != null) {
                    ron += count * payment(ronScore)
                    ronTiles += count
                }
                if (tsumoScore != null) {
                    tsumo += count * payment(tsumoScore)
                    tsumoTiles += count
                }
            }
        }
        return Waits(ron, tsumo, ronTiles, tsumoTiles)
    }
}

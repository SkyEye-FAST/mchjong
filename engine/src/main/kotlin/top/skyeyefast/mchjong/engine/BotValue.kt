package top.skyeyefast.mchjong.engine

/** Complete hands use the engine scorer. Incomplete hands have explicitly heuristic potential. */
internal class BotValue(private val view: RiichiView) {
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
        // The payout is conditional on completing the closed hand and declaring.
        // Distance discounts the retained option, not the han of that declaration.
        val payoutOption = if (state.riichi()) state.riichiHan().toDouble() else if (canRiichi) 1.0 else 0.0
        val potentialHan = assessment.han + payoutOption + bonuses
        val estimate = if (viable) {
            // Cache point-table endpoints, not each continuously varying route
            // estimate. Interpolation remains identical to RiichiHandAnalyzer's scenarios.
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
        RiichiHandAnalyzer.estimatedPayment(han.toDouble(), wind() == 0, false, view.rules())
    }

    fun score(state: BotAnalysis.State, winning: Int, tsumo: Boolean, replacement: Boolean): HandScore? {
        val bonus = bonus(state, winning)
        val key = ScoreKey(
            state.hand().map { BotAnalysis.face(it) }.sorted(),
            state.melds().map { MahjongUtilsInterop.meldNotation(it) }.sorted(),
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
            scores[key] = RiichiHandAnalyzer.score(
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

    fun winningPayment(state: BotAnalysis.State, winning: Int, score: HandScore, remaining: IntArray): Double =
        expected(score, if (state.riichi() && view.rules().uraDora()) uraDistribution(state, winning, remaining) else doubleArrayOf(1.0))

    private fun expected(score: HandScore, ura: DoubleArray): Double = ura.indices.sumOf { bonus ->
        ura[bonus] * RiichiHandAnalyzer.bonusPayment(score, bonus, wind() == 0, view.rules())
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
                val ura = if (state.riichi() && view.rules().uraDora()) uraDistribution(state, tile, remaining) else doubleArrayOf(1.0)
                if (ronScore != null) {
                    ron += count * expected(ronScore, ura)
                    ronTiles += count
                }
                if (tsumoScore != null) {
                    tsumo += count * expected(tsumoScore, ura)
                    tsumoTiles += count
                }
            }
        }
        return Waits(ron, tsumo, ronTiles, tsumoTiles)
    }

    /** Sample concealed indicators without replacement from exchangeable unseen tiles.
     * Only the winning tile is removed; neither hidden wall identities nor opponent hands enter. */
    private fun uraDistribution(state: BotAnalysis.State, winning: Int, remaining: IntArray): DoubleArray {
        val indicators = view.wall().count { it >= 0 }
        if (indicators == 0) return doubleArrayOf(1.0)
        val complete = IntArray(34)
        state.hand().forEach { complete[Tile.kind(it)]++ }
        state.melds().forEach { meld -> meld.tiles().forEach { complete[Tile.kind(it)]++ } }
        state.norths().forEach { complete[Tile.kind(it)]++ }
        complete[Tile.kind(winning)]++
        val buckets = IntArray(5)
        for (kind in 0 until 34) {
            val count = remaining[kind] + remaining[kind + 34] - if (kind == Tile.kind(winning)) 1 else 0
            if (count > 0) buckets[complete[Tile.doraAfter(kind, view.rules().sanma())]] += count
        }
        val draws = minOf(indicators, buckets.sum())
        if (draws == 0) return doubleArrayOf(1.0)
        if (draws == 1) return buckets.map { it / buckets.sum().toDouble() }.toDoubleArray()
        var ways = Array(draws + 1) { DoubleArray(draws * 4 + 1) }
        ways[0][0] = 1.0
        for (bonus in buckets.indices) {
            val next = Array(draws + 1) { DoubleArray(draws * 4 + 1) }
            for (used in 0..draws) for (han in ways[used].indices) {
                if (ways[used][han] == 0.0) continue
                var combinations = 1.0
                for (take in 0..minOf(buckets[bonus], draws - used)) {
                    next[used + take][han + take * bonus] += ways[used][han] * combinations
                    combinations *= (buckets[bonus] - take).toDouble() / (take + 1)
                }
            }
            ways = next
        }
        val total = ways[draws].sum()
        return ways[draws].map { it / total }.toDoubleArray()
    }
}

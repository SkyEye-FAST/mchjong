package top.skyeyefast.mchjong.engine

/** Complete hands use the engine scorer. Incomplete hands have explicitly heuristic potential. */
internal class BotValue(private val view: TableView) {
    @JvmField
    val dora: IntArray = HandBonuses.indicators(view.wall(), view.rules().sanma())

    private val scores = HashMap<ScoreKey, HandScore?>()
    private val potentialPayments = HashMap<Double, Double>()

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
    data class Potential(val viable: Boolean, val estimate: Double, val retention: Double)

    @JvmRecord
    data class Waits(val ron: Double, val tsumo: Double, val ronTiles: Int, val tsumoTiles: Int) {
        fun quality(): Double = (ronTiles + tsumoTiles) / 2.0

        fun average(): Double = (ron + tsumo) / maxOf(1, ronTiles + tsumoTiles)

        companion object {
            @JvmField
            val EMPTY = Waits(0.0, 0.0, 0, 0)
        }
    }

    fun bonus(tile: Int): Int = HandBonuses.tile(tile, dora)

    fun bonus(state: BotAnalysis.State, winning: Int): Int =
        HandBonuses.count(state.hand(), state.melds(), state.norths(), winning, dora)

    private fun all(state: BotAnalysis.State): List<Int> = buildList {
        addAll(state.hand())
        state.melds().forEach { addAll(it.tiles()) }
    }

    fun wind(): Int = Math.floorMod(view.viewerSeat() - view.dealer(), view.rules().players())

    fun yakuhai(kind: Int): Int =
        (if (kind >= Tile.WHITE) 1 else 0) +
            (if (kind == Tile.EAST + wind()) 1 else 0) +
            (if (kind == Tile.EAST + view.round() / view.rules().players()) 1 else 0)

    fun potential(state: BotAnalysis.State): Potential {
        val all = all(state)
        val counts = IntArray(34)
        all.forEach { counts[Tile.kind(it)]++ }
        val closed = state.melds().all { it.closed() }
        var han = 0.0
        for (kind in 27 until 34) {
            if (counts[kind] >= 2) han += yakuhai(kind) * if (counts[kind] >= 3) 1.0 else 0.45
        }
        if ((closed || view.rules().kuitan()) && all.none { Tile.terminalOrHonor(Tile.kind(it)) }) han++
        for (suit in 0 until 3) {
            var off = 0
            var suited = 0
            for (kind in 0 until 27) {
                if (kind / 9 == suit) suited += counts[kind] else off += counts[kind]
            }
            val compatible = state.melds().asSequence().flatMap { it.tiles().asSequence() }
                .all { Tile.kind(it) >= 27 || Tile.kind(it) / 9 == suit }
            if (compatible && suited >= 7 && off <= 2) han += (if (closed) 3 else 2) * (3 - off) / 3.0
        }
        if (closed && state.melds().isEmpty() && counts.count { it >= 2 } >= 5) han += 1.5
        val bonuses = bonus(state, Tile.ABSENT)
        // Retained bonuses have value only alongside a plausible yaku path; none satisfy minHan.
        val viable = han + (if (closed) 1.0 else 0.0) >= view.rules().minHan()
        val potentialHan = han + (if (closed) 1.0 else 0.0) + bonuses
        val estimate = if (viable) {
            potentialPayments.getOrPut(potentialHan) {
                HandAnalyzer.estimatedPayment(potentialHan, wind() == 0, false, view.rules())
            }
        } else {
            0.0
        }
        return Potential(viable, estimate, minOf(8.0, han) * 2 + bonuses * 3)
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

package top.skyeyefast.mchjong.engine

/** Public per-opponent evidence. Risk, pressure and estimated value are ordinal heuristics. */
internal class BotDefence(
    private val view: TableView,
    private val level: BotDifficulty,
    private val value: BotValue,
    unseen: IntArray,
) {
    enum class Mode { PUSH, CAUTIOUS, FOLD }

    @JvmRecord
    data class Threat(
        val seat: Int,
        val river: Long,
        val pressure: Double,
        val value: Double,
        val closed: Boolean,
        val riichi: Boolean,
    )

    private val known = VisibleTiles.counts(view)
    @JvmField
    val threats = mutableListOf<Threat>()
    private val risks = Array(view.rules().players()) { DoubleArray(34) }

    init {
        val total = unseen.sum()
        var bonusMass = 0.0
        unseen.forEachIndexed { face, count ->
            if (count > 0) bonusMass += count * value.bonus(BotAnalysis.tile(face))
        }
        for (seat in view.seats().indices) {
            if (seat == view.viewerSeat()) continue
            val opponent = view.seats()[seat]
            var river = 0L
            opponent.river().forEach { river = river or (1L shl Tile.kind(it.tile())) }
            val open = opponent.melds().count { !it.closed() }
            var han = if (opponent.riichi()) 1 else 0
            var bonus = opponent.norths().size
            opponent.norths().forEach { bonus += value.bonus(it) }
            val wind = Tile.EAST + Math.floorMod(seat - view.dealer(), view.rules().players())
            opponent.melds().forEach { meld ->
                meld.tiles().forEach { bonus += value.bonus(it) }
                if (meld.type() != Meld.Type.CHI) {
                    if (meld.kind() >= Tile.WHITE) han++
                    if (meld.kind() == wind) han++
                    if (meld.kind() == Tile.EAST + view.round() / view.rules().players()) han++
                }
            }
            // Visible yaku/bonus content and elapsed turns strengthen an open-hand signal;
            // neither calls nor discards prove tenpai or concealed yaku.
            val progress = minOf(1.0, opponent.river().size / 16.0)
            var pressure = if (opponent.riichi()) {
                1.0
            } else if (open == 0) {
                maxOf(0.0, progress - 0.65) * 0.5
            } else {
                minOf(0.9, open * 0.14 + progress * 0.28 + (if (han > 0) 0.12 else 0.0) + minOf(4, bonus) * 0.04)
            }
            if (level == BotDifficulty.EASY && !opponent.riichi() && !(open >= 2 && han > 0 && bonus >= 2)) {
                pressure = 0.0
            }
            // Conditional payout scenarios use actual point tables. Concealed
            // bonuses are an exchangeable estimate from public remaining counts,
            // never an inspection of concealed identities or an asserted han.
            val hiddenBonus = opponent.hand().size * bonusMass / maxOf(1, total)
            val estimate = HandAnalyzer.estimatedPayment(
                maxOf(view.rules().minHan().toDouble(), han.toDouble()) + bonus + hiddenBonus,
                seat == view.dealer(),
                true,
                view.rules(),
            )
            val threat = Threat(seat, river, pressure, estimate, open == 0, opponent.riichi())
            threats += threat
            for (kind in 0 until 34) risks[seat][kind] = risk(threat, kind)
        }
    }

    private fun contains(mask: Long, kind: Int): Boolean = mask and (1L shl kind) != 0L

    private fun risk(threat: Threat, kind: Int): Double {
        if (contains(threat.river, kind)) return 0.0 // Genbutsu applies to this opponent only.
        val pair = when (known[kind]) {
            3, 4 -> 0.10
            2 -> 0.22
            else -> 0.38
        }
        if (level == BotDifficulty.EASY) return if (Tile.terminalOrHonor(kind)) 0.8 else 1.0
        // Keep a residual for tanki/shanpon, chiitoitsu and (closed hands) kokushi.
        val special = if (threat.closed && Tile.terminalOrHonor(kind)) 0.06 else 0.02
        if (kind >= 27) return pair + special
        var sequence = 0.65
        val number = kind % 9
        val suji = when {
            number < 3 -> contains(threat.river, kind + 3)
            number > 5 -> contains(threat.river, kind - 3)
            else -> contains(threat.river, kind - 3) && contains(threat.river, kind + 3)
        }
        if (suji) sequence *= 0.55 // Only the sequence component, never a safety proof.
        if (level == BotDifficulty.HARD) {
            var possible = 0
            var forms = 0
            for (low in maxOf(kind / 9 * 9, kind - 2)..minOf(kind, kind / 9 * 9 + 6)) {
                forms++
                var blocked = false
                for (candidate in low until low + 3) {
                    if (candidate != kind && known[candidate] >= 4) blocked = true
                }
                if (!blocked) possible++
            }
            sequence *= possible.toDouble() / forms
        }
        return pair + sequence + special
    }

    fun riskAgainst(seat: Int, kind: Int): Double = risks[seat][kind]

    fun danger(tile: Int): Double = threats.sumOf { threat ->
        risks[threat.seat][Tile.kind(tile)] * threat.pressure *
            (1 + value.bonus(tile) * 0.12) * threat.value / 3000
    }

    fun pressure(): Double = threats.sumOf { it.pressure }

    fun strongestValue(): Double = threats.asSequence().filter { it.pressure >= 0.4 }.maxOfOrNull { it.value } ?: 0.0

    fun mode(hand: BotAnalysis.Evaluation): Mode {
        if (pressure() < 0.5) return Mode.PUSH
        val draws = view.remaining() / view.rules().players()
        if (level == BotDifficulty.EASY) return if (hand.shanten() >= 3) Mode.FOLD else Mode.CAUTIOUS
        val urgency = placementUrgency(hand.points())
        val goodTenpai = hand.shanten() == 0 && hand.waits().quality() >= 3 && hand.points() * urgency >= strongestValue()
        val goodApproach = hand.shanten() == 1 && hand.live() >= 14 && hand.points() * urgency >= strongestValue() * 1.5 && draws >= 5
        if (goodTenpai || goodApproach && pressure() < 1.5) return Mode.PUSH
        // Several weak signals still affect each discard's risk. Their sum alone
        // must not turn uncertain opponents into an established tenpai threat.
        val established = threats.any { it.pressure >= 0.65 }
        if (!established && draws > hand.shanten() + 1) return Mode.CAUTIOUS
        if (
            hand.shanten() >= 2 || hand.live() == 0 || draws <= hand.shanten() + 1 ||
            level == BotDifficulty.HARD && hand.shanten() > 0 &&
            (pressure() >= 1.5 || hand.points() * urgency < strongestValue())
        ) return Mode.FOLD
        return Mode.CAUTIOUS
    }

    fun placementUrgency(points: Double): Double {
        if (view.round() < view.rules().scheduledRounds() - 1) return 1.0
        val own = view.seats()[view.viewerSeat()].points()
        var above = Int.MAX_VALUE
        var below = Int.MAX_VALUE
        for (seat in view.seats().indices) {
            if (seat == view.viewerSeat()) continue
            val difference = view.seats()[seat].points() - own
            if (difference >= 0) above = minOf(above, difference) else below = minOf(below, -difference)
        }
        if (above == Int.MAX_VALUE) return 0.75
        if (points + view.riichiSticks() * 1000 >= above) return 1.25
        return if (below == Int.MAX_VALUE) 1.15 else 1.0
    }

    fun penalty(tile: Int, mode: Mode): Double = danger(tile) * when (mode) {
        Mode.PUSH -> 7
        Mode.CAUTIOUS -> 30
        Mode.FOLD -> 100
    }

    fun reserve(state: BotAnalysis.State): Double {
        if (level != BotDifficulty.HARD || pressure() < 0.5) return 0.0
        var safeKinds = 0L
        state.hand().forEach { if (danger(it) < 0.08) safeKinds = safeKinds or (1L shl Tile.kind(it)) }
        return minOf(2, java.lang.Long.bitCount(safeKinds)) * 3.0
    }
}

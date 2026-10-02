package top.skyeyefast.mchjong.engine

/** Riichi-only rules and completed hand records. */
@JvmRecord
data class RiichiReplay(val rules: RiichiRules, val initialDealer: Int, val redFives: RedFives, val hands: List<ReplayHand>) {
    init {
        require(rules.allows(redFives) && initialDealer in 0 until rules.players() && hands.size <= 1024)
        require(hands.all { it.initialHands.size == rules.players() })
    }
}

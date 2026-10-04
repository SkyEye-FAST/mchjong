package top.skyeyefast.mchjong.engine

/** One hand, balanced transfers and inputs for the next hand; no balances or session lifecycle. */
class TaiwanSettlement internal constructor(
    val winner: Int?, val supplier: Int?, val score: TaiwanHandAnalyzer.Score?,
    val flowerScore: TaiwanHandAnalyzer.FlowerScore?, transfers: List<Transfer>,
    val nextDealer: Int, val nextContinuation: Int,
) {
    data class Transfer(val from: Int, val to: Int, val base: Long, val handTai: Int, val dealerTai: Int, val amount: Long)
    val transfers: List<Transfer> = java.util.List.copyOf(transfers)
    val deltas: List<Long> = java.util.List.copyOf(List(4) { seat ->
        transfers.sumOf { if (it.to == seat) it.amount else if (it.from == seat) -it.amount else 0L }
    })
}

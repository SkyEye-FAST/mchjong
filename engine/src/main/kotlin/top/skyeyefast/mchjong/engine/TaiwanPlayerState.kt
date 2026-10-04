package top.skyeyefast.mchjong.engine

/** Full-information engine snapshot, not a recipient-safe network view. */
class TaiwanPlayerState internal constructor(
    val seat: Int, concealed: List<Int>, melds: List<Meld>, flowers: List<FlowerTile>, discards: List<Int>,
    val ready: TaiwanWinContext.Ready, val passedWin: Boolean,
) {
    val concealed: List<Int> = java.util.List.copyOf(concealed)
    val melds: List<Meld> = java.util.List.copyOf(melds)
    val flowers: List<FlowerTile> = java.util.List.copyOf(flowers)
    /** Unclaimed physical discards only; claimed tiles move to the caller. */
    val discards: List<Int> = java.util.List.copyOf(discards)
}

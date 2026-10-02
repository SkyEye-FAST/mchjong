package top.skyeyefast.mchjong.engine

/** MCR-only completed hands. */
@JvmRecord
data class McrReplay(val hands: List<McrReplayHand>) {
    init { require(hands.size <= 16 && hands.withIndex().all { (index, hand) -> hand.number == index + 1 }) }
}

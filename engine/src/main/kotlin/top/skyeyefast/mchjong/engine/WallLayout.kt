package top.skyeyefast.mchjong.engine

/** Public wall positions only: drawing moves clockwise, opposite to the seat order. */
object WallLayout {
    /** Count seats from the dealer, then skip diceSum stacks from that owner's right. */
    @JvmStatic
    fun breakOffset(dealer: Int, diceSum: Int, size: Int, players: Int): Int {
        val stacksPerSide = size / (2 * players)
        val side = (dealer + diceSum - 1) % players
        return 2 * (side * stacksPerSide + stacksPerSide - diceSum - 1)
    }

    @JvmStatic
    fun stack(index: Int, breakOffset: Int, size: Int): Int =
        Math.floorMod(breakOffset / 2 - index / 2, size / 2)

    @JvmStatic
    fun side(index: Int, breakOffset: Int, size: Int, players: Int): Int =
        stack(index, breakOffset, size) / (size / (2 * players))
}

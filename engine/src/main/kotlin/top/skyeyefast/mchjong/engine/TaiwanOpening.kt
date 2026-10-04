package top.skyeyefast.mchjong.engine

/** Seats advance counterclockwise. Stock is four walls, each ordered from its right edge. */
class TaiwanOpening(val dealer: Int, dice: List<Int>) {
    val dice: List<Int> = java.util.List.copyOf(dice)
    init { require(dealer in 0..3 && dice.size == 3 && dice.all { it in 1..6 }) }
    fun cutIndex(stockSize: Int): Int {
        require(stockSize == 136 || stockSize == 144)
        // Funtown opens the selected dealer's wall, skipping the dice sum in stacks.
        return (dealer * (stockSize / 4) + 2 * dice.sum()) % stockSize
    }
}

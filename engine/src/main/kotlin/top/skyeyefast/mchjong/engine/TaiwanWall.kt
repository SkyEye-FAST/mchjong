package top.skyeyefast.mchjong.engine

/** Independent front/tail wall. Remaining reserve is never dealt or used for replacements. */
class TaiwanWall internal constructor(stock: List<Int>, opening: TaiwanOpening, private val rules: TaiwanRules) {
    private val order: List<Int>
    private var front = 0
    private var tail: Int
    var kongs: Int = 0
        private set
    init {
        val expected = if (rules.flowers == TaiwanRules.Flowers.NONE) Tile.set(false, RedFives.NONE) else Tile.standard144Set()
        require(stock.size == expected.size && stock.toSet() == expected.toSet()) { "Expected one complete undecorated Taiwan stock" }
        val cut = opening.cutIndex(stock.size)
        order = java.util.List.copyOf(stock.drop(cut) + stock.take(cut))
        tail = order.size
    }
    val reserve: Int get() = 16 + if (rules.reserve == TaiwanRules.Reserve.SIXTEEN_PLUS_KONGS) kongs else 0
    val drawable: Int get() = maxOf(0, tail - front - reserve)
    fun remaining(): List<Int> = java.util.List.copyOf(order.subList(front, tail))
    internal fun draw(): Int? = if (drawable > 0) order[front++] else null
    internal fun replace(): Int? = if (drawable > 0) order[--tail] else null
    internal fun canKong(): Boolean = drawable > if (rules.reserve == TaiwanRules.Reserve.SIXTEEN_PLUS_KONGS) 1 else 0
    internal fun completeKong() { check(canKong()); kongs++ }
}

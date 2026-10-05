package top.skyeyefast.mchjong.engine

/** Fixed slots never move; cursors count front/tail takes in TaiwanWallLayout order. */
class TaiwanWall private constructor(private val slots: MutableList<Int>, private val opening: TaiwanOpening,
                                     private val rules: TaiwanRules, private var front: Int,
                                     private var tail: Int, kongs: Int) {
    var kongs: Int = kongs
        private set
    internal constructor(stock: List<Int>, opening: TaiwanOpening, rules: TaiwanRules) :
        this(stock.toMutableList(), opening, rules, 0, 0, 0) {
        require(stock.size == expected(rules).size && stock.toSet() == expected(rules).toSet())
    }
    val reserve: Int get() = 16 + if (rules.reserve == TaiwanRules.Reserve.SIXTEEN_PLUS_KONGS) kongs else 0
    val drawable: Int get() = slots.size - front - tail - reserve
    fun remaining(): List<Int> = java.util.List.copyOf(slots.filter { it != Tile.ABSENT })
    fun physicalSlots(): List<Int> = java.util.List.copyOf(slots)
    internal fun draw(): Int? = if (drawable > 0) take(TaiwanWallLayout.drawSlot(slots.size, opening, front++)) else null
    internal fun replace(): Int? = if (drawable > 0) take(TaiwanWallLayout.replacementSlot(slots.size, opening, tail++)) else null
    private fun take(slot: Int): Int = slots[slot].also { check(it != Tile.ABSENT); slots[slot] = Tile.ABSENT }
    internal fun canKong(): Boolean = drawable > if (rules.reserve == TaiwanRules.Reserve.SIXTEEN_PLUS_KONGS) 1 else 0
    internal fun completeKong() { check(canKong()); kongs++ }
    internal fun save() = TaiwanGameState.Wall(slots, front, tail, kongs)
    companion object {
        internal fun expected(rules: TaiwanRules): List<Int> = if (rules.flowers == TaiwanRules.Flowers.NONE) Tile.set(false, RedFives.NONE) else Tile.standard144Set()
        internal fun restore(state: TaiwanGameState.Wall, opening: TaiwanOpening, rules: TaiwanRules): TaiwanWall {
            val size = expected(rules).size
            require(state.slots().size == size && state.front() in 65..size && state.tail() in 0..size && state.kongs() in 0..20)
            val wall = TaiwanWall(state.slots().toMutableList(), opening, rules, state.front(), state.tail(), state.kongs())
            require(wall.drawable >= 0 && state.front() + state.tail() <= size)
            for (i in 0 until size) {
                val tile = state.slots()[TaiwanWallLayout.drawSlot(size, opening, i)]
                val absent = i < state.front() || i >= size - state.tail()
                require((tile == Tile.ABSENT) == absent)
                require(absent || tile in expected(rules))
            }
            require(wall.remaining().distinct().size == wall.remaining().size)
            return wall
        }
    }
}

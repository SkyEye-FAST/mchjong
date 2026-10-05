package top.skyeyefast.mchjong.engine

/** Stable physical contract. Sides follow the preset's numbered stock order. */
object TaiwanWallLayout {
    enum class Layer { UPPER, LOWER }
    @JvmStatic fun stacks(size: Int): Int { require(size == 136 || size == 144); return size / 8 }
    @JvmStatic fun slot(size: Int, side: Int, stack: Int, layer: Layer): Int {
        require(side in 0..3 && stack in 0 until stacks(size))
        return side * size / 4 + stack * 2 + layer.ordinal
    }
    @JvmStatic fun side(size: Int, slot: Int): Int { require(slot in 0 until size); stacks(size); return slot / (size / 4) }
    @JvmStatic fun stack(size: Int, slot: Int): Int { side(size, slot); return slot % (size / 4) / 2 }
    @JvmStatic fun layer(slot: Int): Layer { require(slot >= 0); return Layer.entries[slot % 2] }
    @JvmStatic fun drawSlot(size: Int, opening: TaiwanOpening, index: Int): Int {
        require(index in 0 until size)
        return (opening.cutIndex(size) + index) % size
    }
    /** Exact reverse: lower then upper, toward the preceding stack. */
    @JvmStatic fun replacementSlot(size: Int, opening: TaiwanOpening, index: Int): Int = drawSlot(size, opening, size - 1 - index)
}

package top.skyeyefast.mchjong.engine

/** The same physical red, indicator and north bonuses for settlement and hypothetical wins. */
internal object HandBonuses {
    @JvmStatic
    fun indicators(indicators: List<Int>, sanma: Boolean): IntArray {
        val counts = IntArray(34)
        for (tile in indicators) if (tile >= 0) counts[Tile.doraAfter(Tile.kind(tile), sanma)]++
        return counts
    }

    fun tile(tile: Int, dora: IntArray): Int = dora[Tile.kind(tile)] + if (Tile.red(tile)) 1 else 0

    @JvmStatic
    fun count(hand: List<Int>, melds: List<Meld>, norths: List<Int>, winning: Int, dora: IntArray): Int {
        var count = norths.size
        if (winning >= 0 && hand.size + melds.size * 3 == 13) count += tile(winning, dora)
        for (tile in hand) count += tile(tile, dora)
        for (meld in melds) for (tile in meld.tiles()) count += tile(tile, dora)
        for (tile in norths) count += tile(tile, dora)
        return count
    }
}

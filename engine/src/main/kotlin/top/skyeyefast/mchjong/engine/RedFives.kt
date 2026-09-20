package top.skyeyefast.mchjong.engine

import java.util.Locale

/** Composition of the full physical set, before removing 2–8 characters for sanma. */
enum class RedFives(private val man: Int, private val pin: Int, private val sou: Int) {
    NONE(0, 0, 0),
    THREE(1, 1, 1),
    FOUR(1, 2, 1);

    fun count(suit: Int): Int = when (suit) {
        0 -> man
        1 -> pin
        2 -> sou
        else -> 0
    }

    fun total(): Int = man + pin + sou

    fun translationKey(): String = "red_fives.mchjong.${name.lowercase(Locale.ROOT)}"

    companion object {
        @JvmStatic
        fun of(man: Int, pin: Int, sou: Int): RedFives? =
            entries.firstOrNull { it.man == man && it.pin == pin && it.sou == sou }

        @JvmStatic
        fun of(tiles: List<Int>): RedFives? {
            val counts = IntArray(3)
            for (tile in tiles) if (Tile.red(tile)) counts[Tile.kind(tile) / 9]++
            // Three-player walls omit all middle characters, including the red five.
            if (tiles.size == 108 && counts[1] > 0 && counts[2] > 0) counts[0] = 1
            return of(counts[0], counts[1], counts[2])
        }
    }
}

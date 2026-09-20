package top.skyeyefast.mchjong.engine

/** A tile ID identifies one physical tile, not just its face. */
object Tile {
    private const val RED_FLAG = 256

    @JvmField
    val ORDER = compareBy<Int>({ kind(it) }, { it })

    const val HIDDEN = -1
    const val ABSENT = -2
    const val EAST = 27
    const val SOUTH = 28
    const val WEST = 29
    const val NORTH = 30
    const val WHITE = 31
    const val GREEN = 32
    const val RED = 33

    @JvmStatic
    fun kind(id: Int): Int {
        val physical = id and RED_FLAG.inv()
        if (physical < 0 || physical >= 136 ||
            (id and RED_FLAG) != 0 && physical / 4 != 4 && physical / 4 != 13 && physical / 4 != 22
        ) throw IllegalArgumentException("Invalid physical tile: $id")
        return physical / 4
    }

    @JvmStatic
    fun red(id: Int): Boolean = id >= 0 && (id and RED_FLAG) != 0

    @JvmStatic
    fun id(kind: Int, copy: Int, red: Boolean): Int {
        if (kind !in 0..<34 || copy !in 0..<4) throw IllegalArgumentException("Invalid tile identity")
        val id = kind * 4 + copy or if (red) RED_FLAG else 0
        kind(id)
        return id
    }

    @JvmStatic
    fun validSet(tiles: List<Int>): Boolean {
        val sanma = tiles.size == 108
        if (!sanma && tiles.size != 136) return false
        val seen = BooleanArray(136)
        return try {
            for (tile in tiles) {
                val face = kind(tile)
                if (sanma && face in 1..<8) return false
                val physical = tile and RED_FLAG.inv()
                if (seen[physical]) return false
                seen[physical] = true
            }
            RedFives.of(tiles) != null
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    @JvmStatic
    fun terminalOrHonor(kind: Int): Boolean = kind >= 27 || kind % 9 == 0 || kind % 9 == 8

    @JvmStatic
    fun notation(kind: Int): String {
        if (kind !in 0..<34) throw IllegalArgumentException("Invalid tile kind")
        val number = if (kind < 27) kind % 9 + 1 else kind - 26
        return "$number${"mpsz"[kind / 9]}"
    }

    @JvmStatic
    fun parseKind(notation: String): Int {
        if (notation.length != 2) throw IllegalArgumentException("Invalid tile notation")
        val suit = "mpsz".indexOf(notation[1])
        val number = Character.digit(notation[0], 10)
        if (suit < 0 || number < 1 || number > if (suit == 3) 7 else 9) {
            throw IllegalArgumentException("Invalid tile notation: $notation")
        }
        return suit * 9 + number - 1
    }

    @JvmStatic
    fun doraAfter(indicator: Int, sanma: Boolean): Int {
        if (sanma && indicator == 0) return 8
        if (sanma && indicator == 8) return 0
        if (indicator < 27) return indicator / 9 * 9 + (indicator + 1) % 9
        if (indicator < 31) return 27 + (indicator - 26) % 4
        return 31 + (indicator - 30) % 3
    }

    @JvmStatic
    fun set(sanma: Boolean): List<Int> = set(sanma, RedFives.THREE)

    @JvmStatic
    fun set(sanma: Boolean, redFives: RedFives): List<Int> {
        val tiles = ArrayList<Int>(if (sanma) 108 else 136)
        for (face in 0..<34) {
            if (sanma && face in 1..<8) continue
            for (copy in 0..<4) {
                tiles += id(face, copy, face < 27 && face % 9 == 4 && copy < redFives.count(face / 9))
            }
        }
        return tiles
    }

    @JvmStatic
    fun notations(ids: List<Int>): List<String> = ids.map { notation(kind(it)) }
}

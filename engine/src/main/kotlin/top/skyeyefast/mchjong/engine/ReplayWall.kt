package top.skyeyefast.mchjong.engine

/** Initial physical wall layout sealed into a replay only after the hand has settled. */
@JvmRecord
data class ReplayWall(
    val tiles: List<Int>,
    val breakOffset: Int,
    val replacements: List<Int>,
    val dora: List<Int>,
    val ura: List<Int>,
) {
    init {
        require(Tile.validSet(tiles) && breakOffset in tiles.indices) { "Invalid replay wall" }
        require(replacements.isNotEmpty() && replacements.size <= 8 && replacements.all { it in tiles.indices }) {
            "Invalid replay replacement slots"
        }
        require(dora.size == 5 && ura.size == 5 && (dora + ura).all { it in tiles.indices }) {
            "Invalid replay indicator slots"
        }
    }

    val deadStart: Int get() = tiles.size - 14
}

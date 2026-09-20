package top.skyeyefast.mchjong.engine

/** Library-derived shape information. Availability is counted separately from public tiles. */
@JvmRecord
data class TileEfficiency(
    val shanten: Int,
    val improving: Set<Int>,
    val goodShape: Set<Int>,
)

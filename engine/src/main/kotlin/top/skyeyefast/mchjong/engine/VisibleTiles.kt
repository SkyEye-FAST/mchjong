package top.skyeyefast.mchjong.engine

import java.util.HashSet

/** Public table information and the recipient's own hand, never opponents' concealed tiles. */
object VisibleTiles {
    @JvmStatic
    fun counts(view: TableView): IntArray {
        val counts = IntArray(34)
        for (tile in tiles(view)) counts[Tile.kind(tile)]++
        return counts
    }

    internal fun tiles(view: TableView): Set<Int> {
        val visible = HashSet<Int>()
        if (view.viewerSeat() in view.seats().indices) visible += view.seats()[view.viewerSeat()].hand()
        for (seat in view.seats()) {
            seat.river().forEach { visible += it.tile() }
            seat.melds().forEach { visible += it.tiles() }
            visible += seat.norths()
        }
        for (tile in view.wall()) if (tile >= 0) visible += tile
        view.focus()?.let { visible += it.tile() }
        visible.removeIf { it < 0 }
        return visible
    }
}

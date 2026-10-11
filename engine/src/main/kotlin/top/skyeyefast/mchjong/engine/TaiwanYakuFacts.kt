package top.skyeyefast.mchjong.engine

import top.skyeyefast.taiwan.GroupType
import top.skyeyefast.taiwan.TaiwanScore

/** Candidate-specific facts; a discard-completed triplet is exposed only for group conditions. */
internal object TaiwanYakuFacts {
    fun of(score: TaiwanScore, complete: List<Int>, melds: List<Meld>, winningTile: Int, c: TaiwanWinContext): YakuFacts {
        val selfDraw = c.method == TaiwanWinContext.Method.SELF_DRAW
        val groups = score.shape.groups.mapIndexed { index, group ->
            YakuFacts.Group(if (group.type == GroupType.SEQUENCE) "sequence" else "triplet", group.kind,
                group.type == GroupType.SEQUENCE || selfDraw || index != score.winningGroup)
        } + melds.map {
            YakuFacts.Group(when (it.type()) {
                Meld.Type.SEQUENCE -> "sequence"
                Meld.Type.TRIPLET -> "triplet"
                else -> "quad"
            }, it.tiles().minOf(Tile::kind), it.closed())
        }
        val winning = score.shape.groups.getOrNull(score.winningGroup)
        val kind = Tile.kind(winningTile)
        val wait = when {
            winning == null -> "pair"
            winning.type == GroupType.TRIPLET -> "triplet"
            kind == winning.kind + 1 -> "closed"
            winning.kind % 9 == 0 && kind == winning.kind + 2 || winning.kind % 9 == 6 && kind == winning.kind -> "edge"
            else -> "two_sided"
        }
        val flags = buildSet {
            add(if (selfDraw) "self_draw" else "discard")
            if (c.method == TaiwanWinContext.Method.ROBBING_KONG) add("robbing_kong")
            if (c.lastTile) add(if (selfDraw) "last_draw" else "last_discard")
            when (c.drawOrigin) {
                TaiwanWinContext.DrawOrigin.KONG_REPLACEMENT -> add("kong_replacement")
                TaiwanWinContext.DrawOrigin.FLOWER_REPLACEMENT -> add("flower_replacement")
                else -> Unit
            }
            if (c.opening != TaiwanWinContext.Opening.NONE) add(c.opening.name.lowercase(java.util.Locale.ROOT) + "_win")
            if (c.ready != TaiwanWinContext.Ready.NONE) add("ready")
            if (c.ready == TaiwanWinContext.Ready.HEAVENLY) add("heavenly_ready")
            if (c.ready == TaiwanWinContext.Ready.EARTHLY) add("earthly_ready")
        }
        val owned = complete + melds.flatMap { it.tiles() }
        return YakuFacts("regular", melds.all { it.closed() }, owned.map(Tile::kind), groups, score.shape.pair,
            wait, flags, owned.count(Tile::red), c.flowers.size)
    }
}
